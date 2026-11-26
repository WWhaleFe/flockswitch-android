package io.github.wwhalefe.flockswitch

import io.github.wwhalefe.flockswitch.net.NetworkConfig
import io.github.wwhalefe.flockswitch.net.NetworkPeer
import io.github.wwhalefe.flockswitch.net.hidKeyCode
import io.github.wwhalefe.flockswitch.ui.normalizeNetworkAddress
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class NetworkConfigTest {
    @Test fun legacyAndCoreSchema() {
        assertFalse(Config.fromJson("{}").network.on)
        val n = NetworkConfig(on = true, peers = listOf(NetworkPeer(id = "abc", side = "left", os = "windows", logitech_channel = 2)))
        val c = Config(network = n)
        assertEquals(c, Config.fromJson(c.toJson()))
        val root = Config.json.parseToJsonElement(n.toCoreJson()).jsonObject
        assertTrue(root.keys.containsAll(listOf("swap_cmd_ctrl", "lang1_on_mac", "cross_delay_ms", "peers")))
        assertFalse(root.containsKey("swapCmdCtrl"))
        val header = SettingsFile.header("2.0.0", "phone", LocalDateTime.of(2026, 11, 24, 1, 0))
        assertEquals(ImportResult.Ok(c, header), SettingsFile.read(SettingsFile.write(c, header)))
        assertTrue(n.copy(port = 0).problems().isNotEmpty())
        assertTrue(n.copy(peers = listOf(NetworkPeer(id = "abc", side = "invalid"))).problems().isNotEmpty())
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
