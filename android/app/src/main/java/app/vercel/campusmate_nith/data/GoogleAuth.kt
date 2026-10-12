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
        throw SignInCancelled()
    } catch (e: NoCredentialException) {
        throw ApiException("No Google account is available on this phone. Add one in Settings and try again.")
    } catch (e: GetCredentialException) {
        val msg = e.message.orEmpty()
        throw ApiException(
            if ("28444" in msg || "Developer console" in msg || "10:" in msg)
                "Google Sign-In isn't set up for the app yet (the Android OAuth client is missing in Google Cloud)."
            else "Google Sign-In failed: ${msg.ifEmpty { e.type }}"
        )
    }
    val cred = result.credential
    if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(cred.data).idToken
    }
    throw ApiException("Google returned an unexpected sign-in type.")
}
