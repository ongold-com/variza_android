package com.variza.smsforwarder.util

import com.variza.smsforwarder.net.Sender
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneMatchTest {
    @Test fun internationalAndLocalFormatsMatch() {
        assertTrue(PhoneMatch.matches("+989121234567", "09121234567"))
        assertTrue(PhoneMatch.matches("00989121234567", "+989121234567"))
        assertTrue(PhoneMatch.matches("09121234567", "9121234567"))
    }

    @Test fun differentNumbersDoNotMatch() {
        assertFalse(PhoneMatch.matches("+989121234567", "09121234568"))
    }

    @Test fun persianDigitsAreNormalized() {
        assertTrue(PhoneMatch.matches("+989121234567", "۰۹۱۲۱۲۳۴۵۶۷"))
    }

    @Test fun shortCodesNeedExactMatch() {
        assertTrue(PhoneMatch.matches("3000", "3000"))
        assertFalse(PhoneMatch.matches("3000", "30001"))
    }

    @Test fun namedSendersIgnoreCase() {
        assertTrue(PhoneMatch.matches("BankMelli", "bankmelli"))
        assertFalse(PhoneMatch.matches("BankMelli", "BankSepah"))
    }

    @Test fun persianNamesIgnoreSpacesAndArabicLetters() {
        assertTrue(PhoneMatch.matches("بانک رسالت", "بانکرسالت"))
        assertTrue(PhoneMatch.matches("عليرضا", "علیرضا"))
        assertFalse(PhoneMatch.matches("رسالت", "علی"))
    }

    @Test fun findMatchReturnsEntry() {
        assertEquals("0912 123 4567", PhoneMatch.findMatch("+989121234567", listOf("Bank", "0912 123 4567")))
        assertEquals(null, PhoneMatch.findMatch("X", listOf("Y")))
    }

    @Test fun emptyNeverMatches() {
        assertFalse(PhoneMatch.matches("", "09121234567"))
        assertFalse(PhoneMatch.matchesAny("09121234567", emptyList()))
    }

    @Test fun payloadHasExpectedFields() {
        val o = JSONObject(Sender.buildJson("+98912", "hello", 123L, 1, 5))
        assertEquals("+98912", o.getString("from"))
        assertEquals("hello", o.getString("body"))
        assertEquals(123L, o.getLong("receivedAt"))
        assertEquals(1, o.getInt("simSlot"))
        assertEquals(5, o.getInt("subscriptionId"))
    }

    @Test fun backendResultMustBeParsedAndMatched() {
        val ok = Sender.checkResults(
            """{"results":[{"parsed":true,"matched":true,"duplicate":false,"code":"1","amount":71156,"bankId":"resalat"}],"matchedCode":"1"}"""
        )
        assertTrue(ok.ok)
        assertEquals(71156L, ok.amount)
        assertEquals("resalat", ok.bankId)
        assertFalse(Sender.checkResults("""{"results":[{"parsed":true,"matched":false}]}""").ok)
        assertFalse(Sender.checkResults("""{"results":[{"parsed":false,"matched":true}]}""").ok)
        assertFalse(Sender.checkResults("""{"results":[]}""").ok)
        assertFalse(Sender.checkResults("not json").ok)
    }
}
