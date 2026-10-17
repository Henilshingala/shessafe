package com.womensafety.app.util

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import java.util.Locale

/**
 * Phone number utilities using libphonenumber for proper international normalization.
 * No hardcoded country codes — always uses device locale or explicit region.
 */
object PhoneUtil {

    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /**
     * Returns the ISO 3166-1 alpha-2 region code for the device's default locale.
     * Example: "IN", "US", "GB"
     */
    fun defaultRegion(): String = Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "US"

    /**
     * Returns the calling code prefix string for a region, e.g. "+91" for "IN".
     */
    fun callingCodeForRegion(region: String = defaultRegion()): String {
        return try {
            "+${phoneUtil.getCountryCodeForRegion(region)}"
        } catch (_: Exception) {
            "+1"
        }
    }

    /**
     * Tries to parse and format [rawNumber] in E.164 format (e.g. +919876543210).
     * [defaultRegion] is used as the fallback if no country code prefix is present.
     *
     * Returns `null` if the number cannot be parsed or is not valid.
     */
    fun toE164(rawNumber: String, defaultRegion: String = PhoneUtil.defaultRegion()): String? {
        return try {
            val parsed = phoneUtil.parse(rawNumber.trim(), defaultRegion)
            if (phoneUtil.isValidNumber(parsed)) {
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else null
        } catch (_: NumberParseException) {
            null
        }
    }

    /**
     * Returns `true` if [rawNumber] is a valid phone number for the given [region].
     */
    fun isValid(rawNumber: String, region: String = defaultRegion()): Boolean {
        return toE164(rawNumber, region) != null
    }

    /**
     * Normalize two numbers and compare them. Returns `true` if they refer to the same
     * phone number. Falls back to last-10-digit comparison if libphonenumber cannot parse.
     */
    fun isSameNumber(a: String, b: String, region: String = defaultRegion()): Boolean {
        val e164A = toE164(a, region)
        val e164B = toE164(b, region)
        if (e164A != null && e164B != null) return e164A == e164B
        // Fallback: compare last 10 digits
        val digA = a.filter { it.isDigit() }.takeLast(10)
        val digB = b.filter { it.isDigit() }.takeLast(10)
        return digA.isNotEmpty() && digA == digB
    }

    /**
     * Returns a display-friendly format of [rawNumber], e.g. "+91 98765 43210".
     * Falls back to the original string if parsing fails.
     */
    fun toDisplayFormat(rawNumber: String, region: String = defaultRegion()): String {
        return try {
            val parsed = phoneUtil.parse(rawNumber.trim(), region)
            phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        } catch (_: Exception) {
            rawNumber
        }
    }
}
