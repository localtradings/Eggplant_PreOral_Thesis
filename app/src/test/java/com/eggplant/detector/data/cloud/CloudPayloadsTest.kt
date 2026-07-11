package com.eggplant.detector.data.cloud

import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
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

    @Test
    fun `disease request omits blank optional name and records camera sources`() {
        val payload = diseaseRequestPayload(
            clientRequestId = "request-1",
            requestedName = null,
            notes = "Unknown fruit damage",
            modelVersion = "model",
            photoPaths = listOf("/private/photo.jpg"),
            photoSources = listOf("live"),
            rightsConsent = true,
            trainingConsent = false,
        )

        assertFalse("requestedName" in payload)
        assertEquals(listOf("live"), payload.getValue("photoSources").jsonArray.map { it.jsonPrimitive.content })
    }
}
