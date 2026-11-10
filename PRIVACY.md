# Privacy Policy — flockswitch for Android

**English** · [한국어](#개인정보처리방침--flockswitch-android)

Effective date: November 10, 2026 · Applies to flockswitch for Android 0.9.0 and later, from Google Play or from [GitHub Releases](https://github.com/WWhaleFe/flockswitch-android/releases).

## Summary

flockswitch collects no personal data and sends nothing about you anywhere. The Google Play version has no internet access at all. The version from GitHub connects to the internet only to look for a new version of itself, which you can turn off. Everything the app keeps stays on your phone or tablet.

## What flockswitch does with data

flockswitch talks to the Logitech devices connected to your phone or tablet — through a Logitech receiver plugged into USB, or over Bluetooth — using Logitech's HID++ protocol, to read and change their Easy-Switch channel, to use the mouse buttons you turn on, and to play haptic feedback on a mouse that has a motor. It talks to no other devices.

It keeps these files in its private app storage, which other apps cannot read:

| File | Contents |
|---|---|
| `config.json` | Your settings: buttons, channels, screen edges, effects, haptics, Find the cursor, and the devices in the group (by product ID such as "B042", or by device name) |
| `devices.json` | Logitech devices seen on this device: product ID, name (e.g. "MX Master 4"), kind (mouse, keyboard) and whether it can be the master mouse — so the settings can list them while they are on another computer |
| `button-state.json` | How each mouse button was set before flockswitch used it, so it can be put back |
| App preferences | The settings files you saved with **Save** (their location, name and time), so **Manage** can list them |

No serial numbers, Bluetooth addresses, account details, names, locations, contacts, or anything you type are stored.

The app's activity log (devices found, switches, errors) is kept only in memory while the app runs and in Android's own system log; it is not written to a file.

**Settings files.** **General → Settings backup → Save** writes your settings to a JSON file at a place you choose in Android's file picker. Besides the settings, the file names the app, its version, the Android device's maker and model, and the date. flockswitch only accesses files you pick or saved yourself.

**Android backup.** If backup is turned on in your Android settings, Android backs up `config.json` (and nothing else from flockswitch) to your Google account and moves it to a new phone, so your settings come back after a reinstall. This is done by Android under your Google account's backup settings; the developer cannot see it.

## Permissions and what they are used for

- **USB device access** (Android asks when you plug in a receiver): to talk to the Logitech receiver. flockswitch uses only the receiver's settings channel; typing and pointing keep working.
- **Nearby devices** (Bluetooth connect): to talk to Logitech devices already paired with this device over Bluetooth. flockswitch does not scan for devices and does not use your location.
- **Notifications**, **foreground service (connected device)** and **start at boot**: to keep running in the background with a notification showing the master mouse, this device's channel and channel buttons.
- **Display over other apps**: only for switching at the screen edge. Invisible thin strips along the edges notice the mouse pointer, and the countdown is drawn on top of the screen. They take no input and do not read the screen.
- **Accessibility service** (off until you turn it on yourself; the Google Play version first shows what it accesses and asks for your agreement): only for switching at the top and bottom screen edges, "Only while Control is held", and Find the cursor. While one of the keyboard settings is on, key presses of a hardware keyboard pass through flockswitch: it only checks whether Control is held and takes Ctrl+Alt+L; every other key goes on unchanged. After you ask to find the cursor, it reads the mouse pointer's position once, at the next movement. It does not read what is on the screen, and nothing it sees is stored or sent.
- **Internet** (GitHub version only): see below.
- **Install apps** (GitHub version only): to install an update it downloaded and verified.

## What flockswitch does not do

- **No internet in the Google Play version.** The Google Play version does not have the internet permission. Google Play keeps it up to date.
- **No internet beyond updates in the GitHub version.** At start and once a day, while **General → Updates → Check for a new version** is on (and whenever you press **Check now**), it asks GitHub's public releases API over https whether a new version exists, and, to install it, downloads the new release from GitHub and checks it against the release's published SHA-256 list. These requests carry only what any web request does (your IP address, and a "flockswitch-android/<version>" user agent); no settings, devices or other data are sent. It contacts no other server.
- **No reports sent by the app.** In the GitHub version, **General → To the developer** opens your own mail app with the app version, Android version, device maker and model, the names of the Logitech devices seen, and the recent log filled in. Nothing is sent unless you send that mail yourself, and you can edit it first. The Google Play version has no such button.
- **No tracking, analytics, advertising, accounts or crash reporting.**
- **No sharing.** Nothing is sold or shared with anyone, including the developer and Logitech.

## Deleting your data

- In flockswitch: **General → Reset settings…** puts every setting back to how it started.
- **Android Settings → Apps → flockswitch → Storage → Clear storage**, or uninstalling the app, deletes everything in its private storage.
- Settings files you saved stay where you saved them: delete them in **General → Settings backup → Manage**, or with any file app.
- A backup in your Google account can be managed in Android's backup settings.

## Children

flockswitch collects no data from anyone, including children.

## Changes

If this policy changes, the new version is published at this address with a new effective date. The history of this file is public in the repository.

## Contact

Questions: feedback.wwhale@gmail.com, or [GitHub issues](https://github.com/WWhaleFe/flockswitch-android/issues).

flockswitch is an independent open-source project, not affiliated with Logitech. "Logitech", "MX Master", "Easy-Switch", "Logi Bolt", "Unifying" and "Logi Options+" are trademarks of Logitech.

---

# 개인정보처리방침 — flockswitch (Android)

[English](#privacy-policy--flockswitch-for-android) · **한국어**

시행일: 2026년 11월 10일 · Google Play 또는 [GitHub Releases](https://github.com/WWhaleFe/flockswitch-android/releases)에서 받은 flockswitch Android 0.9.0 이상에 적용됩니다.

## 요약

flockswitch는 개인정보를 수집하지 않으며 사용자에 관한 어떤 정보도 외부로 보내지 않습니다. Google Play 버전은 인터넷에 전혀 연결하지 않습니다. GitHub에서 받은 버전은 새 버전을 확인할 때만 인터넷에 연결하며, 이 기능은 끌 수 있습니다. 앱이 보관하는 내용은 모두 이 휴대전화나 태블릿 안에 있습니다.

## flockswitch가 다루는 정보

flockswitch는 이 기기에 USB로 꽂은 Logitech 수신기나 블루투스로 연결된 Logitech 장치와 Logitech HID++ 프로토콜로 통신합니다. Easy-Switch 채널을 읽고 바꾸며, 사용자가 켠 마우스 버튼을 사용하고, 진동 모터가 있는 마우스에 진동을 보냅니다. 다른 장치와는 통신하지 않습니다.

다음 파일을 다른 앱이 읽을 수 없는 앱 전용 저장 공간에 보관합니다.

| 파일 | 내용 |
|---|---|
| `config.json` | 설정: 버튼, 채널, 화면 모서리, 효과, 진동, 마우스 찾기, 그룹 장치(제품 ID(예: "B042") 또는 장치 이름) |
| `devices.json` | 이 기기에서 본 Logitech 장치의 제품 ID, 이름(예: "MX Master 4"), 종류(마우스·키보드), 마스터 마우스가 될 수 있는지 — 장치가 다른 컴퓨터에 가 있어도 설정에 표시하기 위함 |
| `button-state.json` | flockswitch가 쓰기 전 각 마우스 버튼의 원래 상태 — 되돌려 놓기 위함 |
| 앱 환경설정 | **저장**으로 저장한 설정 파일의 위치·이름·시각 — **관리**에서 목록을 보여 주기 위함 |

일련번호, 블루투스 주소, 계정 정보, 이름, 위치, 연락처, 사용자가 입력한 내용은 저장하지 않습니다.

앱 동작 기록(찾은 장치, 전환, 오류)은 앱이 실행 중인 동안 메모리와 Android 시스템 로그에만 남고, 파일로 쓰지 않습니다.

**설정 파일.** **일반 → 설정 백업 → 저장**은 설정을 Android 파일 선택기에서 고른 위치에 JSON 파일로 저장합니다. 파일에는 설정과 함께 앱 이름, 버전, 기기 제조사와 모델, 날짜가 적힙니다. flockswitch는 사용자가 고르거나 직접 저장한 파일에만 접근합니다.

**Android 백업.** Android 설정에서 백업이 켜져 있으면 Android가 `config.json`(flockswitch의 다른 파일은 제외)을 사용자의 Google 계정에 백업하고 새 휴대전화로 옮겨, 앱을 다시 설치해도 설정이 돌아옵니다. 이 백업은 사용자의 Google 계정 백업 설정에 따라 Android가 처리하며, 개발자는 볼 수 없습니다.

## 권한과 용도

- **USB 기기 접근** (수신기를 꽂으면 Android가 물음): Logitech 수신기와 통신. flockswitch는 수신기의 설정용 통로만 쓰므로 입력과 포인터는 그대로 동작합니다.
- **근처 기기** (블루투스 연결): 이 기기와 이미 페어링된 Logitech 블루투스 장치와 통신. 장치를 검색하지 않으며 위치 정보를 쓰지 않습니다.
- **알림**, **포그라운드 서비스(연결된 기기)**, **부팅 시 시작**: 마스터 마우스, 이 기기의 채널, 채널 버튼을 보여 주는 알림을 띄운 채 백그라운드에서 동작.
- **다른 앱 위에 표시**: 화면 모서리 전환에만. 가장자리를 따라 보이지 않는 얇은 띠가 마우스 포인터를 감지하고, 전환 대기 효과를 화면 위에 그립니다. 입력을 가로채거나 화면 내용을 읽지 않습니다.
- **접근성 서비스** (사용자가 직접 켜기 전까지 꺼짐. Google Play 버전은 먼저 무엇에 접근하는지 보여 주고 동의를 받음): 위쪽·아래쪽 화면 모서리 전환, ‘Control 키를 누른 동안에만 전환’, 마우스 찾기에만 씁니다. 키보드 관련 설정이 켜져 있는 동안 하드웨어 키보드의 키 입력이 flockswitch를 거쳐 가며, Control을 누르고 있는지만 확인하고 Ctrl+Alt+L만 받습니다. 다른 키는 그대로 전달합니다. 마우스 찾기를 요청하면 다음 마우스 움직임에서 포인터 위치를 한 번 읽습니다. 화면 내용은 읽지 않으며, 본 내용을 저장하거나 보내지 않습니다.
- **인터넷** (GitHub 버전만): 아래 참고.
- **앱 설치** (GitHub 버전만): 내려받아 확인한 업데이트를 설치.

## flockswitch가 하지 않는 것

- **Google Play 버전은 인터넷 연결 없음.** Google Play 버전에는 인터넷 권한이 없습니다. 업데이트는 Google Play가 합니다.
- **GitHub 버전은 업데이트 외 인터넷 연결 없음.** **일반 → 업데이트 → 새 버전 확인**이 켜져 있으면 시작할 때와 하루에 한 번(그리고 **지금 확인**을 누를 때) GitHub의 공개 릴리스 API에 https로 새 버전이 있는지 묻고, 설치할 때는 GitHub에서 새 릴리스를 내려받아 릴리스에 함께 게시된 SHA-256 목록과 맞는지 확인합니다. 이 요청에는 일반적인 웹 요청에 포함되는 정보(IP 주소, "flockswitch-android/<버전>" 사용자 에이전트)만 담기며, 설정·장치 등 다른 정보는 보내지 않습니다. 그 밖의 서버에는 연결하지 않습니다.
- **앱이 리포트를 보내지 않음.** GitHub 버전의 **일반 → 개발자에게**는 앱 버전, Android 버전, 기기 제조사와 모델, 본 Logitech 장치 이름, 최근 기록을 채운 상태로 사용자의 메일 앱을 엽니다. 사용자가 직접 보내지 않으면 아무것도 전송되지 않으며, 보내기 전에 고칠 수 있습니다. Google Play 버전에는 이 버튼이 없습니다.
- **추적·분석·광고·계정·충돌 보고 없음.**
- **제3자 제공 없음.** 개발자와 Logitech을 포함해 누구에게도 정보를 판매하거나 제공하지 않습니다.

## 정보 삭제

- 앱에서: **일반 → 설정 초기화…**를 누르면 모든 설정이 처음 상태로 돌아갑니다.
- **Android 설정 → 애플리케이션 → flockswitch → 저장공간 → 데이터 삭제**, 또는 앱을 삭제하면 앱 전용 저장 공간의 내용이 모두 지워집니다.
- 직접 저장한 설정 파일은 저장한 곳에 남습니다. **일반 → 설정 백업 → 관리**나 파일 앱에서 지우세요.
- Google 계정의 백업은 Android 백업 설정에서 관리할 수 있습니다.

## 아동

flockswitch는 아동을 포함한 누구의 정보도 수집하지 않습니다.

## 변경

이 방침이 바뀌면 새 시행일과 함께 이 주소에 게시합니다. 이 파일의 변경 기록은 저장소에서 누구나 볼 수 있습니다.

## 문의

feedback.wwhale@gmail.com 또는 [GitHub 문제 신고](https://github.com/WWhaleFe/flockswitch-android/issues)

flockswitch는 Logitech과 관계없는 개인 오픈소스 프로젝트입니다. "Logitech", "MX Master", "Easy-Switch", "Logi Bolt", "Unifying", "Logi Options+"는 Logitech의 상표입니다.
