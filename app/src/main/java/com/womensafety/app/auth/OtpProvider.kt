package com.womensafety.app.auth

/**
 * OTP Provider interface for phone-number verification.
 *
 * The default implementation uses Firebase Phone Auth ([FirebaseOtpProvider]).
 * A [StubOtpProvider] is used when google-services.json has not been configured,
 * so the app can still compile and run (OTP will fail gracefully with a message).
 */
interface OtpProvider {

    /**
     * Initiate sending an OTP to [phoneNumber].
     * @return [OtpResult.Success] with a verification ID on success, or [OtpResult.Error].
     */
    suspend fun sendOtp(phoneNumber: String): OtpResult

    /**
     * Verify the [otp] code against the given [verificationId].
     * @return [OtpResult.Success] on correct code, or [OtpResult.Error].
     */
    suspend fun verifyOtp(verificationId: String, otp: String): OtpResult
}

sealed class OtpResult {
    /** OTP was sent / verified successfully. [verificationId] is set when sending. */
    data class Success(val verificationId: String = "") : OtpResult()

    /** Something went wrong. [message] is user-facing. */
    data class Error(val message: String) : OtpResult()
}
