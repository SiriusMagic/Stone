package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.HubLink
import com.zirakusa.stone.nativeapp.automation.cloudflare.Cloudflare
import com.zirakusa.stone.nativeapp.automation.cloudflare.CloudflareException
import com.zirakusa.stone.nativeapp.automation.cloudflare.CloudflareTransport
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The Cloudflare token: checked, kept encrypted, shared with Gateway Hub only through the locked channel. */
@RunWith(RobolectricTestRunner::class)
class CloudflareTest {
    private val sent = mutableListOf<String>()
    private val ok = { r: String -> 200 to """{"success":true,"result":$r}""" }
    private fun cloud(status: String = "active") = CloudflareTransport { _, url, token ->
        sent += "$token ${url.removePrefix(Cloudflare.BASE)}"
        when (url.removePrefix(Cloudflare.BASE)) {
            "/user/tokens/verify" -> ok("""{"status":"$status","expires_on":"2030-01-08T00:00:00Z"}""")
            "/accounts" -> ok("""[{"id":"a1"}]""")
            "/accounts/a1/workers/scripts" -> ok("""[{"id":"stone-hub"},{"id":"stone-relay"}]""")
            "/accounts/a1/workers/subdomain" -> ok("""{"subdomain":"ana"}""")
            else -> 404 to "{}"
        }
    }

    @Before fun setUp() { Cloudflare.load(StoneSettingsStore(RuntimeEnvironment.getApplication())); Cloudflare.forget() }
    @After fun reset() { Cloudflare.forget(); ApiMode.allowHubRedirect(false); ApiMode.relayUrl = "" }

    @Test fun updateChecksTheTokenAndKeepsItsExpiry() {
        Cloudflare.transport = cloud()
        Cloudflare.update("  cf_ONE  ")
        assertEquals("cf_ONE", Cloudflare.token())
        assertEquals(java.time.Instant.parse("2030-01-08T00:00:00Z").toEpochMilli(), Cloudflare.expiresAt)
        assertTrue(sent.all { it.startsWith("cf_ONE /") })              // only in the header, never in the URL
        Cloudflare.transport = cloud("disabled")
        assertEquals("INACTIVE", runCatching { Cloudflare.update("cf_TWO") }.exceptionOrNull().let { (it as CloudflareException).code })
        assertEquals("cf_ONE", Cloudflare.token())                      // a bad token never replaces a good one
    }

    @Test fun theTunnelIsFoundInTheAccount() {
        Cloudflare.transport = cloud()
        assertEquals("https://stone-relay.ana.workers.dev", Cloudflare.relayAddress("cf"))
    }

    @Test fun gatewayGetsAndGivesTheTokenOnlyWithEveryLock() {
        Cloudflare.transport = cloud(); Cloudflare.update("cf_STONE")
        assertNull(HubLink.answerCloudflare(false, null, 0, 0).getString("token"))
        assertEquals("REDIRECT_OFF", HubLink.answerCloudflare(true, null, 0, 0).getString(HubLink.KEY_ERROR))
        ApiMode.allowHubRedirect(true)
        assertEquals("cf_STONE", HubLink.answerCloudflare(true, null, 0, 0).getString("token"))
        // An older token from Gateway is ignored; a newer one replaces ours.
        assertFalse(HubLink.answerCloudflare(true, "cf_OLD", 0, 1).getBoolean("saved"))
        assertTrue(HubLink.answerCloudflare(true, "cf_NEW", 5, Long.MAX_VALUE).getBoolean("saved"))
        assertEquals("cf_NEW", Cloudflare.token()); assertEquals(5L, Cloudflare.expiresAt)
    }

    @Test fun daysLeft() {
        assertNull(Cloudflare.daysLeft())
        Cloudflare.receive("x", 10 * 86_400_000L, 1)
        assertEquals(9L, Cloudflare.daysLeft(now = 1))
    }
}
