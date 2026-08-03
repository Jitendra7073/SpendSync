package com.example.spendsync.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCapturePackagesParsingTest {

    @Test
    fun `parses null as empty set`() {
        assertTrue(parseAutoCapturePackages(null).isEmpty())
    }

    @Test
    fun `parses blank string as empty set`() {
        assertTrue(parseAutoCapturePackages("").isEmpty())
    }

    @Test
    fun `round trips a set of package names`() {
        val packages = setOf("com.phonepe.app", "com.google.android.apps.messaging")

        val serialized = serializeAutoCapturePackages(packages)
        val parsed = parseAutoCapturePackages(serialized)

        assertEquals(packages, parsed)
    }
}
