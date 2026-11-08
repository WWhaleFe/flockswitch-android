package io.github.wwhalefe.flockswitch.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wwhalefe.flockswitch.BUTTON_GESTURE
import io.github.wwhalefe.flockswitch.BUTTON_MIDDLE
import io.github.wwhalefe.flockswitch.BUTTON_WHEEL_MODE
import io.github.wwhalefe.flockswitch.BUTTONS
import io.github.wwhalefe.flockswitch.BuildConfig
import io.github.wwhalefe.flockswitch.Config
import io.github.wwhalefe.flockswitch.DeviceInfo
import io.github.wwhalefe.flockswitch.Edge
import io.github.wwhalefe.flockswitch.Gesture
import io.github.wwhalefe.flockswitch.MainActivity
import io.github.wwhalefe.flockswitch.MoveSetting
import io.github.wwhalefe.flockswitch.R
import io.github.wwhalefe.flockswitch.RELEASES_PAGE
import io.github.wwhalefe.flockswitch.Shared
import io.github.wwhalefe.flockswitch.Status
import io.github.wwhalefe.flockswitch.UpdateState
import io.github.wwhalefe.flockswitch.checkForUpdate
import io.github.wwhalefe.flockswitch.pick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the screen asks the activity to do. */
class Actions(
    val setEnabled: (Boolean) -> Unit,
    val allowUsb: () -> Unit,
    val allowNearby: () -> Unit,
    val allowOverlay: () -> Unit,
    val notificationsOn: () -> Boolean,
    val switchTo: (Int) -> Unit,
    val open: (String) -> Unit,
    val report: () -> Unit,
)

private enum class Tab(@StringRes val title: Int, val glyph: String) {
    Home(R.string.tab_home, "⇄"),
    Buttons(R.string.tab_buttons, "◉"),
    Devices(R.string.tab_devices, "⌨"),
    General(R.string.tab_general, "⚙"),
}

@Composable
private fun theme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFAFC0FF), secondary = Color(0xFFC9B6F5))
        else -> lightColorScheme(primary = Color(0xFF3550D0), secondary = Color(0xFF7A55D6))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun FlockswitchApp(shared: Shared, actions: Actions) = theme {
    val cfg by shared.config.collectAsStateWithLifecycle()
    val status by shared.status.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val needOneButton = stringResource(R.string.need_one_button)
    val update: (Config.() -> Config) -> Unit = { change ->
        if (!shared.updateConfig(change)) scope.launch { snackbar.showSnackbar(needOneButton) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(t.glyph, style = MaterialTheme.typography.titleLarge) },
                        label = { Text(stringResource(t.title)) },
                    )
                }
            }
        },
    ) { padding ->
        // On a tablet the settings stay a readable column in the middle.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (Tab.entries[tab]) {
                    Tab.Home -> HomeTab(cfg, status, actions)
                    Tab.Buttons -> ButtonsTab(cfg, status, update, actions)
                    Tab.Devices -> DevicesTab(cfg, status, update, shared)
                    Tab.General -> GeneralTab(cfg, status, update, actions)
                }
            }
        }
    }
}

// Building blocks.

@Composable
private fun Section(title: String? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Notice(text: String, button: String? = null, onClick: () -> Unit = {}) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
            if (button != null) Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Hint(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A chosen "Off" segment: neutral gray (Apple's systemGray), not the accent colour, so
 *  it reads as switched off rather than as a choice made. */
private val OffGray = Color(0xFF8E8E93)

/** "Off" (0) and channels 1–3 to choose from; this device's own channel is shown but
 *  cannot be chosen. Off replaces an on/off switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelPicker(selected: Int?, here: Int?, onPick: (Int) -> Unit) {
    val chosen = selected ?: 0
    SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        for (c in 0..3) {
            val off = c == 0
            SegmentedButton(
                selected = chosen == c,
                onClick = { onPick(c) },
                enabled = c != here,
                shape = SegmentedButtonDefaults.itemShape(index = c, count = 4),
                colors = if (off) {
                    SegmentedButtonDefaults.colors(activeContainerColor = OffGray, activeContentColor = Color.White, activeBorderColor = OffGray)
                } else {
                    SegmentedButtonDefaults.colors()
                },
            ) { Text(if (off) stringResource(R.string.off) else "$c") }
        }
    }
    if (here != null) Hint(stringResource(R.string.here_channel, here))
}

/** One of a few fixed values, e.g. the time at the edge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ValuePicker(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onPick(value) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label) }
        }
    }
}

/** Shown as chosen: where a press goes from here (older settings may list several). */
private fun shownTarget(channels: List<Int>, here: Int?): Int? =
    if (here != null) pick(channels, here, 3) else channels.firstOrNull()

@Composable
private fun kindName(kind: String) = stringResource(
    when (kind) {
        "mouse" -> R.string.kind_mouse
        "keyboard" -> R.string.kind_keyboard
        else -> R.string.kind_other
    },
)

@Composable
private fun deviceSubtitle(d: DeviceInfo): String {
    val kind = kindName(d.kind)
    return if (d.online) "$kind · ${d.key}" else "$kind · ${d.key} · ${stringResource(R.string.away)}"
}

// Tabs.

@Composable
private fun HomeTab(cfg: Config, s: Status, actions: Actions) {
    Section {
        val headline = when {
            !cfg.enabled -> stringResource(R.string.status_paused)
            s.trigger != null -> s.trigger
            s.receivers == 0 && s.usbNeedsPermission == 0 -> stringResource(R.string.status_no_receiver)
            else -> stringResource(R.string.status_searching)
        }
        Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Hint(
            if (s.channel != null) stringResource(R.string.this_device_channel, s.channel)
            else stringResource(R.string.channel_unknown),
        )
        s.lastSwitch?.let { Hint(stringResource(R.string.last_switch, it)) }
        HorizontalDivider()
        SwitchRow(stringResource(R.string.enabled), cfg.enabled) { actions.setEnabled(it) }
    }

    if (s.usbNeedsPermission > 0) {
        Notice(stringResource(R.string.usb_permission_title) + "\n" + stringResource(R.string.usb_permission_text),
            stringResource(R.string.usb_permission_button), actions.allowUsb)
    }
    if (s.receiverBusy) Notice(stringResource(R.string.receiver_busy))
    if (s.serviceBlocked && s.usbNeedsPermission == 0) {
        Notice(stringResource(R.string.service_blocked), stringResource(R.string.grant), actions.allowNearby)
    }
    if (s.bluetoothNeedsPermission) {
        Notice(stringResource(R.string.bluetooth_permission), stringResource(R.string.grant), actions.allowNearby)
    }
    if (cfg.enabled && !actions.notificationsOn()) Notice(stringResource(R.string.notifications_off))
    if (s.receivers == 0 && s.usbNeedsPermission == 0 && s.trigger == null) {
        Section { Hint(stringResource(R.string.no_receiver_hint)) }
    }

    if (cfg.enabled && s.trigger != null) {
        Section(stringResource(R.string.switch_now)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 1..3) {
                    OutlinedButton(onClick = { actions.switchTo(c) }, enabled = c != s.channel, modifier = Modifier.weight(1f)) {
                        Text("$c")
                    }
                }
            }
            Hint(stringResource(R.string.switch_now_hint))
        }
    }
}

@Composable
private fun ButtonsTab(cfg: Config, s: Status, update: (Config.() -> Config) -> Unit, actions: Actions) {
    Hint(stringResource(R.string.buttons_hint))
    for (cid in BUTTONS) {
        val b = cfg.button(cid)
        val title = stringResource(
            when (cid) {
                BUTTON_GESTURE -> R.string.button_gesture
                BUTTON_MIDDLE -> R.string.button_middle
                else -> R.string.button_wheel_mode
            },
        )
        val missing = s.buttons.isNotEmpty() && cid !in s.buttons
        Section {
            // The button is turned off with the "Off" segment of its channel row.
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (missing) Hint(stringResource(R.string.not_on_mouse))
            Text(stringResource(R.string.channels), style = MaterialTheme.typography.labelLarge)
            ChannelPicker(if (b.on) shownTarget(b.channels, s.channel) else 0, s.channel) { c ->
                update { withButton(cid, if (c == 0) b.copy(on = false) else b.copy(on = true, channels = listOf(c))) }
            }
            if (cid == BUTTON_GESTURE) {
                Spacer(Modifier.height(4.dp))
                Hint(stringResource(R.string.gesture_hint))
                for (g in listOf(Gesture.Up, Gesture.Down, Gesture.Left, Gesture.Right)) {
                    val m = b.moves.get(g) ?: continue
                    HorizontalDivider()
                    val name = stringResource(
                        when (g) {
                            Gesture.Up -> R.string.gesture_up
                            Gesture.Down -> R.string.gesture_down
                            Gesture.Left -> R.string.gesture_left
                            else -> R.string.gesture_right
                        },
                    )
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    ChannelPicker(if (m.on) shownTarget(m.channels, s.channel) else 0, s.channel) { c ->
                        val next = if (c == 0) m.copy(on = false) else MoveSetting(true, listOf(c))
                        update { withButton(cid, b.copy(moves = b.moves.with(g, next))) }
                    }
                }
            }
        }
    }
    EdgesSection(cfg, s, update, actions)
}

/** Switching at the screen edge: the channel of each side and the time at the edge. */
@Composable
private fun EdgesSection(cfg: Config, s: Status, update: (Config.() -> Config) -> Unit, actions: Actions) {
    Section(stringResource(R.string.edges_title)) {
        Hint(stringResource(R.string.edges_hint))
        if (s.overlayNeeded) {
            Notice(stringResource(R.string.overlay_permission), stringResource(R.string.grant), actions.allowOverlay)
        }
        for (edge in Edge.entries) {
            HorizontalDivider()
            val name = stringResource(
                when (edge) {
                    Edge.Left -> R.string.edge_left
                    Edge.Right -> R.string.edge_right
                    Edge.Top -> R.string.edge_top
                    Edge.Bottom -> R.string.edge_bottom
                },
            )
            Text(name, style = MaterialTheme.typography.bodyLarge)
            ChannelPicker(cfg.edges.channel(edge), s.channel) { c -> update { copy(edges = edges.with(edge, c)) } }
        }
        HorizontalDivider()
        Text(stringResource(R.string.edge_dwell), style = MaterialTheme.typography.labelLarge)
        val options = listOf(300L to "0.3", 500L to "0.5", 1000L to "1", 2000L to "2")
            .map { (ms, text) -> ms to stringResource(R.string.seconds, text) }
        ValuePicker(options, cfg.edges.dwellMs) { ms -> update { copy(edges = edges.copy(dwellMs = ms)) } }
        Hint(stringResource(R.string.edges_corner_hint))
    }
}

@Composable
private fun DevicesTab(cfg: Config, s: Status, update: (Config.() -> Config) -> Unit, shared: Shared) {
    Section(stringResource(R.string.master)) {
        val auto = cfg.triggerDevice.equals("auto", ignoreCase = true)
        ChoiceRow(
            stringResource(R.string.master_auto),
            s.trigger?.let { stringResource(R.string.current, it) },
            auto,
            radio = true,
        ) { update { copy(triggerDevice = "auto") } }
        val masters = s.devices.filter { it.master }
        if (masters.isEmpty()) Hint(stringResource(R.string.no_master))
        for (d in masters) {
            ChoiceRow(d.name, deviceSubtitle(d), !auto && cfg.triggerDevice.equals(d.key, ignoreCase = true), radio = true) {
                update { copy(triggerDevice = d.key) }
            }
        }
    }
    Section(stringResource(R.string.follow)) {
        Hint(stringResource(R.string.follow_hint))
        val others = s.devices.filter { it.key != s.triggerKey }
        if (others.isEmpty()) Hint(stringResource(R.string.no_devices))
        val keys = others.map { it.key }
        for (d in others) {
            ChoiceRow(d.name, deviceSubtitle(d), cfg.follows(d.key.toIntOrNull(16), d.name), radio = false) {
                update { toggleFollow(keys, d.key) }
            }
        }
        if (s.devices.any { !it.online }) {
            TextButton(onClick = { shared.forgetDevices() }) { Text(stringResource(R.string.forget_devices)) }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, checked: Boolean, radio: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (radio) RadioButton(selected = checked, onClick = onClick) else Checkbox(checked = checked, onCheckedChange = { onClick() })
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Hint(subtitle)
        }
    }
}

@Composable
private fun GeneralTab(cfg: Config, s: Status, update: (Config.() -> Config) -> Unit, actions: Actions) {
    Section {
        SwitchRow(stringResource(R.string.start_at_boot), cfg.startAtBoot) { on -> update { copy(startAtBoot = on) } }
        HorizontalDivider()
        SwitchRow(stringResource(R.string.bluetooth_title), cfg.bluetooth, subtitle = stringResource(R.string.bluetooth_hint)) { on ->
            update { copy(bluetooth = on) }
            if (on) actions.allowNearby()
        }
    }

    Section(stringResource(R.string.section_haptics)) {
        SwitchRow(stringResource(R.string.haptics_switch), cfg.haptics.onSwitch) { on -> update { copy(haptics = haptics.copy(onSwitch = on)) } }
        HorizontalDivider()
        SwitchRow(stringResource(R.string.haptics_edge), cfg.haptics.onEdge) { on -> update { copy(haptics = haptics.copy(onEdge = on)) } }
        HorizontalDivider()
        Text(stringResource(R.string.haptics_intensity), style = MaterialTheme.typography.labelLarge)
        // 0 = as Logi Options+ sets it; otherwise the strength in percent.
        val levels = listOf(0 to stringResource(R.string.haptics_follow), 25 to "25%", 50 to "50%", 75 to "75%", 100 to "100%")
        ValuePicker(levels, cfg.haptics.intensity) { level -> update { copy(haptics = haptics.copy(intensity = level)) } }
        Hint(stringResource(R.string.haptics_hint))
    }

    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Unknown) }
    val scope = rememberCoroutineScope()
    val check: () -> Unit = {
        updateState = UpdateState.Checking
        scope.launch { updateState = withContext(Dispatchers.IO) { checkForUpdate(BuildConfig.VERSION_NAME) } }
    }
    LaunchedEffect(Unit) { if (cfg.checkUpdates && updateState == UpdateState.Unknown) check() }
    Section(stringResource(R.string.section_update)) {
        Text(stringResource(R.string.version, BuildConfig.VERSION_NAME))
        SwitchRow(stringResource(R.string.update_auto), cfg.checkUpdates) { on -> update { copy(checkUpdates = on) } }
        val text = when (val u = updateState) {
            UpdateState.Unknown -> null
            UpdateState.Checking -> stringResource(R.string.update_checking)
            UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
            UpdateState.Failed -> stringResource(R.string.update_failed)
            is UpdateState.Available -> stringResource(R.string.update_available, u.version)
        }
        text?.let { Hint(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = check, enabled = updateState != UpdateState.Checking) { Text(stringResource(R.string.update_check)) }
            if (updateState is UpdateState.Available) {
                Button(onClick = { actions.open(RELEASES_PAGE) }) { Text(stringResource(R.string.update_page)) }
            }
        }
    }

    Section(stringResource(R.string.section_report)) {
        Hint(stringResource(R.string.report_desc))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.report) { Text(stringResource(R.string.report_mail)) }
            OutlinedButton(onClick = { actions.open(MainActivity.ISSUES_PAGE) }) { Text(stringResource(R.string.report_issue)) }
        }
    }
    Spacer(Modifier.height(8.dp))
}
