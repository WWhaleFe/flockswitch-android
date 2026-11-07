# 안드로이드용 flockswitch

[English](README.md) · **한국어**

마우스 버튼 하나로 Logitech 마우스와 **키보드를 함께** 다른 컴퓨터로 넘깁니다. 안드로이드 태블릿이나 폰도 그 컴퓨터 중 하나가 될 수 있습니다.

flockswitch는 MX Master 마우스의 버튼(휠 모드 전환 버튼, 제스처 버튼, 휠 클릭)을 Easy-Switch 키로 바꿉니다. 누르면 그룹에 넣은 장치(키보드, 다른 마우스)가 먼저 같은 Easy-Switch 채널로 넘어가고, 마지막에 마우스가 따라갑니다. 컴퓨터 사이에 네트워크가 필요 없습니다.

이 저장소는 안드로이드 버전입니다. 다른 버전은 [flockswitch-mac](https://github.com/WWhaleFe/flockswitch-mac), [flockswitch-windows](https://github.com/WWhaleFe/flockswitch-windows), [flockswitch-linux](https://github.com/WWhaleFe/flockswitch-linux)에 있고, 모두 같은 방식으로 전환하므로 섞어 쓸 수 있습니다.

> Logitech과 관계없는 프로젝트입니다. "Logitech", "MX Master", "Easy-Switch", "Bolt", "Unifying"은 Logitech의 상표입니다.

## 지원 마우스

**MX Master 3**와 **MX Master 4**로 만들고 시험했습니다. 같은 버튼(HID++ 2.0 *Change Host*, 재지정 가능한 버튼)을 가진 다른 Logitech 마우스도 될 수 있지만 **아직 시험하지 않았습니다**.

## 연결 방식

- **USB에 꽂은 Logi Bolt·Unifying 수신기** (권장): 수신기를 태블릿이나 폰에 꽂습니다. 필요하면 USB-C·OTG 어댑터를 씁니다. 수신기에는 키보드, 마우스, 설정용 통로(HID++) 세 부분이 있는데, flockswitch는 설정용 통로만 가져가므로 입력과 포인터는 안드로이드에서 평소처럼 동작합니다. 처음 꽂을 때 안드로이드가 수신기 사용을 한 번 묻습니다. *항상 flockswitch로 열기*를 체크하면 다시 묻지 않습니다.
- **블루투스** (실험, 기본값 꺼짐): 블루투스로 안드로이드에 연결된 Logitech 장치는 Logitech 전용 서비스로 HID++를 제공합니다. 일반 탭에서 *블루투스 장치*를 켜고 *근처 기기* 권한을 허용하세요.

안드로이드의 Logitech 드라이버가 수신기를 쓰고 있으면(일부 기기의 Unifying 수신기), 마우스와 키보드가 끊기지 않도록 flockswitch가 수신기를 바로 돌려주고 홈 탭에 알려 줍니다.

## 설치

태블릿이나 폰에서 [Releases](../../releases)의 `flockswitch-android-<버전>.apk`를 받아 엽니다. 처음에는 브라우저나 파일 앱에서 앱을 설치하도록 허용하라고 안드로이드가 묻습니다.

전환할 **모든** 컴퓨터에 flockswitch를 설치하고, 각 장치를 컴퓨터마다 같은 채널 번호에 페어링하세요 (예: 1 = 윈도우 PC, 2 = 맥, 3 = 안드로이드 태블릿).

## 사용

탭은 네 개이고, 바꾼 내용은 바로 적용됩니다.

- **홈**: 현재 상태, 전환 켜기/끄기, 그룹 장치와 마스터 마우스를 그 채널로 보내는 *지금 전환* 버튼
- **버튼**: 버튼별(휠 모드 전환, 제스처, 휠 클릭 — 여러 개를 켤 수 있음) 이동할 채널. 설정은 컴퓨터마다 따로라서 각 컴퓨터에서 고르며, 이 기기의 채널은 보이지만 고를 수 없습니다. 제스처 버튼은 누른 채 마우스를 위·아래·왼쪽·오른쪽으로 움직였다 떼는 방향마다 채널을 따로 정할 수 있습니다.
- **장치**: 마스터 마우스, 함께 이동할 장치 (다른 컴퓨터에 연결된 장치도 표시)
- **일반**: 기기가 켜지면 자동 시작, 블루투스 장치(실험), 업데이트, 개발자에게 리포트

전환을 켜 두면 flockswitch가 알림을 띄운 채 백그라운드에서 동작합니다. 알림에는 마스터 마우스와 이 기기의 채널, 다른 채널로 보내는 버튼이 있습니다. 안드로이드는 앱이 시스템 전체 단축키를 쓰지 못하게 하므로, 단축키 대신 **빠른 설정 타일**을 제공합니다. 빠른 설정에 *flockswitch*를 추가하고 누르면 휠 모드 전환 버튼을 누른 것처럼 전환합니다.

전환을 끄거나 버튼을 끄면 그 버튼은 원래 상태로 정확히 돌아갑니다.

## 권한

- **USB 기기 접근** (수신기를 꽂으면 안드로이드가 물음): 수신기와 통신
- **알림**: 채널 버튼이 있는 백그라운드 알림
- **근처 기기**: 블루투스 장치를 쓸 때, 또는 안드로이드가 백그라운드 동작에 요구할 때만
- **인터넷**: GitHub에서 새 버전이 있는지 확인할 때만 (끌 수 있음). 그 밖에는 아무것도 보내지 않습니다.

## 빌드

JDK 17과 Android SDK(플랫폼 37):

```sh
./gradlew testDebugUnitTest assembleDebug
```

서명된 릴리스 빌드는 `FLOCKSWITCH_KEYSTORE`, `FLOCKSWITCH_KEYSTORE_PASSWORD`, `FLOCKSWITCH_KEY_ALIAS`, `FLOCKSWITCH_KEY_PASSWORD`에서 키를 읽습니다.

## 라이선스

MIT
