package io.github.wwhalefe.flockswitch.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wwhalefe.flockswitch.BUTTON_GESTURE
import io.github.wwhalefe.flockswitch.BUTTON_MIDDLE
import io.github.wwhalefe.flockswitch.BUTTON_WHEEL_MODE
import io.github.wwhalefe.flockswitch.BUTTONS
import io.github.wwhalefe.flockswitch.BackupState
import io.github.wwhalefe.flockswitch.BuildConfig
import io.github.wwhalefe.flockswitch.COLOR_PRESETS
import io.github.wwhalefe.flockswitch.Config
import io.github.wwhalefe.flockswitch.DeviceInfo
import io.github.wwhalefe.flockswitch.DRAWN_EFFECTS
import io.github.wwhalefe.flockswitch.EFFECT_SIZES
import io.github.wwhalefe.flockswitch.EffectLook
import io.github.wwhalefe.flockswitch.Edge
import io.github.wwhalefe.flockswitch.EdgeAnimDirection
import io.github.wwhalefe.flockswitch.EdgeAnimStyle
import io.github.wwhalefe.flockswitch.Edges
import io.github.wwhalefe.flockswitch.Gesture
import io.github.wwhalefe.flockswitch.HapticPreview
import io.github.wwhalefe.flockswitch.ImportRejection
import io.github.wwhalefe.flockswitch.Haptics
import io.github.wwhalefe.flockswitch.Locate
import io.github.wwhalefe.flockswitch.LocateStyle
import io.github.wwhalefe.flockswitch.MainActivity
import io.github.wwhalefe.flockswitch.ManageOutcome
import io.github.wwhalefe.flockswitch.MoveSetting
import io.github.wwhalefe.flockswitch.R
import io.github.wwhalefe.flockswitch.RenameProblem
import io.github.wwhalefe.flockswitch.SavedFile
import io.github.wwhalefe.flockswitch.SettingsFile
import io.github.wwhalefe.flockswitch.RELEASES_PAGE
import io.github.wwhalefe.flockswitch.Shared
import io.github.wwhalefe.flockswitch.Status
import io.github.wwhalefe.flockswitch.UpdateFailure
import io.github.wwhalefe.flockswitch.UpdateState
import io.github.wwhalefe.flockswitch.Waveform
import io.github.wwhalefe.flockswitch.formatColor
import io.github.wwhalefe.flockswitch.parseColor
import io.github.wwhalefe.flockswitch.pick
import io.github.wwhalefe.flockswitch.pointerReadable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the screen asks the activity to do. */
class Actions(
    val setEnabled: (Boolean) -> Unit,
    val allowUsb: () -> Unit,
    val allowNearby: () -> Unit,
    val allowOverlay: () -> Unit,
    val previewEdge: (Edges) -> Unit,
    val previewLocate: (Locate, Float, Float) -> Unit,
    val openAccessibility: () -> Unit,
    val openAppInfo: () -> Unit,
    val overlayOn: () -> Boolean,
    val notificationsOn: () -> Boolean,
    val switchTo: (Int) -> Unit,
    val open: (String) -> Unit,
    val report: () -> Unit,
    /** Plays a haptics choice on the master mouse at a strength (0 = as the mouse is set). */
    val feel: (HapticPreview, Int) -> Unit,
    /** Asks GitHub for a new version (and installs it if that is turned on). */
    val checkUpdate: () -> Unit,
    /** Downloads, verifies and installs the new version, or shows Android's waiting prompt. */
    val updateNow: () -> Unit,
    /** Saves the settings to a file the user picks. */
    val saveSettings: () -> Unit,
    /** Picks a settings file to load (asked to confirm on the screen first). */
    val loadSettings: () -> Unit,
    /** Shows the folder the settings were last saved to. */
    val openSettingsFolder: () -> Unit,
    /** Puts the settings read from the named file in place. */
    val applySettings: (Config, String) -> Unit,
    /** Takes saved settings files that are gone off the list. */
    val refreshSaved: () -> Unit,
    /** Renames a saved file to the name typed (already checked), keeping ".json". */
    val renameSaved: (SavedFile, String, (ManageOutcome) -> Unit) -> Unit,
    /** Deletes a saved file from the device and the list. */
    val deleteSaved: (SavedFile, (ManageOutcome) -> Unit) -> Unit,
    /** Takes a saved file off the list only. */
    val forgetSaved: (SavedFile) -> Unit,
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

    // A phone keeps the bar at the bottom. An open foldable, a tablet or a phone on its
    // side has room beside the page instead; a cover screen gets one thin row.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cover = maxWidth < 360.dp && maxHeight < 480.dp
        val rail = !cover && (maxWidth >= 600.dp || maxHeight < 480.dp)
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { if (cover) CompactTabs(tab) { tab = it } },
            bottomBar = {
                if (!cover && !rail) {
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
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (rail) {
                    NavigationRail {
                        Tab.entries.forEachIndexed { i, t ->
                            NavigationRailItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Text(t.glyph, style = MaterialTheme.typography.titleLarge) },
                                label = { Text(stringResource(t.title)) },
                            )
                        }
                    }
                }
                // On a tablet the settings stay a readable column in the middle.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .padding(horizontal = if (cover) 8.dp else 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (Tab.entries[tab]) {
                            Tab.Home -> HomeTab(cfg, status, actions)
                            Tab.Buttons -> ButtonsTab(cfg, status, update, actions)
                            Tab.Devices -> DevicesTab(cfg, status, update, shared)
                            Tab.General -> GeneralTab(cfg, status, shared.update, shared.backup, shared.saved, shared::setBackup, update, actions)
                        }
                    }
                }
            }
        }
    }
}

/** The tabs as one thin row, for a cover screen. */
@Composable
private fun CompactTabs(tab: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Tab.entries.forEachIndexed { i, t ->
            TextButton(onClick = { onPick(i) }) {
                Text(t.glyph, color = if (tab == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Building blocks. Every tab and dialog is made of these, so heights, widths, gaps and
// text styles match everywhere: change a measure here, not at the place it is used.

/** Every button, drop-down button and segment row is this tall. */
private val ControlHeight = 40.dp

/** Buttons are at least this wide, so a short label lines up with a long one. */
private val ButtonMinWidth = 96.dp

/** Between the items of a section or a dialog. */
private val ItemGap = 10.dp

/** Between a row's label and its control. */
private val LabelGap = 6.dp

/** Above and below a divider, on top of [ItemGap]; the same everywhere. */
private val DividerPad = 4.dp

/** A colour swatch, the "+" one included: eight in a row fit a 360 dp wide phone. */
private val SwatchSize = 30.dp

/** A row with a switch, radio button or check box is at least as tall as the switch, so a
 *  divider is as far from it as from a label. Compose still gives it a 48 dp target. */
private val ToggleRowHeight = 32.dp

/** Inside every button: the same for filled, outlined and text buttons. */
private val ButtonPadding = PaddingValues(horizontal = 16.dp)

@Composable
private fun Section(
    title: String? = null,
    /** A small button at the end of the title, e.g. [InfoIcon]. */
    info: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
            if (title != null && info != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
                    info()
                }
            } else if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/** Between the parts of a section. */
@Composable
private fun SectionDivider() = HorizontalDivider(Modifier.padding(vertical = DividerPad))

@Composable
private fun Notice(text: String, button: String? = null, onClick: () -> Unit = {}) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
            if (button != null) AppButton(button, onClick, kind = ButtonKind.Filled)
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

private enum class ButtonKind { Filled, Outlined, Text }

/** Every button: one height, a minimum width, the same padding and text. */
@Composable
private fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Outlined,
    enabled: Boolean = true,
) {
    val m = modifier.heightIn(min = ControlHeight).widthIn(min = ButtonMinWidth)
    val label: @Composable RowScope.() -> Unit = {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
    when (kind) {
        ButtonKind.Filled -> Button(onClick = onClick, modifier = m, enabled = enabled, contentPadding = ButtonPadding, content = label)
        ButtonKind.Outlined -> OutlinedButton(onClick = onClick, modifier = m, enabled = enabled, contentPadding = ButtonPadding, content = label)
        ButtonKind.Text -> TextButton(onClick = onClick, modifier = m, enabled = enabled, contentPadding = ButtonPadding, content = label)
    }
}

/** The text of a row: a switch's title, a control's label, an edge's name. */
@Composable
private fun RowLabel(text: String, modifier: Modifier = Modifier, enabled: Boolean = true) = Text(
    text,
    modifier,
    style = MaterialTheme.typography.bodyLarge,
    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)

/** A label with its control below it, and a hint under both. */
@Composable
private fun LabeledControl(label: String, enabled: Boolean = true, hint: String? = null, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        RowLabel(label, enabled = enabled)
        control()
        if (hint != null) Hint(hint)
    }
}

/** A title (and a hint under it) with a switch on the right. The whole row is one target. */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    /** A small button between the text and the switch, e.g. [ShortcutInfo]. */
    info: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ToggleRowHeight)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            RowLabel(title, enabled = enabled)
            if (subtitle != null) Hint(subtitle)
        }
        // A target of its own inside the row: a tap here does not flip the switch.
        info?.invoke()
        // The row takes the touch, so the switch needs no target of its own.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** The round "i" beside a setting that uses keyboard keys: which keys Android lets
 *  flockswitch see and which not. Shown while the mouse rests on it, on a tap, and on a
 *  long press; it stays until the pointer leaves or the screen is tapped elsewhere.
 *  TalkBack reads the whole text as the button's description. */
@Composable
private fun ShortcutInfo() = InfoIcon(
    stringResource(R.string.shortcut_info_title),
    stringResource(R.string.shortcut_info_text),
    stringResource(R.string.shortcut_info),
)

/** A round "i" that shows [title] and [text] on hover, tap or long press; [label] is what
 *  TalkBack says a tap does. See [ShortcutInfo]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoIcon(title: String, text: String, label: String) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val color = MaterialTheme.colorScheme.primary
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            RichTooltip(title = { Text(title) }) {
                // A small screen (a foldable's cover screen) scrolls it rather than cutting it.
                Text(text, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
            }
        },
        state = state,
    ) {
        Box(
            Modifier
                .size(ControlHeight)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = label) { scope.launch { state.show() } }
                .semantics { contentDescription = "$title. $text" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(InfoIconSize).border(1.5.dp, color, CircleShape), contentAlignment = Alignment.Center) {
                Text("i", color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The circle of [ShortcutInfo]; its touch target is a button's height. */
private val InfoIconSize = 20.dp

/** A dialog with a title, its content (scrolling when long) and a row of equal buttons. */
@Composable
private fun AppDialog(
    title: String?,
    onDismiss: () -> Unit,
    buttons: List<Triple<String, ButtonKind, () -> Unit>>,
    dismissOnClickOutside: Boolean = true,
    buttonsEnabled: List<Boolean> = buttons.map { true },
    content: @Composable ColumnScope.() -> Unit,
) {
    // As wide as a phone allows (the platform's default leaves little room for a row),
    // and no wider than a readable column on a tablet.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = dismissOnClickOutside, usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 560.dp), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleLarge)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ItemGap),
                    content = content,
                )
                Spacer(Modifier.height(DividerPad))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEachIndexed { i, (label, kind, onClick) ->
                        // Equal widths; a label too long for its share wraps rather than cuts.
                        AppButton(label, onClick, Modifier.weight(1f).widthIn(min = 0.dp), kind, enabled = buttonsEnabled[i])
                    }
                }
            }
        }
        }
    }
}

/** A chosen "Off" segment: neutral gray (Apple's systemGray), not the accent colour, so
 *  it reads as switched off rather than as a choice made. */
private val OffGray = Color(0xFF8E8E93)

/** A row of segments, as tall as a button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentRow(modifier: Modifier = Modifier, content: @Composable SingleChoiceSegmentedButtonRowScope.() -> Unit) =
    SingleChoiceSegmentedButtonRow(modifier.height(ControlHeight), content = content)

/** "Off" (0) and channels 1–3 to choose from; this device's own channel is shown but
 *  cannot be chosen. Off replaces an on/off switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelPicker(selected: Int?, here: Int?, showHere: Boolean = true, onPick: (Int) -> Unit) {
    val chosen = selected ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        SegmentRow(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
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
                    contentPadding = SegmentPadding,
                    icon = {},
                    modifier = Modifier.fillMaxHeight(),
                ) { SegmentLabel(if (off) stringResource(R.string.off) else "$c") }
            }
        }
        if (here != null && showHere) Hint(stringResource(R.string.here_channel, here))
    }
}

/** Segments spend their width on the text: no check mark, and little padding. */
private val SegmentPadding = PaddingValues(horizontal = 2.dp)

@Composable
private fun SegmentLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)

/** One of a few fixed values, e.g. the time at the edge. Where the segments would be too
 *  narrow for their text (the folded screen of a foldable, a larger font, a longer
 *  language) it is a drop-down instead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ValuePicker(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, enabled: Boolean = true, onPick: (T) -> Unit) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val widest = with(LocalDensity.current) { options.maxOf { measurer.measure(it.second, style).size.width }.toDp() }
    BoxWithConstraints(modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        if (maxWidth / options.size < widest + 8.dp) {
            ValueMenu(options, selected, enabled = enabled, onPick = onPick)
        } else {
            SegmentRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = selected == value,
                        onClick = { onPick(value) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                        contentPadding = SegmentPadding,
                        icon = {},
                        modifier = Modifier.fillMaxHeight(),
                    ) { SegmentLabel(label) }
                }
            }
        }
    }
}

/** A drop-down button: the chosen value on the left, the arrow on the right. */
@Composable
private fun <T> ValueMenu(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, enabled: Boolean = true, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = ControlHeight),
            contentPadding = ButtonPadding,
        ) {
            Text(
                options.firstOrNull { it.first == selected }?.second ?: selected.toString(),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("▾", style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((value, label) in options) {
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    open = false
                    onPick(value)
                })
            }
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
        SectionDivider()
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
                    AppButton("$c", { actions.switchTo(c) }, Modifier.weight(1f).widthIn(min = 0.dp), enabled = c != s.channel)
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
        // The button is turned off with the "Off" segment of its channel row.
        Section(title) {
            if (missing) Hint(stringResource(R.string.not_on_mouse))
            LabeledControl(stringResource(R.string.channels)) {
                ChannelPicker(if (b.on) shownTarget(b.channels, s.channel) else 0, s.channel) { c ->
                    update { withButton(cid, if (c == 0) b.copy(on = false) else b.copy(on = true, channels = listOf(c))) }
                }
            }
            if (cid == BUTTON_GESTURE) {
                SectionDivider()
                Hint(stringResource(R.string.gesture_hint))
                for (g in listOf(Gesture.Up, Gesture.Down, Gesture.Left, Gesture.Right)) {
                    val m = b.moves.get(g) ?: continue
                    val name = stringResource(
                        when (g) {
                            Gesture.Up -> R.string.gesture_up
                            Gesture.Down -> R.string.gesture_down
                            Gesture.Left -> R.string.gesture_left
                            else -> R.string.gesture_right
                        },
                    )
                    LabeledControl(name) {
                        ChannelPicker(if (m.on) shownTarget(m.channels, s.channel) else 0, s.channel, showHere = false) { c ->
                            val next = if (c == 0) m.copy(on = false) else MoveSetting(true, listOf(c))
                            update { withButton(cid, b.copy(moves = b.moves.with(g, next))) }
                        }
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
        if ((cfg.edges.channel(Edge.Top) != 0 || cfg.edges.channel(Edge.Bottom) != 0) && !s.pointerService) {
            var guide by rememberSaveable { mutableStateOf(false) }
            Notice(stringResource(R.string.edge_bars_service), stringResource(R.string.guide_open)) { guide = true }
            if (guide) AccessibilityGuide(s, actions) { guide = false }
        }
        SectionDivider()
        for (edge in Edge.entries) {
            val name = stringResource(
                when (edge) {
                    Edge.Left -> R.string.edge_left
                    Edge.Right -> R.string.edge_right
                    Edge.Top -> R.string.edge_top
                    Edge.Bottom -> R.string.edge_bottom
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowLabel(name, Modifier.width(64.dp))
                Box(Modifier.weight(1f)) {
                    ChannelPicker(cfg.edges.channel(edge), s.channel, showHere = false) { c -> update { copy(edges = edges.with(edge, c)) } }
                }
            }
        }
        s.channel?.let { Hint(stringResource(R.string.here_channel, it)) }
        SectionDivider()
        val options = listOf(300L to "0.3", 500L to "0.5", 1000L to "1", 2000L to "2")
            .map { (ms, text) -> ms to stringResource(R.string.seconds, text) }
        LabeledControl(stringResource(R.string.edge_dwell), hint = stringResource(R.string.edges_corner_hint)) {
            ValuePicker(options, cfg.edges.dwellMs) { ms -> update { copy(edges = edges.copy(dwellMs = ms)) } }
        }
        SectionDivider()
        SwitchRow(
            stringResource(R.string.edge_require_control),
            cfg.edges.requireControl,
            subtitle = stringResource(R.string.edge_require_control_hint),
            info = { ShortcutInfo() },
        ) { on ->
            update { copy(edges = edges.copy(requireControl = on)) }
        }
        if (cfg.edges.requireControl && !s.pointerService) {
            var guide by rememberSaveable { mutableStateOf(false) }
            Notice(stringResource(R.string.edge_control_service), stringResource(R.string.guide_open)) { guide = true }
            if (guide) AccessibilityGuide(s, actions) { guide = false }
        }
        SectionDivider()
        EdgeAnimRows(cfg, update, actions)
    }
}

/** What is drawn during the wait at an edge. Each effect keeps its own colour, size,
 *  direction and shrinking: the rows below show and change the chosen effect's. Random
 *  has none of its own, so it shows only what it does. */
@Composable
private fun EdgeAnimRows(cfg: Config, update: (Config.() -> Config) -> Unit, actions: Actions) {
    val style = cfg.edges.animStyle
    LabeledControl(stringResource(R.string.edge_anim)) {
        // The effect, with its preview beside it.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ValueMenu(EdgeAnimStyle.entries.zip(stringArrayResource(R.array.edge_anim_names)), style, Modifier.weight(1f)) { chosen ->
                update { copy(edges = edges.withStyle(chosen)) }
            }
            if (style != EdgeAnimStyle.None) AppButton(stringResource(R.string.preview), { actions.previewEdge(cfg.edges) })
        }
    }
    if (style == EdgeAnimStyle.Random) {
        Hint(stringResource(R.string.edge_anim_random_hint))
        return
    }
    if (style !in DRAWN_EFFECTS) return
    val look = cfg.edges.look(style)
    val set: (EffectLook) -> Unit = { l -> update { copy(edges = edges.withLook(style, l)) } }
    if (style.canShrink) {
        SwitchRow(stringResource(R.string.edge_anim_shrink), look.shrink) { on -> set(look.copy(shrink = on)) }
    }
    if (style.hasDirection && !(look.shrink && style.canShrink)) {
        val labels = if (style == EdgeAnimStyle.Bar) {
            listOf(R.string.bar_forward, R.string.bar_reverse, R.string.bar_both)
        } else {
            listOf(R.string.dir_clockwise, R.string.dir_counter, R.string.dir_both)
        }
        LabeledControl(stringResource(R.string.edge_anim_direction)) {
            ValuePicker(EdgeAnimDirection.entries.zip(labels.map { stringResource(it) }), look.direction) { d -> set(look.copy(direction = d)) }
        }
    }
    EffectColorRow(look.color) { color -> set(look.copy(color = color)) }
    EffectSizeRow(look.size) { size -> set(look.copy(size = size)) }
    Hint(stringResource(R.string.edge_anim_own_hint))
}

/** How large an effect is drawn. */
@Composable
private fun EffectSizeRow(size: Int, onPick: (Int) -> Unit) {
    LabeledControl(stringResource(R.string.effect_size)) {
        ValuePicker(EFFECT_SIZES.map { it to "$it%" }, size, onPick = onPick)
    }
}

/** One colour to click on. */
@Composable
private fun ColorSwatch(rgb: Int, selected: Boolean, description: String, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(SwatchSize)
            .background(Color(0xFF000000 or rgb.toLong()), CircleShape)
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
    )
}

/** The ready-made colours, and one more swatch that opens a picker for any other. */
@Composable
private fun EffectColorRow(color: String, onPick: (String) -> Unit) {
    val current = parseColor(color) ?: COLOR_PRESETS.first()
    var picking by rememberSaveable { mutableStateOf(false) }
    LabeledControl(stringResource(R.string.effect_color)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (preset in COLOR_PRESETS) {
                ColorSwatch(preset, preset == current, formatColor(preset)) { onPick(formatColor(preset)) }
            }
            // Any other colour: the same swatch, gray with a "+" until one is chosen.
            val other = current !in COLOR_PRESETS
            Box(Modifier.size(SwatchSize), contentAlignment = Alignment.Center) {
                ColorSwatch(if (other) current else OffGray.toArgb() and 0xFFFFFF, other, stringResource(R.string.color_other)) { picking = true }
                if (!other) Text("+", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (picking) {
        ColorPickerDialog(current, onClose = { picking = false }) { rgb ->
            picking = false
            onPick(formatColor(rgb))
        }
    }
}

/** Any colour: red, green and blue sliders, or the "#RRGGBB" typed in. */
@Composable
private fun ColorPickerDialog(initial: Int, onClose: () -> Unit, onChoose: (Int) -> Unit) {
    var rgb by rememberSaveable { mutableIntStateOf(initial) }
    var hex by rememberSaveable { mutableStateOf(formatColor(initial)) }
    fun typed(text: String): Int? = parseColor(if (text.trim().startsWith("#")) text else "#" + text.trim())
    val valid = typed(hex) != null
    AppDialog(
        stringResource(R.string.effect_color),
        onClose,
        listOf(
            Triple(stringResource(R.string.close), ButtonKind.Outlined, onClose),
            Triple(stringResource(R.string.ok), ButtonKind.Filled) { onChoose(rgb) },
        ),
        buttonsEnabled = listOf(true, valid),
    ) {
        Box(Modifier.fillMaxWidth().height(ControlHeight).background(Color(0xFF000000 or rgb.toLong()), MaterialTheme.shapes.small))
        Column {
            for ((label, shift) in listOf("R" to 16, "G" to 8, "B" to 0)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RowLabel(label, Modifier.width(24.dp))
                    Slider(
                        value = ((rgb shr shift) and 0xFF).toFloat(),
                        onValueChange = { v ->
                            rgb = (rgb and (0xFF shl shift).inv()) or (v.toInt().coerceIn(0, 255) shl shift)
                            hex = formatColor(rgb)
                        },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        OutlinedTextField(
            value = hex,
            onValueChange = { text ->
                hex = text
                typed(text)?.let { rgb = it }
            },
            label = { Text("#RRGGBB") },
            singleLine = true,
            isError = !valid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Highlighting the pointer: on/off, the effect, and what brings it up. */
@Composable
private fun LocateSection(cfg: Config, s: Status, update: (Config.() -> Config) -> Unit, actions: Actions) {
    val l = cfg.locate
    Section(stringResource(R.string.section_locate)) {
        SwitchRow(stringResource(R.string.locate_on), l.on) { on -> update { copy(locate = locate.copy(on = on)) } }
        if (l.on) {
            SectionDivider()
            val styles = listOf(
                LocateStyle.Ring to stringResource(R.string.locate_ring),
                LocateStyle.Radar to stringResource(R.string.locate_radar),
                LocateStyle.Spotlight to stringResource(R.string.locate_spotlight),
                LocateStyle.Random to stringResource(R.string.locate_random),
            )
            LabeledControl(stringResource(R.string.locate_style)) {
                // The effect, with its preview beside it. The pointer is on the button when
                // it is pressed: the preview plays there.
                var at by remember { mutableStateOf(Offset.Zero) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ValueMenu(styles, l.style, Modifier.weight(1f)) { style -> update { copy(locate = locate.copy(style = style)) } }
                    AppButton(
                        stringResource(R.string.preview),
                        { actions.previewLocate(l, at.x, at.y) },
                        Modifier.onGloballyPositioned { at = it.boundsInWindow().center },
                    )
                }
            }
            EffectColorRow(l.color) { color -> update { copy(locate = locate.copy(color = color)) } }
            EffectSizeRow(l.size) { size -> update { copy(locate = locate.copy(size = size)) } }
            SectionDivider()
            if (!pointerReadable) {
                Hint(stringResource(R.string.locate_needs_android14))
            } else {
                if (!s.pointerService) {
                    var guide by rememberSaveable { mutableStateOf(false) }
                    Notice(stringResource(R.string.locate_service_needed), stringResource(R.string.guide_open)) { guide = true }
                    if (guide) AccessibilityGuide(s, actions) { guide = false }
                }
                SwitchRow(stringResource(R.string.locate_use_hotkey), l.onHotkey, info = { ShortcutInfo() }) { on ->
                    update { copy(locate = locate.copy(onHotkey = on)) }
                }
                SwitchRow(stringResource(R.string.locate_arrival), l.onArrival) { on -> update { copy(locate = locate.copy(onArrival = on)) } }
                Hint(stringResource(R.string.locate_hint))
            }
        }
    }
}

private val hapticsSaver = Saver<Haptics, String>(
    save = { Config.json.encodeToString(Haptics.serializer(), it) },
    restore = { Config.json.decodeFromString(Haptics.serializer(), it) },
)

/** The haptics settings. Nothing reaches the mouse before Apply or OK; Close puts back
 *  what was set when the dialog opened, undoing an Apply too. Feel plays a choice on the
 *  mouse at once, at the strength chosen here, and leaves the mouse's own strength be. */
@Composable
private fun HapticsDialog(cfg: Config, update: (Config.() -> Config) -> Unit, actions: Actions, onClose: () -> Unit) {
    val openedWith = rememberSaveable(saver = hapticsSaver) { cfg.haptics }
    var d by rememberSaveable(stateSaver = hapticsSaver) { mutableStateOf(cfg.haptics) }
    val close = {
        if (cfg.haptics != openedWith) update { copy(haptics = openedWith) }
        onClose()
    }
    val feel = { what: HapticPreview -> actions.feel(what, d.intensity) }
    AppDialog(
        stringResource(R.string.haptics_dialog_title),
        close,
        // As on the desktop: Apply, OK, Close.
        listOf(
            Triple(stringResource(R.string.apply), ButtonKind.Outlined) { update { copy(haptics = d) } },
            Triple(stringResource(R.string.ok), ButtonKind.Filled) {
                update { copy(haptics = d) }
                onClose()
            },
            Triple(stringResource(R.string.close), ButtonKind.Outlined, close),
        ),
        dismissOnClickOutside = false,
    ) {
        val on = d.anyOn()
        SwitchRow(stringResource(R.string.haptics_master), on) { d = d.copy(onSwitch = it, onArrive = it, onEdge = it) }
        // 0 = as Logi Options+ sets it; otherwise the strength in percent.
        val levels = listOf(0 to stringResource(R.string.haptics_follow), 25 to "25%", 50 to "50%", 75 to "75%", 100 to "100%")
        LabeledControl(stringResource(R.string.haptics_intensity), enabled = on) {
            ValuePicker(levels, d.intensity, enabled = on) { d = d.copy(intensity = it) }
        }
        // The situations, one line each, set apart from the two settings above.
        Spacer(Modifier.height(ItemGap))
        SectionDivider()
        val pulses = Waveform.ALL.zip(stringArrayResource(R.array.waveform_names)) + (Waveform.RANDOM to stringResource(R.string.locate_random))
        val leave = stringResource(R.string.haptics_leave_short)
        val arrive = stringResource(R.string.haptics_arrive_short)
        val edge = stringResource(R.string.haptics_edge_short)
        // The names share the width of the longest, so they never wrap and the controls line up.
        val measurer = rememberTextMeasurer()
        val style = MaterialTheme.typography.bodyLarge
        val labelWidth = with(LocalDensity.current) { listOf(leave, arrive, edge).maxOf { measurer.measure(it, style).size.width }.toDp() } + 2.dp
        SituationRow(leave, labelWidth, d.onSwitch, on, { d = d.copy(onSwitch = it) }, { feel(HapticPreview.Pulse(d.switchWaveform)) }) { active ->
            ValueMenu(pulses, d.switchWaveform, enabled = active) { d = d.copy(switchWaveform = it) }
        }
        SituationRow(arrive, labelWidth, d.onArrive, on, { d = d.copy(onArrive = it) }, { feel(HapticPreview.Pulse(d.arriveWaveform)) }) { active ->
            ValueMenu(pulses, d.arriveWaveform, enabled = active) { d = d.copy(arriveWaveform = it) }
        }
        val modes = listOf(true to stringResource(R.string.haptics_rising), false to stringResource(R.string.haptics_even))
        SituationRow(edge, labelWidth, d.onEdge, on, { d = d.copy(onEdge = it) }, { feel(HapticPreview.Edge(d.progressive)) }) { active ->
            ValueMenu(modes, d.progressive, enabled = active) { d = d.copy(progressive = it) }
        }
        SectionDivider()
        Spacer(Modifier.height(ItemGap / 2))
        Hint(stringResource(R.string.haptics_hint))
    }
}

/** A situation of the haptics dialog on one line: its name, the control that shapes its
 *  pulse, a button to feel it, and its switch. */
@Composable
private fun SituationRow(
    title: String,
    labelWidth: Dp,
    checked: Boolean,
    enabled: Boolean,
    onCheck: (Boolean) -> Unit,
    onFeel: () -> Unit,
    control: @Composable (active: Boolean) -> Unit,
) {
    val active = enabled && checked
    val feel = stringResource(R.string.haptics_feel)
    Row(
        Modifier.fillMaxWidth().heightIn(min = SituationRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RowLabel(title, Modifier.width(labelWidth), enabled = enabled)
        Box(Modifier.weight(1f)) { control(active) }
        OutlinedButton(
            onClick = onFeel,
            enabled = active,
            modifier = Modifier.size(ControlHeight).semantics { contentDescription = feel },
            contentPadding = PaddingValues(0.dp),
        ) { Text("▶") }
        Switch(checked = checked, onCheckedChange = onCheck, enabled = enabled)
    }
}

/** A situation's line in the haptics dialog: a little taller than a card's rows. */
private val SituationRowHeight = 52.dp

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
            AppButton(stringResource(R.string.forget_devices), { shared.forgetDevices() })
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, checked: Boolean, radio: Boolean, onClick: () -> Unit) {
    // Like a switch row: the whole row is one target.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ToggleRowHeight)
            .then(
                if (radio) Modifier.selectable(selected = checked, role = Role.RadioButton, onClick = onClick)
                else Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() }),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (radio) RadioButton(selected = checked, onClick = null) else Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            RowLabel(title)
            if (subtitle != null) Hint(subtitle)
        }
    }
}

/** Why the accessibility service is needed and how to turn it on, one step at a time.
 *  Its state follows the system, so a step shows as done on coming back. */
@Composable
private fun AccessibilityGuide(s: Status, actions: Actions, onClose: () -> Unit) {
    // Google Play: what the service accesses, and an explicit Agree, come before any step
    // that leads to Android's settings. No thanks (or going back) closes it; it opens again
    // from the same buttons. The GitHub build goes straight to the steps, as before.
    var agreed by rememberSaveable { mutableStateOf(!BuildConfig.PLAY_STORE || s.pointerService) }
    if (!agreed) {
        AppDialog(
            stringResource(R.string.a11y_disclosure_title),
            onClose,
            listOf(
                Triple(stringResource(R.string.a11y_decline), ButtonKind.Outlined, onClose),
                Triple(stringResource(R.string.a11y_agree), ButtonKind.Filled) { agreed = true },
            ),
            dismissOnClickOutside = false,
        ) { Text(stringResource(R.string.a11y_disclosure_text)) }
        return
    }
    AppDialog(stringResource(R.string.guide_title), onClose, listOf(Triple(stringResource(R.string.close), ButtonKind.Outlined, onClose))) {
        Text(stringResource(R.string.guide_why))
        Hint(stringResource(R.string.guide_privacy))
        SectionDivider()
        Text(stringResource(R.string.guide_step1))
        AppButton(stringResource(R.string.open_accessibility), actions.openAccessibility, kind = ButtonKind.Filled)
        // Android restricts this setting only for apps installed outside an app store.
        if (BuildConfig.PLAY_STORE) {
            Text(stringResource(R.string.guide_step2_play))
        } else {
            Text(stringResource(R.string.guide_step2))
            AppButton(stringResource(R.string.guide_app_info), actions.openAppInfo)
            Text(stringResource(R.string.guide_step3))
        }
        SectionDivider()
        Text(
            stringResource(if (s.pointerService) R.string.guide_done else R.string.guide_not_yet),
            fontWeight = FontWeight.SemiBold,
            color = if (s.pointerService) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

/** One system permission: on, or a button that leads to it. */
@Composable
private fun PermissionRow(title: String, hint: String, on: Boolean, onSet: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            RowLabel(title)
            Hint(hint)
        }
        if (on) {
            // As wide as the button would be, so both rows line up.
            Box(Modifier.widthIn(min = ButtonMinWidth).heightIn(min = ControlHeight), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.perm_on), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            AppButton(stringResource(R.string.perm_set), onSet, kind = ButtonKind.Filled)
        }
    }
}

/** What the system has to allow, in one place. */
@Composable
private fun PermissionsSection(s: Status, actions: Actions) {
    // Changed in the system's own screens: read again on coming back.
    var overlay by remember { mutableStateOf(actions.overlayOn()) }
    LifecycleResumeEffect(Unit) {
        overlay = actions.overlayOn()
        onPauseOrDispose {}
    }
    var guide by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(R.string.section_permissions)) {
        PermissionRow(stringResource(R.string.perm_overlay), stringResource(R.string.perm_overlay_hint), overlay, actions.allowOverlay)
        SectionDivider()
        PermissionRow(stringResource(R.string.perm_accessibility), stringResource(R.string.perm_accessibility_hint), s.pointerService) { guide = true }
    }
    if (guide) AccessibilityGuide(s, actions) { guide = false }
}

/** The version, and looking for (and installing) a new one from GitHub. */
@Composable
private fun UpdateSection(cfg: Config, updates: StateFlow<UpdateState>, update: (Config.() -> Config) -> Unit, actions: Actions) {
    val state by updates.collectAsStateWithLifecycle()
    Section(stringResource(R.string.section_update)) {
        RowLabel(stringResource(R.string.version, BuildConfig.VERSION_NAME))
        SwitchRow(stringResource(R.string.update_auto), cfg.checkUpdates) { on -> update { copy(checkUpdates = on) } }
        SectionDivider()
        // Installing on its own needs the checks made on its own.
        SwitchRow(
            stringResource(R.string.update_install_auto),
            cfg.autoUpdate,
            subtitle = stringResource(R.string.update_install_auto_hint),
            enabled = cfg.checkUpdates,
        ) { on -> update { copy(autoUpdate = on) } }
        SectionDivider()
        val text = when (val u = state) {
            UpdateState.Unknown -> null
            UpdateState.Checking -> stringResource(R.string.update_checking)
            UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
            UpdateState.Failed -> stringResource(R.string.update_failed)
            is UpdateState.Available -> stringResource(R.string.update_available, u.version)
            is UpdateState.Downloading ->
                if (u.percent == null) stringResource(R.string.update_downloading) else stringResource(R.string.update_downloading_percent, u.percent)
            is UpdateState.Installing -> stringResource(R.string.update_installing)
            is UpdateState.Confirming -> stringResource(R.string.update_confirming, u.version)
            is UpdateState.InstallFailed -> stringResource(
                when (u.why) {
                    UpdateFailure.Download -> R.string.update_failed_download
                    UpdateFailure.Checksum -> R.string.update_failed_checksum
                    UpdateFailure.Install -> R.string.update_failed_install
                    UpdateFailure.Cancelled -> R.string.update_cancelled
                },
                u.version,
            )
        }
        text?.let { Hint(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(stringResource(R.string.update_check), actions.checkUpdate, enabled = !state.busy())
            // A newer version is known: install it (again, after a failure or a "no").
            if (state is UpdateState.Available || state is UpdateState.Confirming || state is UpdateState.InstallFailed) {
                AppButton(stringResource(R.string.update_now), actions.updateNow, kind = ButtonKind.Filled)
            }
            // When installing here does not work, the APK can still be fetched by hand.
            if (state is UpdateState.InstallFailed && (state as UpdateState.InstallFailed).why != UpdateFailure.Cancelled) {
                AppButton(stringResource(R.string.update_page), { actions.open(RELEASES_PAGE) })
            }
        }
    }
}

/** Writing to the developer (the mail carries the recent log lines). */
@Composable
private fun ReportSection(actions: Actions) {
    Section(stringResource(R.string.section_report)) {
        Hint(stringResource(R.string.report_desc))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(stringResource(R.string.report_mail), actions.report, kind = ButtonKind.Filled)
            AppButton(stringResource(R.string.report_issue), { actions.open(MainActivity.ISSUES_PAGE) })
        }
    }
}

/** A system's name as a settings file writes it, for the screen. */
private fun osName(os: String) = when (os.lowercase()) {
    "macos", "mac", "darwin" -> "macOS"
    "windows" -> "Windows"
    "linux" -> "Linux"
    else -> os
}

/** Saving the settings to a file and loading them from one, to move a setup to another
 *  device; loading asks first, and a file that is not usable is refused with the reason.
 *  The files saved from here (at most [SettingsFile.MAX_SAVED]) can be renamed and
 *  deleted under Manage. */
@Composable
private fun BackupSection(backup: StateFlow<BackupState>, saved: StateFlow<List<SavedFile>>, setBackup: (BackupState) -> Unit, actions: Actions) {
    val state by backup.collectAsStateWithLifecycle()
    val files by saved.collectAsStateWithLifecycle()
    var manage by rememberSaveable { mutableStateOf(false) }
    val info: @Composable () -> Unit = {
        InfoIcon(
            stringResource(R.string.backup_info_title),
            stringResource(R.string.backup_info_text, SettingsFile.MAX_SAVED),
            stringResource(R.string.backup_info),
        )
    }
    Section(stringResource(R.string.section_backup), info = info) {
        Hint(stringResource(R.string.backup_hint))
        // Four short buttons: two to a row on a narrow (folded) screen, all four when wide.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(stringResource(R.string.backup_save), actions.saveSettings, kind = ButtonKind.Filled)
            AppButton(stringResource(R.string.backup_load), actions.loadSettings)
            AppButton(stringResource(R.string.backup_manage), {
                actions.refreshSaved()
                manage = true
            })
            AppButton(stringResource(R.string.backup_folder), actions.openSettingsFolder)
        }
        Hint(stringResource(R.string.backup_count, files.size, SettingsFile.MAX_SAVED))
        when (val b = state) {
            is BackupState.Saved -> Hint(stringResource(R.string.backup_saved, b.name))
            BackupState.SaveFailed -> Text(
                stringResource(R.string.backup_save_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            is BackupState.Loaded -> Hint(stringResource(R.string.backup_loaded, b.name))
            else -> {}
        }
    }
    val dismiss = { setBackup(BackupState.Idle) }
    when (val b = state) {
        is BackupState.Confirm -> AppDialog(
            stringResource(R.string.backup_confirm_title),
            dismiss,
            listOf(
                Triple(stringResource(R.string.cancel), ButtonKind.Outlined, dismiss),
                Triple(stringResource(R.string.backup_confirm_load), ButtonKind.Filled) { actions.applySettings(b.config, b.name) },
            ),
        ) {
            Text(stringResource(R.string.backup_confirm_text, b.name))
            val from = b.from
            Hint(stringResource(R.string.backup_confirm_from, from.device.ifBlank { "?" }, from.version.ifBlank { "?" }, from.date.replace('T', ' ')))
            Hint(stringResource(R.string.backup_channels))
        }
        is BackupState.Rejected -> AppDialog(
            stringResource(R.string.backup_rejected_title),
            dismiss,
            listOf(Triple(stringResource(R.string.close), ButtonKind.Outlined, dismiss)),
        ) {
            Text(
                when (b.why) {
                    null -> stringResource(R.string.backup_rejected_read)
                    ImportRejection.NotJson -> stringResource(R.string.backup_rejected_json)
                    ImportRejection.NotSettings -> stringResource(R.string.backup_rejected_settings)
                    ImportRejection.OtherOs -> stringResource(R.string.backup_rejected_os, osName(b.detail ?: "?"))
                    ImportRejection.Unusable -> stringResource(R.string.backup_rejected_unusable)
                    ImportRejection.TooLarge -> stringResource(R.string.backup_rejected_large)
                },
            )
            // What exactly is wrong, as the settings' own checks put it.
            if (b.why == ImportRejection.Unusable && b.detail != null) Hint(b.detail)
        }
        BackupState.Full -> AppDialog(
            null,
            dismiss,
            listOf(
                Triple(stringResource(R.string.close), ButtonKind.Outlined, dismiss),
                Triple(stringResource(R.string.backup_manage), ButtonKind.Filled) {
                    dismiss()
                    manage = true
                },
            ),
        ) { Text(stringResource(R.string.backup_full, SettingsFile.MAX_SAVED)) }
        else -> {}
    }
    if (manage) ManageSavedDialog(files, actions) { manage = false }
}

/** The settings files saved from here, newest first, each to rename or delete. */
@Composable
private fun ManageSavedDialog(files: List<SavedFile>, actions: Actions, onClose: () -> Unit) {
    // Kept by URI: the list can change under the dialog (a file found gone, a rename).
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var typed by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var cannotDelete by rememberSaveable { mutableStateOf<String?>(null) }
    // A line under one file: what happened when renaming or deleting it.
    var noteFor by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf<ManageOutcome?>(null) }
    var gone by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val dates = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
    AppDialog(
        stringResource(R.string.backup_manage_title),
        onClose,
        listOf(Triple(stringResource(R.string.close), ButtonKind.Outlined, onClose)),
    ) {
        Hint(stringResource(R.string.backup_count, files.size, SettingsFile.MAX_SAVED))
        gone?.let { Hint(stringResource(R.string.backup_gone, it)) }
        if (files.isEmpty()) Hint(stringResource(R.string.backup_none))
        files.forEachIndexed { i, f ->
            if (i > 0) SectionDivider()
            Column(verticalArrangement = Arrangement.spacedBy(LabelGap)) {
                Text(
                    SettingsFile.shownName(f.name),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Hint(dates.format(java.util.Date(f.savedAt)))
                if (renaming == f.uri) {
                    val others = files.filter { it.uri != f.uri }.map { it.name }
                    val problem = SettingsFile.renameProblem(typed, others)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = {
                            typed = it
                            if (noteFor == f.uri) note = null
                        },
                        label = { Text(stringResource(R.string.backup_new_name)) },
                        suffix = { Text(".json") },
                        singleLine = true,
                        isError = problem != null,
                        supportingText = problem?.let { p -> { Text(renameProblemText(p)) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(stringResource(R.string.ok), {
                            busy = true
                            actions.renameSaved(f, typed) { outcome ->
                                busy = false
                                when (outcome) {
                                    ManageOutcome.Done -> renaming = null
                                    ManageOutcome.Gone -> {
                                        renaming = null
                                        gone = SettingsFile.shownName(f.name)
                                    }
                                    ManageOutcome.Failed -> {
                                        noteFor = f.uri
                                        note = outcome
                                    }
                                }
                            }
                        }, kind = ButtonKind.Filled, enabled = problem == null && !busy)
                        AppButton(stringResource(R.string.cancel), {
                            renaming = null
                            note = null
                        })
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(stringResource(R.string.backup_rename), {
                            renaming = f.uri
                            typed = SettingsFile.shownName(f.name)
                            note = null
                        }, enabled = !busy)
                        AppButton(stringResource(R.string.backup_delete), { deleting = f.uri }, enabled = !busy)
                    }
                }
                if (noteFor == f.uri && note == ManageOutcome.Failed) {
                    Text(
                        stringResource(R.string.backup_rename_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    files.firstOrNull { it.uri == deleting }?.let { f ->
        val cancel = { deleting = null }
        AppDialog(
            stringResource(R.string.backup_delete_title),
            cancel,
            listOf(
                Triple(stringResource(R.string.cancel), ButtonKind.Outlined, cancel),
                Triple(stringResource(R.string.backup_delete), ButtonKind.Filled) {
                    deleting = null
                    busy = true
                    actions.deleteSaved(f) { outcome ->
                        busy = false
                        when (outcome) {
                            ManageOutcome.Done -> {}
                            ManageOutcome.Gone -> gone = SettingsFile.shownName(f.name)
                            ManageOutcome.Failed -> cannotDelete = f.uri
                        }
                    }
                },
            ),
        ) { Text(stringResource(R.string.backup_delete_text, SettingsFile.shownName(f.name))) }
    }
    files.firstOrNull { it.uri == cannotDelete }?.let { f ->
        val close = { cannotDelete = null }
        AppDialog(
            stringResource(R.string.backup_delete_failed_title),
            close,
            listOf(
                Triple(stringResource(R.string.close), ButtonKind.Outlined, close),
                Triple(stringResource(R.string.backup_forget), ButtonKind.Filled) {
                    cannotDelete = null
                    actions.forgetSaved(f)
                },
            ),
        ) { Text(stringResource(R.string.backup_delete_failed, SettingsFile.shownName(f.name))) }
    }
}

@Composable
private fun renameProblemText(p: RenameProblem): String = when (p) {
    RenameProblem.Empty -> stringResource(R.string.backup_name_empty)
    RenameProblem.TooLong -> stringResource(R.string.backup_name_long, SettingsFile.MAX_NAME)
    RenameProblem.BadCharacter -> stringResource(R.string.backup_name_chars, SettingsFile.FORBIDDEN.toList().joinToString(" "))
    RenameProblem.Taken -> stringResource(R.string.backup_name_taken)
}

@Composable
private fun GeneralTab(
    cfg: Config,
    s: Status,
    updates: StateFlow<UpdateState>,
    backup: StateFlow<BackupState>,
    saved: StateFlow<List<SavedFile>>,
    setBackup: (BackupState) -> Unit,
    update: (Config.() -> Config) -> Unit,
    actions: Actions,
) {
    PermissionsSection(s, actions)

    Section {
        SwitchRow(stringResource(R.string.start_at_boot), cfg.startAtBoot) { on -> update { copy(startAtBoot = on) } }
        SectionDivider()
        SwitchRow(stringResource(R.string.bluetooth_title), cfg.bluetooth, subtitle = stringResource(R.string.bluetooth_hint)) { on ->
            update { copy(bluetooth = on) }
            if (on) actions.allowNearby()
        }
    }

    LocateSection(cfg, s, update, actions)

    var hapticsOpen by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(R.string.section_haptics)) {
        val state = stringResource(if (cfg.haptics.anyOn()) R.string.haptics_state_on else R.string.haptics_state_off)
        val strength = if (cfg.haptics.intensity == 0) stringResource(R.string.haptics_follow) else "${cfg.haptics.intensity}%"
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RowLabel("$state · ${stringResource(R.string.haptics_intensity)} $strength", Modifier.weight(1f))
            AppButton(stringResource(R.string.haptics_open), { hapticsOpen = true })
        }
    }
    if (hapticsOpen) HapticsDialog(cfg, update, actions) { hapticsOpen = false }

    // Google Play keeps the app up to date itself, and its listing has the contact details.
    if (!BuildConfig.PLAY_STORE) {
        UpdateSection(cfg, updates, update, actions)
        ReportSection(actions)
    }

    BackupSection(backup, saved, setBackup, actions)

    var confirmReset by rememberSaveable { mutableStateOf(false) }
    Section {
        Hint(stringResource(R.string.reset_explain))
        AppButton(stringResource(R.string.reset_button), { confirmReset = true })
    }
    if (confirmReset) {
        val cancel = { confirmReset = false }
        AppDialog(
            null,
            cancel,
            listOf(
                Triple(stringResource(R.string.cancel), ButtonKind.Outlined, cancel),
                Triple(stringResource(R.string.reset_confirm), ButtonKind.Filled) {
                    confirmReset = false
                    update { Config() }
                    actions.setEnabled(true)
                },
            ),
        ) { Text(stringResource(R.string.reset_explain)) }
    }
    Spacer(Modifier.height(8.dp))
}
