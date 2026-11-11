# Changelog

## 0.10.0 — 2026-11-12

- Slightly less memory in the background: the windows for the edge countdown and for find-the-cursor are set up the first time they are needed instead of when the service starts, and the recent log kept in memory for the mail to the developer holds only the 60 lines the mail carries (the Google Play version, which has no such mail, keeps none)
- A Bluetooth connection that fails half way (for example when the permission is taken back or Bluetooth is turned off meanwhile) is always closed, so it no longer holds on to one of Android's few Bluetooth connection slots
- A receiver slot whose device is asleep or away is no longer written to Android's system log every 30 seconds
- Tidied up: unused texts and code removed, and the Google Play version no longer contains the GitHub version's update check. No change to features, settings or permissions

## 0.9.0 — 2026-11-10

- Each edge countdown effect (ring, semicircle, bar, pulse wave, neon laser, particle vortex, cyber scan, liquid) keeps its own colour and size, and the ring, semicircle and bar their own direction (and the ring and semicircle their own *Shrink away*): choosing *Both ways* for the ring no longer changes the bar. The settings show and change the chosen effect's own values, and the preview plays it with them. Saved in config.json under `edges.effects`; settings from earlier versions (and Google backups of them) give every effect the look they had
- **Random** now varies more: each countdown is a random effect in that effect's own colour and size, with a random direction for the ring, semicircle and bar and a random shrink-away for the ring and semicircle. A chosen effect keeps its saved direction and shrinking
- Settings backup: shorter buttons — **Save**, **Load**, **Manage**, **Open folder** — and the number of saved files ("3/10"). flockswitch keeps a list of the files it saved (it keeps access to each) and drops those deleted elsewhere. At most 10: with 10 saved, **Save** asks to tidy up in **Manage** first. **Manage** lists them newest first, each with **Rename** (the name is checked: not empty, at most 60 characters, none of `/ \ : * ? " < > |`; it stays a .json file) and **Delete** (after asking; where the storage does not allow it, the file can be taken off the list instead). **Load** still opens any settings file. A round **i** beside the title explains the list, copying a file to another Android device to load it there, and that channels are per device

## 0.8.0 — 2026-11-10

- Keyboard shortcuts explained: a round **i** beside *With a shortcut (Ctrl+Alt+L)* and *Only while Control is held* says what works on Android — Ctrl+Alt+L is the only shortcut (either Ctrl and Alt, not with Shift or Meta held), only on a hardware keyboard, only with the accessibility service on (without it Control is noticed only while the pointer moves), keys that Android or the device maker keep never arrive, and the computers' channel shortcuts do not exist here. It shows while the mouse rests on it, on a tap and on a long press
- **Settings backup** on the General tab: **Save settings** writes every setting to a JSON file through Android's file picker (`flockswitch-settings-<model>-<date-time>.json`, with a header naming the app, system and version), **Load settings** reads one back after asking — a file that is not flockswitch settings for Android, or holds unusable settings, is refused with the reason — and **Open folder** shows Downloads, or the file picker in the folder of a file saved elsewhere. Channels are per device: check them after loading on another device
- Updates (GitHub version): **Check now** only looks, and **Update now** then downloads, checks and installs the new version whatever *Install updates automatically* says; that switch is for the checks made on their own, and is greyed out while *Check for a new version* is off
- Updates (GitHub version) can now go in without Android's confirmation on Android 12 and later, as 0.7.0 meant to: the permission Android needs for that was missing. The update from 0.7.0 to this version still asks
- The **Show log** button is gone; the mail to the developer still carries the recent log

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
