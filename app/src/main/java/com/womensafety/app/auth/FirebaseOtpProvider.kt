package com.womensafety.app.auth

/**
 * FirebaseOtpProvider — placeholder for Firebase Phone Auth.
 *
 * This class cannot be instantiated until google-services.json is added and
 * the Firebase dependencies are uncommented in build.gradle.kts.
 *
 * Once Firebase is configured, this class should be wired in place of [StubOtpProvider]
 * in [com.womensafety.app.ui.screens.LoginViewModel].
 *
 * Implementation notes:
 *  - Use FirebaseAuth.getInstance().verifyPhoneNumber() with PhoneAuthOptions
 *  - Store verificationId from onCodeSent callback
 *  - Use PhoneAuthProvider.credential(verificationId, smsCode) to create credential
 *  - Sign in with FirebaseAuth.getInstance().signInWithCredential(credential)
 *  - On success, call FirebaseAuth.getInstance().currentUser to confirm
 *
 * The class is commented out to avoid compilation errors without Firebase dependencies.
 */

/*
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.TimeUnit
import android.app.Activity

class FirebaseOtpProvider(private val activity: Activity) : OtpProvider {

    private val auth = FirebaseAuth.getInstance()

    override suspend fun sendOtp(phoneNumber: String): OtpResult {
        val deferred = CompletableDeferred<OtpResult>()

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-verified (e.g. Google Play Services reads the SMS)
                deferred.complete(OtpResult.Success(credential.smsCode ?: ""))
            }
            override fun onVerificationFailed(e: FirebaseException) {
                deferred.complete(OtpResult.Error(e.message ?: "Verification failed"))
            }
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                deferred.complete(OtpResult.Success(verificationId))
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
        return deferred.await()
    }

    override suspend fun verifyOtp(verificationId: String, otp: String): OtpResult {
        val deferred = CompletableDeferred<OtpResult>()
        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { deferred.complete(OtpResult.Success()) }
            .addOnFailureListener { e -> deferred.complete(OtpResult.Error(e.message ?: "Invalid OTP")) }
        return deferred.await()
    }
}
*/

// Placeholder class so the file is not empty:
object FirebaseOtpProviderPlaceholder {
    const val SETUP_INSTRUCTIONS = """
        To enable Firebase Phone Auth:
        1. Create a Firebase project at https://console.firebase.google.com/
        2. Add Android app with package: com.womensafety.app
        3. Download google-services.json → place in app/ directory
        4. In app/build.gradle.kts uncomment: id("com.google.gms.google-services")
        5. In app/build.gradle.kts uncomment Firebase BOM and firebase-auth-ktx
        6. Uncomment the FirebaseOtpProvider class in this file
        7. In LoginViewModel, replace StubOtpProvider() with FirebaseOtpProvider(activity)
    """
}
