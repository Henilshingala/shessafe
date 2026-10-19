package com.womensafety.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUtilTest {

    @Test
    fun testValidE164Formatting() {
        val formatted = PhoneUtil.toE164("+919876543210", "IN")
        assertEquals("+919876543210", formatted)
    }

    @Test
    fun testIsSameNumber() {
        assertTrue(PhoneUtil.isSameNumber("+919876543210", "9876543210", "IN"))
        assertTrue(PhoneUtil.isSameNumber("09876543210", "+919876543210", "IN"))
        assertFalse(PhoneUtil.isSameNumber("+919876543210", "+919876543211", "IN"))
    }

    @Test
    fun testMaskPhoneNumber() {
        val masked = PhoneUtil.maskPhoneNumber("+919876543210")
        assertTrue(masked.startsWith("+"))
        assertTrue(masked.endsWith("3210"))
        assertTrue(masked.contains("*"))
    }
}
