package com.eggplant.detector.data.cloud

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloudPayloadsTest {
    @Test
    fun `enabled sharing consent includes the accepted version`() {
        val payload = sharingConsentPayload(enabled = true)

        assertEquals("true", payload.getValue("enabled").jsonPrimitive.content)
        assertEquals("1", payload.getValue("consentVersion").jsonPrimitive.content)
    }

    @Test
    fun `disabled sharing consent omits the consent version`() {
        val payload = sharingConsentPayload(enabled = false)

        assertEquals("false", payload.getValue("enabled").jsonPrimitive.content)
        assertFalse("consentVersion" in payload)
    }
}
