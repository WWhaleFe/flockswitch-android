package io.github.wwhalefe.flockswitch

import io.github.wwhalefe.flockswitch.net.NetworkConfig
import io.github.wwhalefe.flockswitch.net.NetworkPeer
import io.github.wwhalefe.flockswitch.net.hidKeyCode
import io.github.wwhalefe.flockswitch.ui.normalizeNetworkAddress
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class NetworkConfigTest {
    @Test fun networkConsentIsExplicitAndPersistsInCoreJson() {
        val old = Config.fromJson("""{"network":{"on":true}}""").network
        assertFalse(old.notice_accepted)
        val accepted = old.copy(notice_accepted = true)
        assertTrue(Config.fromJson(Config(network = accepted).toJson()).network.notice_accepted)
        assertTrue(Config.json.parseToJsonElement(accepted.toCoreJson("phone")).jsonObject
            .getValue("notice_accepted").jsonPrimitive.boolean)
        assertFalse(Config.json.parseToJsonElement(old.toCoreJson()).jsonObject
            .getValue("notice_accepted").jsonPrimitive.boolean)
    }

    @Test fun changedPeerUpdatesOnlyAnExistingTrustedIdentity() {
        val peer = NetworkPeer(id = "master", fingerprint = "trusted", last_addr = "10.0.0.1:47160")
        val config = NetworkConfig(peers = listOf(peer))
        val changed = peer.copy(last_addr = "10.0.0.2:47160", side = "right")
        assertEquals(changed, config.withChangedPeer(changed).peers.single())
        assertEquals(config, config.withChangedPeer(changed.copy(fingerprint = "other")))
        assertEquals(config, config.withChangedPeer(changed.copy(id = "unpaired")))
        assertEquals(changed, Config.fromJson(Config(network = config.withChangedPeer(changed)).toJson()).network.peers.single())
    }
    @Test fun legacyAndCoreSchema() {
        assertFalse(Config.fromJson("{}").network.on)
        val n = NetworkConfig(on = true, peers = listOf(NetworkPeer(id = "abc", side = "left", os = "windows", logitech_channel = 2)))
        val c = Config(network = n)
        assertEquals(c, Config.fromJson(c.toJson()))
        val root = Config.json.parseToJsonElement(n.toCoreJson()).jsonObject
        assertTrue(root.keys.containsAll(listOf("swap_cmd_ctrl", "lang1_on_mac", "cross_delay_ms", "peers")))
        assertFalse(root.containsKey("swapCmdCtrl"))
        val header = SettingsFile.header("2.0.0", "phone", LocalDateTime.of(2026, 11, 24, 1, 0))
        assertEquals(ImportResult.Ok(c.withoutNetworkTrust(), header), SettingsFile.read(SettingsFile.write(c, header)))
        val mine = Config(network = NetworkConfig(on = true, notice_accepted = true, name = "mine", port = 47161, peers = n.peers))
        val loaded = (SettingsFile.read(SettingsFile.write(Config(network = NetworkConfig(name = "file", port = 47170)), header)) as ImportResult.Ok).config
        val kept = loaded.keepingTrustOf(mine).network
        assertTrue(kept.on && kept.notice_accepted)
        assertEquals(n.peers, kept.peers)
        assertEquals("mine", kept.name)
        assertEquals(47161, kept.port)
        assertTrue(root.keys.contains("share_clipboard"))
        assertTrue(n.share_clipboard)
        assertFalse(Config.fromJson(Config(network = n.copy(share_clipboard = false)).toJson()).network.share_clipboard)
        assertTrue(n.copy(port = 0).problems().isNotEmpty())
        assertTrue(n.copy(peers = listOf(NetworkPeer(id = "abc", side = "invalid"))).problems().isNotEmpty())
    }
    @Test fun portRangeMatchesTheCore() {
        for (p in listOf(0, 1, 1023, 5353, 47851, 47852, 65536, -1)) assertFalse("$p", NetworkConfig.validPort(p))
        for (p in listOf(1024, 47160, 65535)) assertTrue("$p", NetworkConfig.validPort(p))
        assertTrue(NetworkConfig(port = 80).problems().isNotEmpty())
        // A port an earlier version saved while typing does not make the whole file unusable.
        val old = Config.fromJson(Config(network = NetworkConfig(port = 47)).toJson().replace("\"port\":47160", "\"port\":47"))
        assertEquals(NetworkConfig.DEFAULT_PORT, old.network.port)
    }
    @Test fun settingsFilesNeverCarryConsentOrPeers() {
        val peer = NetworkPeer(id = "master", fingerprint = "fp", last_addr = "10.0.0.1:47160")
        val c = Config(enabled = false, network = NetworkConfig(on = true, notice_accepted = true, peers = listOf(peer), port = 50000))
        val header = SettingsFile.header("2.1.0", "phone", LocalDateTime.of(2026, 11, 27, 1, 0))
        val text = SettingsFile.write(c, header)
        val network = Config.json.parseToJsonElement(text).jsonObject.getValue("network").jsonObject
        assertFalse(network.getValue("on").jsonPrimitive.boolean)
        assertFalse(network.getValue("notice_accepted").jsonPrimitive.boolean)
        // A file written by hand (or by an older version) with them set is cleaned on reading.
        val dirty = text.replace("\"notice_accepted\":false", "\"notice_accepted\":true")
            .replace("\"on\":false", "\"on\":true").replace("\"peers\":[]", "\"peers\":[{\"id\":\"x\"}]")
        val read = (SettingsFile.read(dirty) as ImportResult.Ok).config
        assertFalse(read.network.on)
        assertFalse(read.network.notice_accepted)
        assertTrue(read.network.peers.isEmpty())
        assertEquals(50000, read.network.port)
        // Receiving over the network was the only thing on: a button is turned on instead.
        assertTrue(read.triggers().isNotEmpty())
    }
    @Test fun serviceRunsOnlyWhileSomethingNeedsIt() {
        assertTrue(Config().serviceNeeded())
        val off = Config().copy(enabled = false)
        assertFalse(off.serviceNeeded())
        assertFalse(off.copy(network = NetworkConfig(on = true)).serviceNeeded())
        assertTrue(off.copy(network = NetworkConfig(on = true, notice_accepted = true)).serviceNeeded())
    }
    @Test fun configWithoutIdentityKeyLosesConsentAndPeers() {
        val dir = java.nio.file.Files.createTempDirectory("flockswitch-cfg").toFile()
        try {
            val peer = NetworkPeer(id = "master", fingerprint = "fp")
            java.io.File(dir, "config.json").writeText(Config(network = NetworkConfig(on = true, notice_accepted = true, peers = listOf(peer))).toJson())
            val restored = loadConfig(dir)
            assertFalse(restored.network.on)
            assertFalse(restored.network.notice_accepted)
            assertTrue(restored.network.peers.isEmpty())
            identityKeyFile(dir).apply { parentFile!!.mkdirs(); writeText("key") }
            val kept = loadConfig(dir)
            assertTrue(kept.network.on && kept.network.notice_accepted)
            assertEquals(listOf(peer), kept.network.peers)
        } finally { dir.deleteRecursively() }
    }
    @Test fun manualAddress() {
        assertEquals("192.168.1.2:47160", normalizeNetworkAddress("192.168.1.2"))
        assertEquals("192.168.1.2:1234", normalizeNetworkAddress("192.168.1.2:1234"))
        assertEquals("[::1]:47160", normalizeNetworkAddress("::1"))
        assertNull(normalizeNetworkAddress("999.1.2.3"))
        assertNull(normalizeNetworkAddress("[::1]1234"))
        assertNull(normalizeNetworkAddress("[::::]:47160"))
        assertNull(normalizeNetworkAddress("[::1]:"))
        assertNull(normalizeNetworkAddress("192.168.1.2:65536"))
    }
    @Test fun hidCoverage() {
        for (usage in (4..0x65) + (0xe0..0xe7)) assertNotNull("HID $usage", hidKeyCode(usage))
        assertNull(hidKeyCode(0xffff))
    }
}
