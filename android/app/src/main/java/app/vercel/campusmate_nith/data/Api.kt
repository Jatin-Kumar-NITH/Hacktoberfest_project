package app.vercel.campusmate_nith.data

import android.content.Context
import app.vercel.campusmate_nith.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/** A failed call, with the friendly message the server sent (or one we wrote). */
class ApiException(message: String, val status: Int = 0, val banned: Ban? = null) : IOException(message)

/** Keeps the Flask session cookie across app restarts, so students stay signed in. */
private class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val cookies = mutableMapOf<String, Cookie>()

    init {
        val base = BuildConfig.BASE_URL.toHttpUrl()
        prefs.all.values.filterIsInstance<String>().forEach { raw ->
            Cookie.parse(base, raw)?.let { if (it.expiresAt > System.currentTimeMillis()) cookies[it.name] = it }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val edit = prefs.edit()
        cookies.forEach { c ->
            if (c.expiresAt <= System.currentTimeMillis()) {
                this.cookies.remove(c.name); edit.remove(c.name)
            } else {
                this.cookies[c.name] = c
                // session cookies (no expiry) still need to survive a restart
                edit.putString(c.name, c.toString())
            }
        }
        edit.apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.filter { it.matches(url) }

    @Synchronized
    fun clear() {
        cookies.clear(); prefs.edit().clear().apply()
    }
}

private fun String.toHttpUrl(): HttpUrl = HttpUrl.Builder().scheme("https").host(removePrefix("https://").substringBefore('/')).build()

class Api(context: Context) {
    val base = BuildConfig.BASE_URL
    private val jar = PersistentCookieJar(context)
    private val cacheDir = context.cacheDir
    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)   // the AI endpoints can take a while
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun call(req: Request): String = withContext(Dispatchers.IO) {
        val res = try {
            client.newCall(req).execute()
        } catch (e: IOException) {
            throw ApiException("Can't reach CampusMate. Check your internet connection and try again.")
        }
        res.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val err = runCatching { json.decodeFromString<ErrorBody>(text) }.getOrNull()
                val msg = err?.error ?: when (it.code) {
                    401 -> "Please sign in again."
                    413 -> "That file is too big. Please keep uploads under 12 MB."
                    else -> "Something went wrong (error ${it.code}). Please try again."
                }
                throw ApiException(msg, it.code, err?.banned)
            }
            text
        }
    }

    private fun get(path: String) = Request.Builder().url(base + path).get().build()

    // every POST carries the same custom header the website sends (CSRF protection in accounts.py)
    private fun post(path: String, body: RequestBody) =
        Request.Builder().url(base + path).post(body).header("X-CampusMate", "1").build()

    private fun postJson(path: String, body: String) = post(path, body.toRequestBody(jsonType))

    suspend fun config(): String =
        json.decodeFromString<JsonObject>(call(get("/api/config")))["google_client_id"]?.stringOrNull().orEmpty()

    suspend fun login(idToken: String) {
        call(postJson("/api/login", buildJsonObject { put("credential", idToken) }.toString()))
    }

    suspend fun logout() {
        runCatching { call(postJson("/api/logout", "{}")) }
        jar.clear()
    }

    suspend fun me(): User = json.decodeFromString<JsonObject>(call(get("/api/me")))["user"]
        ?.let { json.decodeFromJsonElement(User.serializer(), it) } ?: throw ApiException("Please sign in again.", 401)

    suspend fun campus(): CampusResponse = json.decodeFromString(call(get("/api/campus")))

    suspend fun explainNotice(text: String, hindi: Boolean, image: ByteArray?, sampleId: String): NoticeResponse {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("text", text)
            .addFormDataPart("hindi", if (hindi) "1" else "0")
        if (sampleId.isNotEmpty()) body.addFormDataPart("sample_id", sampleId)
        if (image != null) body.addFormDataPart("file", "notice.jpg", image.toRequestBody("image/jpeg".toMediaType()))
        return json.decodeFromString(call(post("/api/notice", body.build())))
    }

    suspend fun plan(req: PlanRequest): PlanResponse = json.decodeFromString(call(postJson("/api/plan", json.encodeToString(req))))

    suspend fun ask(body: JsonObject): AskResponse = json.decodeFromString(call(postJson("/api/ask", body.toString())))

    suspend fun attendance(): Attendance = json.decodeFromString<AttendanceResponse>(call(get("/api/attendance"))).attendance

    suspend fun saveAttendance(a: Attendance) {
        call(postJson("/api/attendance", json.encodeToString(a)))
    }

    suspend fun chat(after: Int): ChatResponse = json.decodeFromString(call(get("/api/chat?after=$after")))
    suspend fun sendChat(body: String) {
        call(postJson("/api/chat", buildJsonObject { put("body", body) }.toString()))
    }
    suspend fun deleteChat(id: Int) {
        call(postJson("/api/chat/$id/delete", "{}"))
    }
    suspend fun ban(userId: Int, hours: Int) {
        call(postJson("/api/chat/ban", buildJsonObject { put("user_id", userId); put("hours", hours) }.toString()))
    }
    suspend fun unban(userId: Int) {
        call(postJson("/api/chat/unban", buildJsonObject { put("user_id", userId) }.toString()))
    }
    suspend fun bans(): List<Ban> = json.decodeFromString<BansResponse>(call(get("/api/chat/bans"))).bans

    suspend fun publish(fields: List<Pair<String, String>>, pdf: Pair<String, ByteArray>?) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (k, v) -> body.addFormDataPart(k, v) }
        if (pdf != null) body.addFormDataPart("pdf", pdf.first, pdf.second.toRequestBody("application/pdf".toMediaType()))
        call(post("/api/posts", body.build()))
    }
    suspend fun deletePost(dbId: Int) {
        call(postJson("/api/posts/$dbId/delete", "{}"))
    }

    suspend fun users(): List<AccountRow> = json.decodeFromString<UsersResponse>(call(get("/api/users"))).users
    suspend fun setRole(uid: Int, role: String) {
        call(postJson("/api/users/$uid/role", buildJsonObject { put("role", role) }.toString()))
    }

    /** Admin editors. Each returns the server's JSON reply so the caller can pick out the saved value. */
    suspend fun edit(path: String, body: String): JsonObject = json.decodeFromString(call(postJson(path, body)))

    /** Downloads a PDF (static docs or signed-in uploads) into the cache so a viewer app can open it. */
    suspend fun downloadPdf(path: String): File {
        val url = if (path.startsWith("http")) path else base + (if (path.startsWith("/")) path else "/static/$path")
        val name = url.substringAfterLast('/').substringBefore('?').ifEmpty { "document.pdf" }
        val dir = File(cacheDir, "pdfs").apply { mkdirs() }
        val out = File(dir, name)
        withContext(Dispatchers.IO) {
            val res = try { client.newCall(Request.Builder().url(url).build()).execute() } catch (e: IOException) {
                throw ApiException("Can't reach CampusMate. Check your internet connection and try again.")
            }
            res.use {
                if (!it.isSuccessful) throw ApiException("Couldn't download the PDF (error ${it.code}).", it.code)
                out.outputStream().use { o -> it.body!!.byteStream().copyTo(o) }
            }
        }
        return out
    }
}
