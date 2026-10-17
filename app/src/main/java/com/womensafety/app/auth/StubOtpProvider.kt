package com.womensafety.app.auth

/**
 * Stub OTP provider used when Firebase has not been configured
 * (i.e., google-services.json is missing from the app/ directory).
 *
 * This allows the app to compile and run. The user will see a clear
 * error message: "Firebase not configured. Please add google-services.json."
 *
 * TO ACTIVATE REAL OTP:
 *  1. Create a Firebase project at https://console.firebase.google.com/
 *  2. Add your Android app (package: com.womensafety.app)
 *  3. Download google-services.json → place in app/ directory
 *  4. In app/build.gradle.kts uncomment: id("com.google.gms.google-services")
 *  5. In app/build.gradle.kts uncomment the Firebase BOM and firebase-auth-ktx deps
 *  6. Replace this class with [FirebaseOtpProvider]
 */
class StubOtpProvider : OtpProvider {

    override suspend fun sendOtp(phoneNumber: String): OtpResult {
        return OtpResult.Error(
            "Firebase not configured. " +
            "Please add google-services.json to the app/ directory " +
            "and enable the Firebase plugin in build.gradle.kts."
        )
    }

    override suspend fun verifyOtp(verificationId: String, otp: String): OtpResult {
        return OtpResult.Error(
            "Firebase not configured. OTP verification is unavailable."
        )
    }
}
