# flockswitch for Android

**English** · [한국어](README.ko.md)

Press one mouse button and move your Logitech mouse **and** keyboard to another computer together — with an Android tablet or phone as one of the computers.

flockswitch turns buttons of an MX Master mouse — the wheel mode-shift button, the gesture button and/or the wheel click (pressing the wheel) — into an Easy-Switch key. When you press one, every device in your group (keyboard, other mice) switches to the same Easy-Switch channel, then the mouse follows. It needs no network between the computers.

This repository is the Android version. The others live in [flockswitch-mac](https://github.com/WWhaleFe/flockswitch-mac), [flockswitch-windows](https://github.com/WWhaleFe/flockswitch-windows) and [flockswitch-linux](https://github.com/WWhaleFe/flockswitch-linux); all of them switch the same way and can be mixed.

> Not affiliated with Logitech. "Logitech", "MX Master", "Easy-Switch", "Bolt" and "Unifying" are trademarks of Logitech.

## Supported mice

flockswitch was built and tested with the **MX Master 3** and **MX Master 4**. Other Logitech mice with the same buttons (HID++ 2.0 *Change Host* and reprogrammable controls) may work but have **not been tested yet**.

## How it connects

- **Logi Bolt or Unifying receiver on USB** (recommended): plug the receiver into the tablet or phone, through a USB-C or OTG adapter if needed. A receiver has three parts: keyboard, mouse and a settings channel (HID++). flockswitch takes only the settings channel, so typing and pointing keep working through Android as usual. Android asks once whether flockswitch may use the receiver; tick *Always open flockswitch* so it does not ask again.
- **Bluetooth** (off by default): Logitech devices connected to Android over Bluetooth offer HID++ in a Logitech service of their own. Turn on *Bluetooth devices* on the General tab and allow *Nearby devices*.

If Android's own Logitech driver turns out to be using a receiver (seen with Unifying receivers on some devices), flockswitch hands it back at once so the mouse and keyboard keep working, and says so on the Home tab.

## Install

Download `flockswitch-android-<version>.apk` from [Releases](../../releases) on the tablet or phone and open it. Android asks to allow installing apps from your browser or file manager the first time.

After that flockswitch keeps itself up to date: it looks for a new version on GitHub as it starts and once a day, downloads it, checks it against the release's `SHA256SUMS.txt` (a file that does not match is never installed) and installs it. The first time, Android asks you to allow flockswitch to install apps. On Android 12 and later it can then update without a question where Android allows an app to update itself; when Android still wants a confirmation, its prompt opens, or a notification *New version ready to install — tap to install* if the app is in the background. Both *Check for a new version* and *Install updates automatically* can be turned off on the General tab, and **Update now** installs a new version by hand. The Google Play version is updated by Play and has none of this.

Install flockswitch on **every** computer you switch between, and pair each device to the same channel number on each (e.g. channel 1 = Windows PC, channel 2 = Mac, channel 3 = Android tablet).

## Use

The app has four tabs; every change applies at once (in the haptics dialog, on Apply or OK):

- **Home**: what is going on, switching on or off, and *Switch now* buttons that send the group and the master mouse to a channel.
- **Buttons**: per button (wheel mode-shift, gesture, wheel click — several can be on), the channel it goes to. Settings are kept per computer, so choose on each one; this device's own channel is shown but cannot be chosen. The gesture button can also switch by holding it and moving the mouse up, down, left or right, each direction with its own channel.
- **Edges** (on the Buttons tab): rest the pointer against the left or right edge of the screen for a moment (0.3 to 2 s, your choice) and the group and the master mouse go to the channel set for that side. No network: each device watches its own screen, so set it up on every computer. Needs *Display over other apps*: thin invisible strips along the edges notice the pointer. The top and bottom edges sit under the status bar and the navigation bar, so they rarely work. A countdown near the pointer shows the wait (eight effects or a random one each time, in the colour and size you choose, with a preview, or none), and the switch can be limited to while Control is held.
- **Devices**: the master mouse, and the devices that move along (devices on another computer are listed too).
- **General**: start when the device starts, find the cursor (a highlight around the pointer when the mouse arrives, on Ctrl+Alt+L or from the notification; in the effect, colour and size you choose; needs Android 14 and the accessibility service, and appears at the next mouse movement), haptics on a mouse with a motor such as the MX Master 4 (*Haptics Settings…*: strength, and for leaving, arriving and waiting at an edge each on or off with its pulse — one of 16, or a random one each time — or, at an edge, rising or even pulses; **Test** plays a choice on the mouse at once), Bluetooth devices (experimental), updates (found, checked and installed automatically), report to the developer, the log, and resetting the settings.

While switching is on, flockswitch runs in the background with a notification that shows the master mouse and this device's channel, with a button for each other channel. Android lets no app take keyboard shortcuts for the whole system, so instead of shortcuts there is a **Quick Settings tile**: add *flockswitch* to Quick Settings and tap it to switch like a press of the wheel mode-shift button would.

When switching is turned off or a button is turned off, the button is put back exactly as it was.

## Permissions

- **USB device access** (asked by Android when the receiver is plugged in): to talk to the receiver.
- **Notifications**: for the background notification with the channel buttons.
- **Nearby devices**: only for Bluetooth devices, or if Android insists on it before letting flockswitch run in the background.
- **Display over other apps**: only for switching at the screen edge. The strips along the edges are invisible and take no input, and the countdown drawn during a wait takes none either.
- **Accessibility service** (off until you turn it on; only for the settings that use it): reads Control and catches Ctrl+Alt+L (other keys pass through untouched), and reads the pointer's position once per find-the-cursor. Android may ask you to allow restricted settings first, in the app's App info page.
- **Internet**: only to ask GitHub whether a newer version exists and to download it (can be turned off). Nothing else is sent anywhere.
- **Install apps** (GitHub version only; Android asks the first time an update is installed): to install an update after its SHA-256 has matched. The Google Play version does not ask for it.

## Build

JDK 17 and the Android SDK (platform 37):

```sh
./gradlew testDebugUnitTest assembleDebug
```

A signed release build reads its key from `FLOCKSWITCH_KEYSTORE`, `FLOCKSWITCH_KEYSTORE_PASSWORD`, `FLOCKSWITCH_KEY_ALIAS` and `FLOCKSWITCH_KEY_PASSWORD`.

## License

MIT
