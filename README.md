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
- **Bluetooth** (experimental, off by default): Logitech devices connected to Android over Bluetooth offer HID++ in a Logitech service of their own. Turn on *Bluetooth devices* on the General tab and allow *Nearby devices*.

If Android's own Logitech driver turns out to be using a receiver (seen with Unifying receivers on some devices), flockswitch hands it back at once so the mouse and keyboard keep working, and says so on the Home tab.

## Install

Download `flockswitch-android-<version>.apk` from [Releases](../../releases) on the tablet or phone and open it. Android asks to allow installing apps from your browser or file manager the first time.

Install flockswitch on **every** computer you switch between, and pair each device to the same channel number on each (e.g. channel 1 = Windows PC, channel 2 = Mac, channel 3 = Android tablet).

## Use

The app has four tabs; every change applies at once:

- **Home**: what is going on, switching on or off, and *Switch now* buttons that send the group and the master mouse to a channel.
- **Buttons**: per button (wheel mode-shift, gesture, wheel click — several can be on), the channel it goes to. Settings are kept per computer, so choose on each one; this device's own channel is shown but cannot be chosen. The gesture button can also switch by holding it and moving the mouse up, down, left or right, each direction with its own channel.
- **Devices**: the master mouse, and the devices that move along (devices on another computer are listed too).
- **General**: start when the device starts, Bluetooth devices (experimental), updates, report to the developer.

While switching is on, flockswitch runs in the background with a notification that shows the master mouse and this device's channel, with a button for each other channel. Android lets no app take keyboard shortcuts for the whole system, so instead of shortcuts there is a **Quick Settings tile**: add *flockswitch* to Quick Settings and tap it to switch like a press of the wheel mode-shift button would.

When switching is turned off or a button is turned off, the button is put back exactly as it was.

## Permissions

- **USB device access** (asked by Android when the receiver is plugged in): to talk to the receiver.
- **Notifications**: for the background notification with the channel buttons.
- **Nearby devices**: only for Bluetooth devices, or if Android insists on it before letting flockswitch run in the background.
- **Internet**: only to ask GitHub whether a newer version exists (can be turned off). Nothing else is sent anywhere.

## Build

JDK 17 and the Android SDK (platform 37):

```sh
./gradlew testDebugUnitTest assembleDebug
```

A signed release build reads its key from `FLOCKSWITCH_KEYSTORE`, `FLOCKSWITCH_KEYSTORE_PASSWORD`, `FLOCKSWITCH_KEY_ALIAS` and `FLOCKSWITCH_KEY_PASSWORD`.

## License

MIT
