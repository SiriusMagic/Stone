package com.zirakusa.stone.nativeapp

import android.content.pm.PackageManager
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.HubLink
import com.zirakusa.stone.nativeapp.automation.auth.ApiTokens
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Redirección hub: three locks (signature permission, caller signature, user switch) before any link. */
@RunWith(RobolectricTestRunner::class)
class HubLinkTest {
    @After fun reset() { ApiMode.allowHubRedirect(false); ApiMode.relayUrl = ""; ApiTokens.replaceForTests(emptyList()) }

    @Test fun theProviderIsProtectedByASignaturePermission() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS)
        val provider = info.providers!!.single { it.authority == "${context.packageName}.hublink" }
        assertTrue(provider.exported)
        assertEquals("com.zirakusa.gatewayhub.permission.LINK", provider.readPermission)
        val permission = info.permissions!!.single { it.name == "com.zirakusa.gatewayhub.permission.LINK" }
        assertEquals(android.content.pm.PermissionInfo.PROTECTION_SIGNATURE, permission.protection)
    }

    @Test fun nothingIsGivenWithoutEveryLock() {
        ApiMode.relayUrl = "https://relay.example"
        ApiMode.allowHubRedirect(true)
        assertEquals("NOT_SAME_DEVELOPER", HubLink.answer(sameDeveloper = false).getString(HubLink.KEY_ERROR))
        ApiMode.allowHubRedirect(false)
        assertEquals("REDIRECT_OFF", HubLink.answer(sameDeveloper = true).getString(HubLink.KEY_ERROR))
        assertTrue(ApiTokens.tokens.isEmpty()) // refused calls never create the hub token
    }

    @Test fun withEveryLockOpenTheHubGetsItsReservedLink() {
        ApiMode.relayUrl = "https://relay.example"
        ApiMode.allowHubRedirect(true)
        ApiMode.enable(true)
        try {
            val answer = HubLink.answer(sameDeveloper = true)
            assertNull(answer.getString(HubLink.KEY_ERROR))
            val link = answer.getString(HubLink.KEY_LINK)!!
            assertTrue(link, link.startsWith("https://relay.example/d/") && link.contains("token=stn_"))
            assertEquals(link, HubLink.answer(sameDeveloper = true).getString(HubLink.KEY_LINK)) // same token reused
            assertEquals(ApiMode.HUB_TOKEN_NAME, ApiTokens.tokens.single().name)
        } finally { ApiMode.enable(false) }
    }
}
