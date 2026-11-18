package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.auth.ApiTokens
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stone's side of the Gateway Hub: one reserved token, reused, revocable from Stone. */
class GatewayHubLinkTest {
    @After fun reset() { ApiMode.relayUrl = ""; ApiMode.hubUrl = ""; ApiTokens.replaceForTests(emptyList()) }

    @Test fun hubCardNeedsBothTheHubAndTheRelay() {
        ApiMode.hubUrl = "https://hub.example"; ApiMode.relayUrl = ""
        assertFalse(ApiMode.hubAvailable)
        ApiMode.relayUrl = "https://relay.example"
        assertTrue(ApiMode.hubAvailable)
        assertEquals("https://hub.example/admin", ApiMode.hubAdminUrl)
    }

    @Test fun theHubTokenIsCreatedOnceWithWriteModeAndReused() {
        ApiTokens.replaceForTests(emptyList())
        val first = ApiMode.hubToken()
        assertEquals(ApiMode.HUB_TOKEN_NAME, first.name)
        assertEquals(AccessMode.WRITE, first.mode) // critical actions still ask on the phone
        assertEquals(first.secret, ApiMode.hubToken().secret)
        assertEquals(1, ApiTokens.tokens.size)
        ApiTokens.revoke(first.id) // revoking it in Stone cuts the hub off; linking again makes a new one
        assertNotEquals(first.secret, ApiMode.hubToken().secret)
    }

    @Test fun theCopiedLinkPointsToThisPhoneThroughTheRelay() {
        ApiMode.relayUrl = "https://relay.example"
        val link = ApiMode.shareText(ApiMode.hubToken().secret)
        assertTrue(link, link.startsWith("https://relay.example/d/") && link.contains("/mcp?token=stn_"))
    }
}
