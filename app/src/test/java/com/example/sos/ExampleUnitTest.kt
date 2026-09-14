package com.example.sos

import com.example.sos.model.EmergencyMessage
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun testBlePayloadLength() {
        val msg = EmergencyMessage(
            messageId = "RQ-8F29A1",
            senderId = "RQ-001",
            timestamp = 1718000000000L,
            latitude = 28.6139,
            longitude = 77.2090,
            originDevice = "RQ-001",
            lastRelayDevice = "RQ-001",
            peopleCount = "4",
            medicalNeeds = "Asthma",
            hazardType = "Flood"
        )
        val blePayload = msg.toBlePayload()
        assertTrue("BLE payload must be <= 24 bytes for BLE advertisement", blePayload.toByteArray().size <= 24)
        assertTrue(blePayload.startsWith("RQ,"))
    }

    @Test
    fun testLegacyFormatParsing() {
        val legacy = "RQ-1234|SOS|Lat:28.6139|Lng:77.2090|Time:1718000000000|Name:Rohan|Age:28|Ph:9988776655|Ppl:2|Med:Trauma|Haz:Fire"
        val parsed = EmergencyMessage.parse(legacy)
        assertNotNull("Legacy pipe string must be successfully parsed", parsed)
        assertEquals("RQ-1234", parsed?.messageId)
        assertEquals(28.6139, parsed?.latitude ?: 0.0, 0.0001)
        assertEquals(77.2090, parsed?.longitude ?: 0.0, 0.0001)
        assertEquals("Fire", parsed?.hazardType)
        assertEquals("Trauma", parsed?.medicalNeeds)
        assertEquals("2", parsed?.peopleCount)
    }
}