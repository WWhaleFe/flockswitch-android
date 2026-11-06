# Changelog

## Unreleased

- Bluetooth: a device that is connected but could not be opened, as can happen right after the mouse comes back while Android is still bringing its own link up, is tried again after a pause (5 seconds, growing to a minute). Before, it was only tried again once it disconnected and reconnected
- Bluetooth: a failed service discovery no longer marks a Logitech device as "not Logitech" until the app restarts; it is tried again instead

## 0.1.0 — 2026-11-03

- First Android version, with the same switching as flockswitch for macOS 0.4.3: buttons, gestures, devices that move along, and a channel per button (this device's own channel cannot be chosen). Not tried on a real Android device yet
- Logi Bolt and Unifying receivers on USB: only the receiver's HID++ part is taken, so typing and pointing keep working. A receiver that Android's own driver uses is handed back at once
- Logitech devices on Bluetooth (experimental, off by default)
- Runs in the background with a notification showing the master mouse and this device's channel, with a button per channel; a Quick Settings tile switches like the wheel mode-shift button, in place of keyboard shortcuts
- Starts when the device starts, checks GitHub for a new version, and a report to the developer from the General tab
