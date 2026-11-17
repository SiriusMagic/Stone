package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.automation.server.McpServer
import com.zirakusa.stone.nativeapp.automation.relay.RelayClient
import com.zirakusa.stone.nativeapp.data.library.artwork.CoverDownloader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/** Nothing secret travels unencrypted outside this phone. */
class TransportSecurityTest {
    @After fun stop() = McpServer.stop()

    @Test fun relayIsDialledOnlyOverHttpsOrOnThisMachine() {
        assertTrue(RelayClient.isAllowedRelay("https://stone-relay.example.workers.dev"))
        assertTrue(RelayClient.isAllowedRelay("http://127.0.0.1:8787"))
        assertTrue(RelayClient.isAllowedRelay("http://localhost:8787"))
        assertFalse(RelayClient.isAllowedRelay("http://stone-relay.example.workers.dev"))
        assertFalse(RelayClient.isAllowedRelay("http://192.168.1.20:8787"))
        assertFalse(RelayClient.isAllowedRelay("http://127.0.0.1.evil.example"))
        assertFalse(RelayClient.isAllowedRelay("http://localhost@evil.example"))
    }

    @Test fun coversAreDownloadedOnlyOverHttps() {
        for (url in listOf("http://example.com/a.jpg", "http://127.0.0.1:8765/mcp", "file:///sdcard/a.jpg")) {
            val message = runCatching { CoverDownloader.download(url) }.exceptionOrNull()?.message
            assertEquals(url, "The cover must be an https image URL", message)
        }
    }

    @Test fun serverIsNotReachableFromTheWifi() {
        assertTrue(McpServer.start("test-token"))
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", McpServer.DEFAULT_PORT), 2_000) }
        val lan = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
        for (address in lan) {
            val reached = runCatching { Socket().use { it.connect(InetSocketAddress(address, McpServer.DEFAULT_PORT), 1_000) } }.isSuccess
            assertFalse("reachable at ${address.hostAddress}", reached)
        }
    }
}
