# Google Play: flockswitch Android 2.1.0 (versionCode 16)

작성: 2026-11-27 · [PLAY_RELEASE.ko.md](PLAY_RELEASE.ko.md)의 0.x 선언을 2.1.0에 맞게 바꾼 내용입니다. 붙여 넣을 영어 문구는 그대로 복사해 쓰세요. 아래 판단은 코드와 매니페스트를 보고 쓴 초안이며, Play 정책 문구는 제출 직전에 Policy Center에서 다시 확인하세요.

## 1. 0.x에서 달라진 점 (코드 기준)

| 항목 | 0.x Play 빌드 | 2.1.0 Play 빌드 |
|---|---|---|
| `INTERNET` | 없음 | **있음** (`app/src/main/AndroidManifest.xml`, Play 매니페스트는 비어 있어 그대로 유지) |
| 그 밖의 네트워크 권한 | 없음 | `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE` (모두 일반 권한) |
| 통신 상대 | 없음 | 사용자가 직접 짝 맺은 자기 컴퓨터만. 같은 네트워크에서 mDNS로 찾거나 IP 주소로 추가. TLS 암호화, 짝 맺은 컴퓨터의 키만 허용 |
| 인터넷으로 나가는 것 | 없음 | **없음**. 서버, 클라우드, 중계, 분석, 광고, 충돌 보고 모두 없음 |
| 업데이트 확인·자체 업데이트 | 없음 | **없음**. `src/play`의 `SelfUpdate`는 아무것도 하지 않는 빈 구현이고, `src/github`의 코드(GitHub 접속, `InstallResultReceiver`)와 `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`은 Play 빌드에 없음. 앱 안의 업데이트 카드와 "개발자에게" 카드도 `BuildConfig.PLAY_STORE`로 숨김. 업데이트는 Google Play가 함 |
| 접근성 서비스가 하는 일 | 모서리 띠, Control·Ctrl+Alt+L 키 확인, 마우스 찾기 | 위에 더해 **짝 맺은 컴퓨터가 보낸 마우스·키보드 입력 실행**: 화면에 커서 그리기(`TYPE_ACCESSIBILITY_OVERLAY`), `dispatchGesture`로 탭·쓸기·스크롤, `performGlobalAction`(뒤로·홈·최근 앱), Android 13 이상에서 `AccessibilityService.getInputMethod()`의 입력 연결로 현재 입력칸에 키 입력 |
| 클립보드 | 없음 | 컴퓨터에서 복사한 **글만 받아** 이 기기 클립보드에 씀(쓰기만, 읽지 않음). 이미지와 파일은 받지 않음 |
| ABI | arm64-v8a, x86_64 (JNI 없음) | arm64-v8a, armeabi-v7a, x86_64 (`libflocknet.so`). 64비트 둘은 16KB 정렬(0x4000) 확인, armeabi-v7a(32비트)는 4KB라 Play의 16KB 요구 대상이 아님 |
| 포그라운드 서비스 | connectedDevice | 그대로 (네트워크 받기도 같은 `SwitchService`가 처리) |

접근성 서비스 설정(`res/xml/pointer_service.xml`)은 `canRetrieveWindowContent=false`, `canRequestFilterKeyEvents=true`, `canPerformGestures=true`입니다. `canPerformGestures`가 새로 선언에 영향을 줍니다.

## 2. 데이터 보안 (Data safety) 초안

| 질문 | 답 |
|---|---|
| 앱에서 필수 사용자 데이터 유형을 수집하거나 공유하나요? | **아니요** |
| 계정 생성 | 앱에서 계정을 만들 수 없음 |
| 결과 미리보기 | "수집된 데이터 없음 / 공유된 데이터 없음" |

근거 (심사나 문의에서 이렇게 설명):

- 개발자는 어떤 데이터도 받지 못합니다. 앱에는 개발자나 제3자의 서버로 가는 통신이 없고, `INTERNET` 권한은 사용자의 자기 컴퓨터와 같은 네트워크에서 직접 통신하는 데만 씁니다.
- 컴퓨터에서 이 기기로 오는 마우스·키보드 입력과 복사한 글은 사용자 본인의 기기 사이를 오가며 이 기기에서 실행되고 저장되지 않습니다. 이 기기에서 컴퓨터로 가는 것은 기기 이름, 화면 크기, 사용자가 바꾼 키 규칙과 연결 유지·짝 맺기에 필요한 신호뿐입니다. 이 기기의 클립보드는 읽지도 보내지도 않습니다.
- 접근성 서비스가 보는 키 상태(Control, Ctrl+Alt+L)와 포인터 위치, 블루투스·USB로 읽는 Logitech 장치 이름·제품 ID는 기기 안에서만 처리합니다.
- Android 자동 백업은 `config.json`만 사용자의 Google 계정으로 보내며(짝 맺은 컴퓨터의 이름·시스템·주소 포함, 네트워크 키 파일은 제외), 사용자의 Google 백업 설정에 따라 Android가 하는 일이고 개발자는 접근할 수 없으므로 "수집"으로 선언하지 않습니다(0.x와 같은 판단).

보수적인 대안: Play 심사가 "사용자 간 기기 전송도 수집"이라고 보면 다음으로 바꿉니다. 이 경우에도 "공유"와 "수집" 모두 개발자 서버가 없다는 점을 설명란에 적습니다.

| 항목 | 답 |
|---|---|
| 수집 유형 | 앱 활동 > 기타 사용자 생성 콘텐츠(클립보드 글), 기기 또는 기타 ID 아님 |
| 목적 | 앱 기능 |
| 전송 중 암호화 | 예 (TLS) |
| 삭제 요청 | 앱에 데이터를 삭제하는 방법을 제공 (설정 초기화, 앱 데이터 삭제) |
| 선택 사항인가 | 예 (네트워크 받기는 사용자가 켜야 함) |

## 3. 접근성 API 선언

위치: 앱 콘텐츠 > 접근성 API. 접근성 도구인가요? **아니요**(`isAccessibilityTool` 없음, 기본값 false). 개인·민감 데이터를 수집하거나 공유하나요? **아니요**.

### 붙여 넣을 설명 (영어, 양식용)

> flockswitch lets a user move one Logitech mouse and keyboard between their own computers, and, since 2.1, lets the user's own computer (running flockswitch) use this Android device's screen with its mouse and keyboard. The accessibility service is optional and is used only for four features the user turns on:
> 1. Switching at the top and bottom screen edges: an accessibility overlay (TYPE_ACCESSIBILITY_OVERLAY) lets invisible, non-touchable strips sit over the status and navigation bars to notice the mouse pointer there. An ordinary overlay cannot cover those bars.
> 2. "Only while Control is held" and the Ctrl+Alt+L "Find the cursor" shortcut: key events of a hardware keyboard are filtered (FLAG_REQUEST_FILTER_KEY_EVENTS, requested only while one of these settings is on) to read only whether Control is held and to catch Ctrl+Alt+L. Every other key is passed on unchanged.
> 3. Find the cursor (Android 14+): after the user asks for it, mouse motion events are requested for a single event to get the pointer's position and draw a highlight there.
> 4. Using this device with the user's own paired computer: only while "Receive mouse and keyboard over the network" is on and the user has paired a computer (6-digit code comparison, then a TLS connection that accepts only that computer's key), the service carries out the mouse and keyboard input that computer sends: it draws a pointer on screen (accessibility overlay), performs taps, swipes and scrolling with dispatchGesture, performs Back, Home and Recents with performGlobalAction, and, on Android 13 and later, enters keys into the focused field through the service's input method connection. Android offers no other way for an app to inject input from another computer.
> The service does not retrieve window content (canRetrieveWindowContent=false), takes no action on its own, only carries out the input the user's paired computer sends while the user has this feature on, stores nothing and does not record what is typed. The input comes from the user's own computer over the local network; the app sends nothing to the internet, has no update check in this build, and has no server. Before the user is sent to Android's accessibility settings, the app shows a disclosure of what the service accesses and how it is used, with "Agree" and "No thanks" buttons.

짧은 버전 (글자 수 제한용):

> Optional. Used for (1) invisible strips over the status/navigation bars to detect the mouse pointer at screen edges, (2) reading only the Control key and Ctrl+Alt+L from a hardware keyboard, (3) the pointer position once for "Find the cursor", and (4) carrying out mouse and keyboard input sent by the user's own paired computer over the local network (pointer overlay, gestures, Back/Home/Recents, key entry), only while the user turns that on. No window content, nothing stored, nothing sent to the internet. An in-app disclosure with Agree / No thanks comes first.

### 앱 안 공개 창 (이미 앱에 들어 있음)

`a11y_disclosure_title`과 `a11y_disclosure_text`(영어는 `values/strings.xml`, 한국어는 `values-ko/strings.xml`)를 네 번째 기능에 맞게 고쳤습니다. 내용은 위 설명과 같고, 접근하는 정보 네 가지(키 입력, 포인터 위치, 화면 위 표시, 내 컴퓨터에서 오는 입력), 사용 방식(이 기기 안에서만, 저장·공유 없음, 짝 맺은 컴퓨터와만 통신, 인터넷으로 아무것도 보내지 않음), 끄는 방법을 담습니다. 한국어 전문:

> flockswitch는 Android 접근성 서비스(AccessibilityService API)를 다음 네 가지 선택 기능에만 씁니다: 위쪽·아래쪽 화면 모서리 전환, 'Control 키를 누른 동안에만 전환', 마우스 찾기, 내 컴퓨터의 마우스·키보드로 이 기기 쓰기(네트워크 탭).
>
> 접근하는 정보
> - 하드웨어 키보드의 키 입력: Control을 누르고 있는지만 확인하고 Ctrl+Alt+L만 받으며, 다른 키는 그대로 전달합니다.
> - 마우스 포인터 위치: 마우스 찾기를 요청한 뒤 다음 마우스 움직임에서 한 번 읽습니다.
> - 화면 위 표시: 모서리 감지용 얇은 띠, 마우스 찾기 효과, 내 컴퓨터 마우스의 커서.
> - 내 컴퓨터에서 오는 입력: 네트워크 받기가 켜져 있고 컴퓨터를 짝 맺은 동안에만, 그 컴퓨터의 마우스 움직임·클릭·스크롤·키 입력이 암호화되어 이 기기에 오고, 서비스가 이 기기에서 실행합니다.
>
> 화면 내용은 읽지 않으며, 입력한 키를 기록하거나 보관하지 않습니다.

### 심사용 영상에 더할 장면

0.x 영상(PLAY_RELEASE.ko.md 6-1)의 1~5번에 이어서:

6. 네트워크 탭에서 *네트워크로 마우스·키보드 받기*를 켜고, 안내문(Android는 받기만 함)을 보여 줍니다.
7. 컴퓨터를 **짝 맺기**로 고르고 두 화면의 6자리 숫자가 같은 것을 보여 준 뒤 양쪽에서 확인합니다.
8. 컴퓨터 마우스를 화면 가장자리로 밀어 이 기기로 넘기고(포인터가 그려짐), 컴퓨터의 마우스로 탭하고 키보드로 입력창에 글을 쓰는 장면. 컴퓨터로 돌아가는 장면.
9. 접근성 서비스를 끄면 입력이 실행되지 않고 네트워크 탭이 "입력을 재현하려면 접근성 서비스가 필요합니다"를 보여 주는 장면.

### 위험과 대응

- **가장 큰 위험**: 다른 컴퓨터가 보낸 입력을 접근성 서비스로 실행하는 것은 "원격 제어"로 읽힐 수 있고, Play의 접근성 API 정책이 이를 허용하는지는 제출 전에 Policy Center의 현재 문구로 확인해야 합니다(여기서는 단정하지 않습니다). 사용자가 직접 켠 선택 기능이고, 짝 맺기(숫자 비교)와 TLS로 본인의 컴퓨터만 연결되며, 이 기기에서 밖으로 나가는 입력이 없다는 점을 설명과 영상에서 분명히 보여 주세요.
- 거절되면 **대안**: Play 빌드에서만 네트워크 받기의 입력 실행을 끕니다. 방법은 (a) Play 빌드에서 네트워크 탭과 `NetworkReceiver`를 숨기고 `INTERNET`도 다시 뺌(0.x 선언으로 돌아감), 또는 (b) 접근성 서비스에서 `canPerformGestures`와 입력 실행만 빼고 Play 빌드는 홈·로지텍·효과·일반만 제공. 필요하면 요청해 주세요. GitHub 빌드는 영향이 없습니다.
- 접근성 도구 여부, 키 이벤트 필터링 설명은 0.x와 같은 주의가 필요합니다("Control 상태와 Ctrl+Alt+L만, 나머지는 그대로 통과").

## 4. 포그라운드 서비스 설명에 더할 문장 (영어)

> While "Receive mouse and keyboard over the network" is on, the same service also keeps the encrypted connection to the user's paired computer open so that its pointer and keys reach this device immediately, and redials it when the phone is unlocked or the network changes.

## 5. 스토어 설명과 개인정보처리방침

- 개인정보처리방침: 저장소의 `PRIVACY.md`(한국어·영어)를 2026-11-27 시행으로 고쳤습니다. 게시된 주소의 내용을 같은 내용으로 갱신해야 합니다(Play Console의 방침 URL은 그대로 쓰고 내용만 바꿈).
- 스토어 설명에서 "인터넷 권한 없음"이라고 쓴 문장은 모두 **"인터넷으로는 아무것도 보내지 않음, 네트워크는 사용자가 짝 맺은 자기 컴퓨터와 직접 통신하는 데만 사용"**으로 바꿉니다. `listing/` 아래 파일은 이 작업에서 건드리지 않았으므로 직접 확인하세요(`grep -rn -i "internet\|인터넷" listing`).
- 새 기능 소개 문구 (설명에 더할 수 있음):
  - 한국어: "컴퓨터의 마우스와 키보드로 이 기기를 쓰세요. flockswitch가 있는 컴퓨터를 짝 맺으면 그 컴퓨터의 포인터와 키 입력, 복사한 글이 같은 네트워크에서 바로 이 기기로 옵니다. Android는 받기만 합니다."
  - English: "Use this device with your computer's mouse and keyboard. Pair a computer running flockswitch and its pointer, key presses and copied text reach this device directly over your network. Android only receives."

## 6. 제출 전 확인

- [ ] Play 빌드: `./gradlew -PplayStore testDebugUnitTest assembleRelease` 성공, 번들은 `bundleRelease` (versionCode 16)
- [ ] 합쳐진 Play 매니페스트에 `INTERNET`이 있고 `REQUEST_INSTALL_PACKAGES`가 없음 (`apkanalyzer manifest print`)
- [ ] 실기기: 네트워크 받기, 짝 맺기, 입력 실행, 재연결(잠금 해제·화면 켜짐·와이파이 전환), 접근성 공개 창
- [ ] Data safety 양식과 접근성 선언, 방침 URL 내용, 스토어 설명 문구를 위 내용으로 갱신
- [ ] targetSdk를 37로 올릴 때는 `ACCESS_LOCAL_NETWORK` 런타임 권한과 그 선언 추가 (현재 targetSdk 36)
