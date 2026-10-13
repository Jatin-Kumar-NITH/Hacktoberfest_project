package app.vercel.campusmate_nith.data

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Thrown when the student closes the Google account picker; not an error worth showing. */
class SignInCancelled : Exception()

private const val NOT_SET_UP =
    "Google rejected this app. The Android OAuth client in Google Cloud is missing or doesn't match: it needs package " +
        "app.vercel.campusmate_nith and this app's SHA-1, in the same project as the website's client ID."

/**
 * Shows Android's Google account picker and returns an ID token for [webClientId].
 * The token's audience is the website's client ID, so /api/login accepts it unchanged.
 */
suspend fun googleIdToken(activity: Activity, webClientId: String): String {
    val option = GetSignInWithGoogleOption.Builder(webClientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val result = try {
        CredentialManager.create(activity).getCredential(activity, request)
    } catch (e: GetCredentialCancellationException) {
        // Google also reports "cancelled" when it rejects the app right after an account is picked,
        // so say what that usually means instead of failing silently.
        throw ApiException(
            if (rejected(e)) "$NOT_SET_UP\n\nDetails: ${detail(e)}"
            else "Sign-in didn't finish. If you picked an account and the picker just closed, Google rejected the app: " +
                "the Android OAuth client in Google Cloud is missing or doesn't match.\n\nDetails: ${detail(e)}"
        )
    } catch (e: NoCredentialException) {
        throw ApiException("No Google account could be used. Add one in your phone's Settings, or check the Android OAuth client.\n\nDetails: ${detail(e)}")
    } catch (e: GetCredentialException) {
        val msg = e.message.orEmpty()
        throw ApiException(if (rejected(e) || "10:" in msg) "$NOT_SET_UP\n\nDetails: ${detail(e)}" else "Google Sign-In failed.\n\nDetails: ${detail(e)}")
    }
    val cred = result.credential
    if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(cred.data).idToken
    }
    throw ApiException("Google returned an unexpected sign-in type.")
}

private fun detail(e: GetCredentialException) = "${e.javaClass.simpleName}: ${e.message ?: e.type}"

/** Google says "cancelled" for some rejections too; these markers mean it wasn't the student. */
private fun rejected(e: GetCredentialException): Boolean {
    val m = e.message.orEmpty()
    return listOf("28444", "Developer console", "[16]", "[10]", "reauth", "DEVELOPER_ERROR").any { it in m }
}
