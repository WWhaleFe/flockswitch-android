# Changelog

## 0.7.0 — 2026-11-10

- Automatic updates (GitHub version): when a new version is found it is downloaded, checked against the release's `SHA256SUMS.txt` (never installed if it does not match) and installed. On Android 12 and later it goes in without a question where Android lets an app update itself; otherwise Android's install prompt opens, or, while the app is in the background, a notification says "New version ready to install — tap to install". Settings → General → Updates: **Install updates automatically** (on by default) beside **Check for a new version**, and **Update now** once a new version is available, with downloading, installing and failure shown. The check now also runs in the background service, as it starts and once a day. The Google Play build has none of this and does not ask for the install permission
- After an update the background service starts again by itself
- The haptics window's Feel button is now called **Test**

## 0.6.0 — 2026-11-10

- Haptics window: each situation on one line (name, pulse, Feel, switch), and 'use haptics / strength' set apart from the situations with more room between rows
- Haptics settings tidied: the pulse as the mouse leaves and the one when it is back are switched on and off on their own, each with its pulse beside it. Waiting at an edge offers **Rising / Even**; the edge waveform and the "stronger as the switch nears" switch are folded into that choice. Older settings keep what they had: the arrival pulse follows the old switching pulse
- **Feel**: the haptics dialog plays the chosen pulse on the mouse at the chosen strength (for an edge, a second of the rising or even pulses), leaving the mouse's own strength as it was. Quiet when no mouse with a motor is here
- The settings say plainly that only a Logitech mouse with a haptic motor (currently the MX Master 4) vibrates
- Random pulse: choose **Random** for the leaving or arriving pulse and each one is a different pulse the mouse offers
- Effect size: the edge countdown and the find-the-cursor highlight each have a size, 75 to 150 %
- A more consistent settings screen: buttons, drop-downs and segment rows share one height, buttons a minimum width, row labels one style, dividers the same space above and below, dialog buttons equal widths, and the colour swatches one size

## 0.5.4 — 2026-11-10

- Foldables: the screen-edge strips follow folding and unfolding, and choice segments become a drop-down where their text would not fit
- Fixed switching at the top and bottom screen edges, where the status and navigation bars took the pointer. These two edges need the accessibility service
- Choice segments such as the haptic strength no longer break their text
- Foldables: on an open foldable or a phone on its side the tabs sit beside the page, and on a cover screen in one thin row
- Step-by-step directions for turning on the accessibility service, and the permissions at the top of the General tab
- Settings come back after a reinstall through Google backup (when that is on)
- The Bluetooth devices setting is no longer marked experimental

## 0.5.0 — 2026-11-09

- Effect colours: the screen-edge countdown and the find-the-cursor highlight each have their own colour (seven ready-made, any other from the picker with sliders or a typed #RRGGBB)
- Random effects: choose **Random** for the edge countdown or the highlight and each one shows a different effect
- Settings: the four screen edges take less room, each side on one line

## 0.4.0 — 2026-11-09

- Haptics settings moved into a dialog (Settings → General → Haptics → **Haptics Settings…**): haptics on or off, each situation on or off, strength, and the waveform for switching, arrival and the wait at an edge (the MX Master 4's 16 waveforms). **Apply** saves and sends to the mouse and stays open, **OK** saves and closes, **Close** puts back what was set when the dialog opened, even after Apply. A waveform just chosen plays once so the hand can judge it
- Stronger as the switch nears (on by default): while the pointer waits at a screen edge the pulses get quicker and sharper, with one more as it switches. Turn it off for the even pulses of before
- A countdown near the pointer while it waits at a screen edge, in #FFCC00: ring, semicircle, bar, pulse wave, neon laser, particle vortex, cyber scan or liquid (Buttons tab → **Switch at the screen edge** → **Countdown effect**, with direction, shrink and a preview), or none. It is gone the moment the wait is called off or the switch happens, and takes no input. On Android 12 and later it is drawn at 80 % opacity, as the system requires of a window clicks must pass through
- *Only while Control is held* for the edges: the wait does not start until Control is down. With flockswitch's accessibility service on (see below) letting go calls it off at once; without it Android reports the key only as the pointer moves, so a key pressed or released while the pointer stands still counts at the next movement
- Find the cursor (General tab → **Find the cursor**): a pulse ring, radar or spotlight around the pointer in #FFCC00, for 1.3 s with a fade, when the mouse arrives from another computer, on Ctrl+Alt+L, or from a button in the notification, with a preview in the settings. Needs Android 14 and flockswitch's accessibility service, the only way Android tells an app where the pointer is. Listening holds the mouse back from other apps, so flockswitch listens for one event only: the highlight appears at the next mouse movement, and stays where the pointer was then. Shaking the mouse is not a trigger on Android, since that would need the mouse's movement all the time
- Settings: **Show log** and **Reset settings…** on the General tab
## 0.3.0 — 2026-11-08

- Haptics on a mouse with a motor (MX Master 4, HID++ feature 0x19B0): a pulse as the mouse is switched away and one when it comes back here, and short pulses while the pointer waits at a screen edge to switch (Settings → General → **Haptics**; both can be turned off). The strength follows the haptics setting in Logi Options+. A mouse without a motor is simply quiet
- Haptics: the strength can be chosen (Settings → General → Haptics: Options+'s own setting, or 25/50/75/100 %). A chosen strength goes to the mouse at once, with a pulse to judge it by, and is applied again whenever the mouse is here, since the mouse forgets it when it loses power

## 0.2.0 — 2026-11-08

- Switching at the screen edge, as Logi Flow does but without a network: rest the mouse pointer against an edge of the screen for a moment and the group and the master mouse go to the channel set for that side (Buttons tab → **Switch at the screen edge**: each side's channel and the time at the edge, 0.3 to 2 s). Each device watches only its own screen. Needs *Display over other apps*: thin invisible strips along the edges notice the pointer. Left and right work best, since the status bar and the navigation bar sit on the top and bottom edges
- Settings: the channel rows of the buttons, the gesture directions and the screen edges now start with a gray **Off** segment, which replaces the on/off switch above each of them. Gray, not the accent colour, so a chosen Off reads as switched off

## 0.1.0 — 2026-11-07

- First Android version, with the same switching as flockswitch for macOS 0.4.3: buttons, gestures, devices that move along, and a channel per button (this device's own channel cannot be chosen)
- Logi Bolt and Unifying receivers on USB: only the receiver's HID++ part is taken, so typing and pointing keep working. A receiver that Android's own driver uses is handed back at once
- Logitech devices on Bluetooth (experimental, off by default). A device that is connected but could not be opened, as can happen right after the mouse comes back while Android is still bringing its own link up, is tried again after a pause (5 seconds, growing to a minute); a device whose service discovery failed is tried again too
- Runs in the background with a notification showing the master mouse and this device's channel, with a button per channel; a Quick Settings tile switches like the wheel mode-shift button, in place of keyboard shortcuts
- Starts when the device starts, checks GitHub for a new version, and a report to the developer from the General tab
