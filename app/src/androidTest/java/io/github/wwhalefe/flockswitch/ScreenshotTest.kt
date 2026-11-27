package io.github.wwhalefe.flockswitch

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wwhalefe.flockswitch.net.DirRules
import io.github.wwhalefe.flockswitch.net.NetCell
import io.github.wwhalefe.flockswitch.net.NetMe
import io.github.wwhalefe.flockswitch.net.NetPeerState
import io.github.wwhalefe.flockswitch.net.NetStatus
import io.github.wwhalefe.flockswitch.net.NetworkConfig
import io.github.wwhalefe.flockswitch.net.NetworkPeer
import io.github.wwhalefe.flockswitch.ui.Actions
import io.github.wwhalefe.flockswitch.ui.FlockswitchApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Store screenshots: the main screens with made-up state, no pairing and no network. The
 *  PNGs go to the app's external files folder ("shots"), where the workflow pulls them from. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun korean() = shoot(Locale.KOREAN)

    @Test fun english() = shoot(Locale.ENGLISH)

    private fun shoot(locale: Locale) {
        val shared = target.shared
        val master = NetworkPeer(id = "mac-mini", name = "Mac mini", os = "mac", fingerprint = "demo", side = "left")
        shared.updateConfig {
            it.copy(
                enabled = true,
                network = NetworkConfig(on = true, notice_accepted = true, name = "Pixel", peers = listOf(master)),
            )
        }
        shared.setStatus {
            it.copy(
                running = true, trigger = "MX Master 3S", triggerKey = "B034", channel = 1, lastSwitch = "2 → 1",
                receivers = 1, pointerService = true,
                devices = listOf(
                    DeviceInfo("B034", "MX Master 3S", "mouse", master = true, online = true),
                    DeviceInfo("B35B", "MX Keys", "keyboard", master = false, online = true),
                ),
            )
        }
        val rules = mapOf("mac>android" to DirRules(hangul_src = listOf("caps_lock", "lang1")))
        val net = NetStatus(
            running = true,
            me = NetMe(id = "demo-phone", name = "Pixel", port = 47160),
            peers = listOf(NetPeerState(id = master.id, connected = true, rtt_ms = 3.4, name = master.name, os = "mac", keyrules = true)),
            layout = listOf(NetCell("mac-mini", 0, 0), NetCell("demo-phone", 1, 0)),
            keys = rules,
            following = master.name,
        )
        shared.keyRuleEdits.observe(rules, following = true)

        Locale.setDefault(locale)
        val config = Configuration(target.resources.configuration).apply { setLocale(locale) }
        val localized = target.createConfigurationContext(config)
        val actions = Actions(
            {}, {}, {}, {}, {}, { _, _, _ -> }, {}, {}, { true }, { true }, { true }, {}, {}, {}, { _, _ -> },
            {}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _, _ -> }, { _, _ -> }, {},
        )
        rule.setContent {
            val base = LocalContext.current
            CompositionLocalProvider(
                LocalContext provides object : android.content.ContextWrapper(base) {
                    override fun getResources() = localized.resources
                },
                LocalConfiguration provides config,
                LocalResources provides localized.resources,
            ) { FlockswitchApp(shared, actions, net) }
        }

        // Both runs must see the same display; a different density or font scale is a failure.
        val metrics = target.resources.displayMetrics
        check(metrics.densityDpi == 420 && target.resources.configuration.fontScale == 1f) {
            "unexpected display: ${metrics.densityDpi} dpi, font scale ${target.resources.configuration.fontScale}"
        }

        val lang = locale.language
        gotoTab(0)
        snap(lang, 1, "home")
        gotoTab(1)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(localizedString(R.string.net_keys_title)).fetchSemanticsNodes().isNotEmpty()
        }
        snap(lang, 2, "network")
        scrollNear(R.string.net_keys_title)
        snap(lang, 3, "keys")
        scrollNear(R.string.net_clipboard_receive)
        snap(lang, 4, "clipboard")
        gotoTab(3)
        snap(lang, 5, "effects")
    }

    /** Clicks the bottom-bar tab and fails unless it really became the selected one. */
    private fun gotoTab(index: Int) {
        val tag = "tab$index"
        repeat(3) {
            if (rule.onAllNodes(hasTestTag(tag) and isSelected()).fetchSemanticsNodes().isNotEmpty()) return
            dismissSystemDialogs()
            rule.onNodeWithTag(tag).performClick()
            rule.waitForIdle()
            runCatching { rule.waitUntil(3_000) { rule.onAllNodes(hasTestTag(tag) and isSelected()).fetchSemanticsNodes().isNotEmpty() } }
        }
        rule.onNode(hasTestTag(tag) and isSelected()).assertExists("tab $index was not selected")
    }

    /** Brings the text to the upper part of the page (the page cannot scroll past its end). */
    private fun scrollNear(res: Int) {
        val node: SemanticsNodeInteraction = rule.onNodeWithText(localizedString(res))
        node.performScrollTo()
        val height = rule.onNode(hasScrollAction()).fetchSemanticsNode().size.height
        rule.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, height * 0.8f) }
    }

    private fun localizedString(res: Int): String {
        val config = Configuration(target.resources.configuration).apply { setLocale(Locale.getDefault()) }
        return target.createConfigurationContext(config).getString(res)
    }

    /** Closes an ANR or other system dialog ("Wait", "Close app", "OK") so it never lands in a shot. */
    private fun dismissSystemDialogs() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(3) {
            val button = listOf("Wait", "Close app", "대기", "앱 닫기")
                .firstNotNullOfOrNull { device.findObject(By.text(it).pkg("android")) ?: device.findObject(By.text(it).pkg("com.android.systemui")) }
                ?: return
            button.click()
            device.waitForIdle()
        }
    }

    private fun snap(lang: String, index: Int, name: String) {
        dismissSystemDialogs()
        rule.waitForIdle()
        Thread.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(target.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "$lang-$index-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
