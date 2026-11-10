# Google Play 출시 안내 (flockswitch Android 0.9.0)

작성: 2026-11-10 · 대상: 새로 만들 Google Play Console 개인 개발자 계정 · 패키지 `io.github.wwhalefe.flockswitch`

이 문서는 Play Console에서 앱을 만들고, 선언을 채우고, 테스트를 거쳐 프로덕션에 올리기까지의 순서입니다. 붙여 넣을 문구는 그대로 복사해 쓰면 됩니다. Play Console 화면 이름은 한국어 화면 기준이고 괄호 안에 영어 이름을 적었습니다.

---

## 0. 준비된 파일

| 무엇 | 경로 (`android/` 기준) | 비고 |
|---|---|---|
| 업로드할 번들 (AAB) | `app/build/outputs/bundle/release/app-release.aab` | 0.9.0 / versionCode 13, 업로드 키로 서명. 커밋하지 않음 |
| 개인정보처리방침 | `PRIVACY.md` | 영어+한국어. **푸시해야 URL이 열립니다** |
| 스토어 문구 (한국어) | `packaging/play/listing/ko-KR/` | `title.txt`(26자), `short_description.txt`(57자), `full_description.txt`(약 2,800자), `changelogs/13.txt`(215자) |
| 스토어 문구 (영어) | `packaging/play/listing/en-US/` | `title.txt`(30자), `short_description.txt`(78자), `full_description.txt`(약 3,940자), `changelogs/13.txt`(373자) |
| 앱 아이콘 512×512 | `packaging/play/listing/icon-512-fullbleed.png` (권장) 또는 `icon-512.png` | 아래 7-2 참고 |
| 그래픽 이미지 1024×500 | `packaging/play/listing/feature-graphic-1024x500.png` | 24비트 PNG, 투명도 없음 |
| 스크린샷 계획 | `packaging/play/listing/SCREENSHOTS.md` | 직접 캡처 필요 |

글자 수 한도: 앱 이름 30자, 간단한 설명 80자, 자세한 설명 4,000자, 출시 노트 500자. 모두 한도 안입니다.

---

## 1. 이번에 바뀐 코드 (커밋 필요)

Play 정책 때문에 **Google Play 빌드(`-PplayStore`)에만** 다음을 바꿨습니다. GitHub 빌드(APK)는 동작과 권한이 그대로입니다.

1. **접근성 서비스 공개·동의 창** (`ui/Screens.kt`의 `AccessibilityGuide`, 문자열 `a11y_*`, `guide_step2_play`)
   - Play 정책은 접근성 도구가 아닌 앱이 접근성 API를 쓰면, 설정 화면으로 보내기 **전에** 무엇에 접근하고 어떻게 쓰는지 앱 안에서 알리고, **동의 버튼 같은 적극적인 동작**으로 동의를 받도록 요구합니다. 기존 안내 창에는 설명은 있었지만 동의/거부 버튼이 없어서, “접근성 설정” 버튼을 누르는 것이 곧 동의로 처리됐습니다.
   - Play 빌드에서는 “설정 방법 보기”나 일반 탭 → 권한 → 접근성 서비스 “설정”을 누르면 먼저 **접근성 서비스 사용 안내** 창이 뜨고, **동의 안 함 / 동의** 버튼이 있습니다. 바깥을 눌러도 닫히지 않습니다. 동의하면 기존 단계 안내가 나오고, 동의 안 함이나 뒤로 가기는 창만 닫습니다(같은 버튼으로 다시 열 수 있음). 서비스가 이미 켜져 있으면 바로 단계 안내가 나옵니다.
   - Play에서 설치한 앱에는 ‘제한된 설정’이 걸리지 않으므로, Play 빌드의 단계 안내에서는 ‘제한된 설정 허용’ 단계(2번)와 “앱 정보 열기” 버튼을 뺐습니다.
2. **인터넷 권한 제거** (`app/src/play/AndroidManifest.xml` 새 파일, `app/build.gradle.kts`의 `sourceSets` 몇 줄)
   - Play 빌드에는 업데이트 확인 코드가 없어 인터넷을 쓰는 곳이 없습니다. 그래서 Play 빌드의 매니페스트에서 `INTERNET`을 뺐습니다. 공개 창, 스토어 설명, 개인정보처리방침의 “인터넷 권한 없음”이 이 사실에 기대므로, 이 변경을 되돌리면 그 문구들도 고쳐야 합니다.
   - `build.gradle.kts`는 GitHub 빌드에 `src/github/AndroidManifest.xml`을, Play 빌드에 `src/play/AndroidManifest.xml`을 붙이도록 한 줄로 합쳤을 뿐이며, GitHub 빌드에 붙는 매니페스트는 전과 같습니다.

확인한 것: `./gradlew testDebugUnitTest assembleDebug`(GitHub 빌드) 성공, `./gradlew -PplayStore assembleDebug` 성공, `./gradlew -PplayStore bundleRelease` 성공. GitHub 디버그 빌드의 합친 매니페스트에는 `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `INTERNET`, `InstallResultReceiver`가 그대로 있습니다.

**할 일:** 위 변경과 `PRIVACY.md`, `packaging/play/`를 커밋하고 `main`에 푸시하세요. AAB는 이 작업 트리에서 만들었으므로, 커밋 후 같은 명령으로 다시 빌드해 올리면 커밋과 AAB가 정확히 일치합니다. 이 변경은 GitHub 빌드에 영향이 없으므로 GitHub 릴리스(태그)를 새로 낼 필요는 없습니다.

---

## 2. AAB 빌드와 검증

```sh
cd android
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
set -a; . ../android-signing/flockswitch-release.env; set +a   # 값은 화면에 출력하지 마세요
./gradlew -PplayStore bundleRelease
# 결과: app/build/outputs/bundle/release/app-release.aab
```

`bundleRelease`는 이미 Android Gradle 플러그인이 제공하고, 서명 설정(`FLOCKSWITCH_KEYSTORE` 등 환경 변수)도 APK와 같이 적용되므로 빌드 설정에 따로 추가할 것은 없었습니다.

2026-11-10에 만든 AAB의 확인 결과:

| 항목 | 값 | 판정 |
|---|---|---|
| versionCode / versionName | 13 / 0.9.0 | GitHub 0.9.0과 같음 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 | Play는 2026-08-31부터 새 앱·업데이트에 **targetSdk 36(Android 16) 이상**을 요구 → 충족 |
| 권한 | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `BLUETOOTH_CONNECT`, `BLUETOOTH`(API 30 이하), `SYSTEM_ALERT_WINDOW`, 앱 자체 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | 설치 권한(`REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`) **없음**, `INTERNET` **없음** |
| 업데이트 코드 | `InstallResultReceiver` 없음, dex 안에 `github.com` 문자열 없음 | 자체 업데이트 코드가 들어 있지 않음 |
| 네이티브 라이브러리 | `libandroidx.graphics.path.so` (4개 ABI) | 모두 16KB 페이지 정렬(0x4000) → Play의 16KB 요구 충족 |
| 서명 | `jarsigner -verify` → `jar verified.` | 업로드 키(기존 릴리스 키)로 서명됨 |
| 광고 ID 권한 (`AD_ID`) | 없음 | “광고 ID 사용 안 함”과 일치 |

다음에 직접 검증하려면 (bundletool이 없어도 됨):

```sh
cd /tmp && rm -rf aab && mkdir aab && cd aab && unzip -q <경로>/app-release.aab
mkdir p && cp base/manifest/AndroidManifest.xml base/resources.pb p/ && (cd p && zip -q ../p.apk *)
/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/aapt2 dump xmltree --file AndroidManifest.xml p.apk | grep -E "permission|versionCode|versionName|SdkVersion"
```

**versionCode 주의:** GitHub APK와 Play가 같은 패키지 이름을 쓰므로 versionCode는 두 곳을 합쳐 계속 커져야 합니다. 이번 Play 업로드가 13이므로 다음 출시(어느 쪽이든)는 14 이상이어야 합니다. 한 번 올린 versionCode는 Play에서 다시 쓸 수 없습니다.

---

## 3. 결정할 것: Play 앱 서명 키

Play 앱 서명(Play App Signing)을 쓰면 사용자 기기에 설치되는 APK는 **앱 서명 키**로 서명되고, 개발자는 **업로드 키**로 서명한 AAB만 올립니다. 업로드 키는 기존 릴리스 키스토어(`android-signing/flockswitch-release.jks`)로 정했습니다. 남은 결정은 앱 서명 키입니다. 첫 AAB를 올릴 때 고르며, 나중에 바꾸기 어렵습니다.

| | A. Google이 앱 서명 키 생성 (Play 기본값) | B. 기존 릴리스 키를 앱 서명 키로 올림 |
|---|---|---|
| 방법 | 첫 업로드 때 기본값 그대로 | “앱 서명 키 변경” → “Java 키스토어에서 키 내보내기 및 업로드” → Play가 주는 PEPK 도구와 암호화 공개 키로 `.zip`을 만들어 올림 |
| GitHub APK 사용자 | 서명이 달라 **Play 버전으로 바로 갈아탈 수 없음** (삭제 후 설치. 설정은 설정 백업 파일이나 Google 백업으로 복원) | 서명이 같아 GitHub APK 위에 Play 버전이 업데이트로 설치됨 |
| F-Droid/IzzyOnDroid | 서명이 다른 두 가지 APK가 세상에 존재 | 한 가지 서명 |
| 보안 | 앱 서명 키가 Google에만 있음, 키 업그레이드 가능 | 같은 키가 GitHub 빌드에도 쓰이므로 이 키를 잃거나 유출되면 양쪽이 영향 |

권장: 이미 GitHub로 쓰는 사용자가 있고 IzzyOnDroid 배포도 계획 중이므로 **B**가 실용적입니다. B를 고르면 Play는 업로드 키를 앱 서명 키와 다르게 만들기를 권장합니다(필수는 아님). 업로드 키를 새로 만들 경우 그 키로 AAB를 다시 서명해야 하니 알려 주세요. A를 고르면 지금 AAB를 그대로 올리면 됩니다.

어느 쪽이든 키스토어 파일과 `.env`는 저장소에 넣지 말고 지금처럼 `android-signing/`에만 두세요.

---

## 4. Play Console에서 앱 만들기

1. Play Console → **앱 만들기 (Create app)**
2. **앱 이름**: `flockswitch` (나중에 스토어 등록정보에서 `listing/*/title.txt`의 긴 이름으로 바꿀 수 있음. 앱 만들기 단계에서도 그대로 넣어도 됨)
3. **기본 언어**: **English (United States) – en-US** 권장. 기본 언어는 번역이 없는 모든 나라에 보이는 언어입니다. 한국어(ko-KR)는 번역으로 추가합니다. 한국 사용자만 생각한다면 한국어를 기본으로 해도 됩니다.
4. **앱 또는 게임**: 앱
5. **무료 또는 유료**: 무료 (한 번 무료로 게시하면 유료로 바꿀 수 없음)
6. 선언: 개발자 프로그램 정책, 미국 수출법 동의 → **앱 만들기**

개인 계정이면 Play Console 모바일 앱에서 기기 인증, 연락처 이메일·전화 인증이 먼저 끝나 있어야 합니다.

---

## 5. 앱 콘텐츠 (App content) 선언

**정책 및 프로그램 → 앱 콘텐츠**에서 하나씩 채웁니다. 일부 항목(접근성 API, 포그라운드 서비스)은 AAB를 한 번 올린 뒤에야 나타납니다(6장).

### 5-1. 개인정보처리방침 (Privacy policy)

- URL: `https://github.com/WWhaleFe/flockswitch-android/blob/main/PRIVACY.md`
- 조건: 공개 URL, 로그인 없이 열림, PDF가 아님. **`PRIVACY.md`를 푸시한 뒤에** 넣고, 브라우저 시크릿 창에서 열리는지 확인하세요.
- 내용은 코드에서 확인한 그대로입니다: Logitech 장치와만 USB·블루투스로 통신, Play 빌드는 인터넷 권한 없음, GitHub 빌드만 GitHub에서 새 버전 확인(끌 수 있음), 설정은 앱 전용 저장 공간(`config.json`, `devices.json`, `button-state.json`), Android 백업은 `config.json`만, 수집·공유 없음, 접근성 서비스가 보는 것.

### 5-2. 앱 액세스 권한 (App access)

- **모든 기능을 특별한 액세스 권한 없이 사용할 수 있음 (All functionality is available without special access)**
- 로그인·회원·결제가 없습니다. 실제 동작에는 Logitech 마우스·수신기가 필요하지만, 앱 액세스 항목은 로그인 같은 제한을 묻는 곳입니다. 심사에서 “기능을 확인할 수 없다”는 답이 오면 그때 “일부 기능 제한”을 고르고 다음을 안내로 넣으세요:
  > No login is needed. The app controls Logitech MX Master 3/4 mice and Logitech keyboards through a Logi Bolt or Unifying USB receiver (USB OTG) or Bluetooth. Without such a device the app shows "Plug in a Logitech receiver". The demo videos in the Accessibility API and Foreground service declarations show the features with the hardware.

### 5-3. 광고 (Ads)

- **아니요, 앱에 광고가 없습니다.**

### 5-4. 콘텐츠 등급 (Content rating)

1. 이메일: 개발자 연락 이메일 (예: feedback.wwhale@gmail.com)
2. 카테고리: **기타 모든 앱 유형 / 유틸리티·생산성 (All Other App Types / Utility, Productivity, Communication, or Other)** — 게임·소셜·뉴스·교육·엔터테인먼트가 아닌 항목
3. 질문은 모두 **아니요**:
   - 폭력, 공포, 성적 콘텐츠, 비속어, 약물·술·담배, 도박·모의 도박: 아니요
   - 사용자 간 상호작용·채팅, 사용자 제작 콘텐츠 공유: 아니요
   - 사용자의 현재 위치를 다른 사용자와 공유: 아니요
   - 디지털 상품 구매: 아니요
   - 웹 브라우저·검색 엔진 기능(제한 없는 인터넷 접근): 아니요
   - 앱이 주로 뉴스/교육 콘텐츠인가: 아니요
4. 예상 결과: 전체이용가 / IARC 3+ / PEGI 3 / ESRB Everyone / USK 0

### 5-5. 타겟층 (Target audience and content)

- 연령: **18세 이상** 권장 (16~17세를 함께 골라도 됨). **13세 미만은 고르지 마세요** — 고르면 가족 정책(Families)이 적용되어 추가 요구가 생깁니다.
- “앱이 어린이의 관심을 끌 수 있나요?”: **아니요**
- 스토어 등록정보에 어린이를 대상으로 한 표현이 없습니다.

### 5-6. 데이터 보안 (Data safety)

| 질문 | 답 |
|---|---|
| 앱에서 필수 사용자 데이터 유형을 수집하거나 공유하나요? (Does your app collect or share any of the required user data types?) | **아니요** |
| (위에서 아니요를 고르면 데이터 유형·암호화·삭제 요청 질문은 건너뜀) | — |
| 계정 생성 (Account creation) 항목이 나오면 | **앱에서 계정을 만들 수 없음 (My app does not allow users to create an account)** |
| 독립적인 보안 검토 (Independent security review) | 선택 안 함 |
| 결과 미리보기 | “수집된 데이터 없음 / 공유된 데이터 없음 (No data collected / No data shared)” |

근거 (심사에서 물으면 이렇게 설명):

- Play 빌드에는 `INTERNET` 권한이 없어 앱이 기기 밖으로 데이터를 보낼 수 없습니다.
- 접근성 서비스가 보는 키 상태(Control, Ctrl+Alt+L)와 포인터 위치, 블루투스·USB로 읽는 장치 이름·제품 ID는 **기기 안에서만 처리**되며, Play의 데이터 보안 기준에서 기기 밖으로 보내지 않는 데이터는 “수집”이 아닙니다.
- Android 자동 백업(Google 백업)은 사용자의 Google 계정 백업 설정에 따라 Android가 `config.json`을 백업하는 것이며, 개발자는 그 데이터에 접근할 수 없습니다. 개발자가 받는 데이터가 아니므로 “수집”으로 선언하지 않습니다. (보수적으로 가고 싶으면 `allowBackup`을 끄는 방법도 있지만, 재설치 후 설정 복원 기능이 없어지므로 권하지 않습니다.)
- “개발자에게” 메일 기능은 Play 빌드에 없습니다.

### 5-7. 그 밖의 선언

| 항목 | 답 |
|---|---|
| 광고 ID (Advertising ID) | **아니요**, 광고 ID를 사용하지 않음 (매니페스트에 `AD_ID` 권한 없음 확인) |
| 정부 앱 (Government apps) | **아니요** |
| 금융 기능 (Financial features) | **내 앱에 금융 기능이 없음** |
| 건강 (Health apps) | **건강 기능 없음** (모든 건강 항목 선택 안 함) |
| 뉴스 앱 (News apps) | **아니요** |

---

## 6. 민감한 권한과 API 선언

AAB를 어느 트랙(내부 테스트도 됨)에 처음 올리면 Play Console이 아래 선언을 요구합니다. 선언을 끝내야 검토에 보낼 수 있습니다.

### 6-1. 접근성 API (AccessibilityService) — 가장 중요, 거절 위험 있음

위치: **앱 콘텐츠 → 접근성 API (Accessibility API)** (또는 “민감한 앱 권한”)

**질문과 답**

- 앱이 접근성 도구인가요(장애가 있는 사용자를 돕는 것이 주 목적인가요)? → **아니요**. (매니페스트에 `android:isAccessibilityTool`을 넣지 않았으므로 기본값 false와 일치합니다. 접근성 도구라고 선언하면 안 됩니다.)
- 접근성 API로 개인정보나 민감한 사용자 데이터를 수집하거나 공유하나요? → **아니요** (기기 안에서만 처리, 인터넷 권한 없음)
- 앱 안의 공개·동의 → 1장에서 추가한 “접근성 서비스 사용 안내” 창

**붙여 넣을 설명 (영어, 양식에 넣는 용도)**

> flockswitch moves a Logitech mouse and keyboard together to another computer (their Easy-Switch channel) when the user presses a mouse button or rests the pointer at a screen edge. The accessibility service is optional and is used only for three features the user turns on:
> 1. Switching at the top and bottom screen edges: an accessibility overlay (TYPE_ACCESSIBILITY_OVERLAY) lets invisible, non-touchable strips sit over the status and navigation bars to notice the mouse pointer there. An ordinary overlay cannot cover those bars.
> 2. "Only while Control is held" and the Ctrl+Alt+L "Find the cursor" shortcut: key events of a hardware keyboard are filtered (FLAG_REQUEST_FILTER_KEY_EVENTS, requested only while one of these settings is on) to read only whether Control is held and to catch Ctrl+Alt+L. Every other key is passed on unchanged. Android offers no other way for an app to see a hardware keyboard's modifier keys system-wide.
> 3. Find the cursor (Android 14+): after the user asks for it, mouse motion events are requested for a single event to get the pointer's position and draw a highlight there.
> The service does not retrieve window content (canRetrieveWindowContent=false), receives events only for the app's own package, takes no actions on the user's behalf, stores nothing, and the app has no INTERNET permission. Before the user is sent to Android's accessibility settings, the app shows a disclosure of what the service accesses and how it is used, with "Agree" and "No thanks" buttons.

짧은 버전(글자 수 제한이 있는 칸용):

> Optional. Used only for (1) invisible strips over the status/navigation bars to detect the mouse pointer at the top and bottom screen edges, (2) reading only the Control key and Ctrl+Alt+L from a hardware keyboard (other keys pass through), (3) reading the pointer position once for "Find the cursor". No window content, no automation, nothing stored or sent; the app has no internet permission. An in-app disclosure with Agree / No thanks comes first.

**심사용 영상 (필수)** — YouTube “일부 공개(unlisted)” 또는 Google Drive 공유 링크. Play가 요구하는 장면을 끊지 말고 한 번에 찍으세요(화면 녹화 + 가능하면 손과 마우스가 보이는 카메라 영상):

1. 앱 아이콘을 눌러 flockswitch를 엽니다.
2. 일반 탭 → 권한 → 접근성 서비스 **설정**을 누릅니다 → “접근성 서비스 사용 안내” 창 전체를 천천히 스크롤해 보여 줍니다.
3. **동의 안 함**을 누릅니다 → 창이 닫히고 앱은 그대로 동작함을 보여 줍니다(예: 마우스 버튼으로 전환). 다시 **설정**을 눌러 안내가 다시 열리는 것을 보여 줍니다.
4. **동의** → **접근성 설정** 버튼 → Android 접근성 설정에서 flockswitch를 켜고 허용 → 앱으로 돌아와 “✓ 접근성 서비스가 켜져 있습니다” 확인.
5. 핵심 기능 시연: 마우스 찾기(Ctrl+Alt+L을 누르고 마우스를 움직이면 포인터 주위에 강조 표시), 그리고 위쪽 모서리에 채널을 정해 포인터를 위쪽 가장자리에 대면 전환 대기 효과가 나오고 마우스·키보드가 다른 컴퓨터로 넘어가는 장면.

영상과 앱이 일치해야 하므로 **Play 빌드(내부 테스트 트랙에서 설치한 것)**로 찍으세요. 영상은 영어 UI가 좋습니다(기기 언어 English).

**거절될 수 있는 이유와 대응**

- “핵심 기능이 아니다”: 이 세 기능은 선택 기능이므로 심사자가 접근성 없이도 앱이 동작한다고 볼 수 있습니다. 위 설명처럼 “Android에서 이 기능을 구현할 다른 API가 없다”는 점을 분명히 쓰세요.
- 키 이벤트 필터링은 키로거와 비슷해 보여 엄격히 봅니다. “Control 상태와 Ctrl+Alt+L만, 나머지는 그대로 통과, 저장·전송 없음, 인터넷 권한 없음”을 영상과 설명에서 일관되게 보여 주는 것이 중요합니다.
- 공개 창이 영상에 안 보이거나 동의 거부 흐름이 빠지면 거절됩니다.
- **대안(B안)**: 계속 거절되면 Play 빌드에서만 접근성 서비스를 빼는 방법이 있습니다(`src/play/AndroidManifest.xml`에서 `PointerService`를 `tools:node="remove"`로 빼고, 관련 안내를 Play 빌드에서 숨김). 이 경우 Play 버전에는 위·아래 모서리 전환, Control 키를 떼는 즉시 취소, 마우스 찾기가 없고, 왼쪽·오른쪽 모서리와 버튼 전환은 그대로입니다. 필요하면 요청해 주세요.

### 6-2. 포그라운드 서비스 (Foreground service)

위치: **앱 콘텐츠 → 포그라운드 서비스 권한 (Foreground service permissions)**

- 선언할 유형: **연결된 기기 (Connected device)** — `FOREGROUND_SERVICE_CONNECTED_DEVICE`, 서비스 `SwitchService`
- Play가 허용하는 용도 “Bluetooth, NFC, IR, USB 또는 네트워크 연결이 필요한 외부 기기와의 상호작용”에 정확히 해당합니다. Android 14 이상의 필수 조건(블루투스 권한 또는 USB 권한 요청)도 앱이 지킵니다.

**붙여 넣을 설명 (영어)**

> SwitchService keeps the HID++ connection to the user's Logitech USB receiver (Logi Bolt / Unifying) or Bluetooth Logitech devices open while "Switch with mouse buttons" is on. It listens for the mouse button the user assigned, the screen-edge trigger, the Quick Settings tile and the notification's channel buttons, and then immediately moves the grouped keyboard and the mouse to another computer's Easy-Switch channel. It must react within a fraction of a second to a button press on an external device, so it cannot be deferred or scheduled. The user starts it by turning switching on (or "Start when this device starts") and stops it by turning switching off; the ongoing notification shows the master mouse, this device's channel and buttons for the other channels. If the service were stopped, pressing the mouse button would do nothing.

- 사용자에게 미치는 영향(중단·연기 시): 위 마지막 문장.
- 영상: 앱에서 “마우스 버튼으로 전환”을 켬 → 알림 표시 → 마우스 버튼을 눌러 키보드·마우스가 다른 컴퓨터로 넘어감 → 알림의 채널 버튼으로 다시 전환. 접근성 영상과 같은 영상의 앞부분을 써도 됩니다.

### 6-3. 다른 앱 위에 표시 (SYSTEM_ALERT_WINDOW)

- Play Console에 별도 선언 양식은 없습니다. 사용자가 시스템 설정에서 직접 허용하는 특별 권한입니다.
- 앱 안 설명이 이미 있습니다: 화면 모서리 전환 칸의 안내(“보이지 않는 얇은 띠가 포인터를 감지… 입력은 가로채지 않습니다”)와 일반 탭 → 권한의 “화면 모서리 전환과 효과 표시에 필요합니다.” 앱을 열자마자 요구하지 않고, 사용자가 모서리에 채널을 정했을 때만 안내합니다. 거절 위험 낮음.
- 심사에서 물으면: “Used only for switching at the screen edge: invisible, non-touchable strips along the edges detect the hovering mouse pointer, and a countdown is drawn near the pointer while it waits. No input is intercepted and no screen content is read.”

### 6-4. 근처 기기 (BLUETOOTH_CONNECT), USB, 부팅 시 시작

- `BLUETOOTH_CONNECT`: 실행 중 요청하는 일반 권한, 선언 양식 없음. 일반 탭에서 “블루투스 장치”를 켤 때(또는 Android가 백그라운드 서비스에 요구할 때)만 묻습니다. `BLUETOOTH_SCAN`과 위치 권한이 없으므로 위치 관련 선언도 필요 없습니다.
- USB: 권한 선언이 아니라 수신기를 꽂을 때 Android가 묻는 장치별 허용입니다. `android.hardware.usb.host`는 `required="false"`라 USB 호스트가 없는 기기에도 설치됩니다(블루투스로 사용 가능).
- `RECEIVE_BOOT_COMPLETED`: 선언 없음. “기기가 켜지면 자동 시작”이 켜져 있을 때 서비스를 다시 시작합니다. `connectedDevice` 유형은 Android 15의 부팅 후 시작 제한 대상이 아닙니다.

### 6-5. Play 빌드에 없는 것 (확인 완료)

`REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `INTERNET`, `QUERY_ALL_PACKAGES`, 위치, 저장소, 연락처, 카메라, 마이크 권한은 Play 빌드에 없습니다. 자체 업데이트 코드와 GitHub 주소도 dex에 없습니다.

---

## 7. 스토어 등록정보 (Main store listing)

**성장 → 스토어 등록정보 → 기본 스토어 등록정보**. 기본 언어(en-US)를 먼저 채우고, **번역 관리 → 직접 번역 추가 → 한국어(ko-KR)**로 한국어를 채웁니다.

### 7-1. 문구

| 칸 | en-US | ko-KR |
|---|---|---|
| 앱 이름 (30자) | `listing/en-US/title.txt` | `listing/ko-KR/title.txt` |
| 간단한 설명 (80자) | `listing/en-US/short_description.txt` | `listing/ko-KR/short_description.txt` |
| 자세한 설명 (4,000자) | `listing/en-US/full_description.txt` | `listing/ko-KR/full_description.txt` |

- 파일 내용을 그대로 복사합니다. Play는 서식(굵게)을 지원하지 않으므로 ■, • 기호와 줄바꿈으로 구획을 나눴습니다.
- 앱 이름 후보: en-US `flockswitch: One-Button Switch`(30자). 더 짧게 하려면 `flockswitch` 하나만 써도 됩니다. ko-KR `flockswitch: 마우스·키보드 함께 전환`(26자).
- 다른 회사 상표(Logitech, MX Master, Easy-Switch, Logi Bolt, Unifying, Logi Options+)는 **앱 이름과 간단한 설명에 넣지 않았고**, 자세한 설명에서 호환 기기를 설명할 때만 쓰며 끝에 “Logitech과 관계없음”과 상표 고지를 두었습니다.
- 설명은 안드로이드 앱만 다룹니다. 다른 운영체제 버전이나 다른 배포처(GitHub 릴리스, 다른 스토어)는 언급하지 않았고, “오가는 다른 컴퓨터에서도 flockswitch가 실행 중이어야 돌아오는 전환이 된다”는 사실만 운영체제 이름 없이 적었습니다.
- 설명의 기능·권한 문구는 Play 빌드 기준(인터넷 권한 없음, 업데이트·개발자에게 메일 기능 없음)입니다.

### 7-2. 그래픽

| 칸 | 파일 | 규격 |
|---|---|---|
| 앱 아이콘 | `listing/icon-512-fullbleed.png` (권장) | 512×512, 32비트 PNG(투명도 포함), 1MB 이하 |
| 그래픽 이미지 (Feature graphic) | `listing/feature-graphic-1024x500.png` | 1024×500, 24비트 PNG(투명도 없음) |
| 휴대전화 스크린샷 | `listing/SCREENSHOTS.md` 계획대로 캡처 | 2~8장, 9:16 권장 |
| 7인치·10인치 태블릿 스크린샷 | 폴더블 펼친 화면 | 각 최대 8장 |

아이콘 두 가지:
- `icon-512.png`: `assets/iconimg.png`(1024×1024)를 512×512로 줄이기만 한 것. 원본은 둥근 사각형 둘레에 투명 여백과 그림자가 있어서, Play가 아이콘을 둥근 사각형으로 자르고 자체 그림자를 넣으면 **작게, 테두리 안에 또 테두리가 있는 모양**으로 보입니다. Play 아이콘 지침(“꽉 찬 정사각형, 그림자 넣지 않기”)과도 맞지 않습니다.
- `icon-512-fullbleed.png`: 같은 원본의 둥근 사각형 부분(1024 기준 200~824px)만 잘라 512×512로 줄인 것. 그림자와 여백이 없고, 모서리의 투명한 부분은 Play의 둥근 마스크(반지름 약 20%) 밖이라 보이지 않습니다. **이것을 권장합니다.** 원본 파일은 바꾸지 않았습니다.

그래픽 이미지는 아이콘과 같은 파랑→보라 그라데이션 배경에 아이콘과 “flockswitch” 글자(Avenir Next Demi Bold, 흰색)만 넣었습니다. 글자와 아이콘은 가운데 영역에 있어 Play가 가장자리를 잘라도 안전합니다.

### 7-3. 분류와 연락처

- 앱 카테고리: **도구 (Tools)** (생산성(Productivity)도 가능)
- 태그: 도구·생산성 관련 태그 중 해당하는 것 (선택)
- 이메일: `feedback.wwhale@gmail.com` (스토어에 공개됨)
- 웹사이트: 비워 두거나 개인정보처리방침이 있는 저장소 주소. 저장소에 APK 다운로드가 있으므로, 스토어 밖 설치로 유도한다는 오해를 피하려면 비워 두는 것이 무난합니다(결정 필요).
- 전화번호: 비워 둠

### 7-4. 출시 노트 (What's new)

트랙에 출시를 만들 때 **출시 노트** 칸에 언어별로 붙여 넣습니다:

```
<en-US>
(listing/en-US/changelogs/13.txt 내용)
</en-US>
<ko-KR>
(listing/ko-KR/changelogs/13.txt 내용)
</ko-KR>
```

---

## 8. 테스트 트랙: 새 개인 계정의 비공개 테스트 요건

2023-11-13 이후에 만든 **개인** 개발자 계정은 프로덕션에 바로 출시할 수 없습니다. 먼저 **비공개 테스트(Closed testing)에 테스터 12명 이상이 14일 연속 참여(옵트인)**해야 하고, 그다음 **프로덕션 액세스 신청(Apply for production)** 설문을 내고 승인을 받아야 합니다.

### 8-1. 내부 테스트 (Internal testing) — 먼저 확인용

1. **테스트 및 출시 → 테스트 → 내부 테스트 → 테스터** 탭에서 이메일 목록 만들기(본인 Google 계정 포함, 최대 100명).
2. **새 버전 만들기** → 앱 서명 키 선택(3장) → AAB 업로드 → 출시 노트 → 저장 → 검토 → 출시.
3. 이때 접근성 API·포그라운드 서비스 선언이 요구되면 6장대로 채웁니다.
4. 테스터 링크로 휴대전화에 Play 버전을 설치해 확인:
   - GitHub 버전이 설치돼 있으면 서명(3장에서 A를 고른 경우)이 달라 설치가 막힙니다. 설정을 **설정 백업 → 저장**으로 파일에 저장해 두고 GitHub 버전을 지운 뒤 설치하세요.
   - 일반 탭에 업데이트·개발자에게 칸이 없는지, 접근성 “설정”을 누르면 공개·동의 창이 먼저 나오는지, ‘제한된 설정’ 없이 접근성 서비스를 켤 수 있는지 확인.
   - 이 설치본으로 6장의 심사용 영상을 찍습니다.

### 8-2. 비공개 테스트 (Closed testing) — 14일

1. **비공개 테스트 → 트랙 만들기(또는 기본 “Closed testing - Alpha”) → 테스터**: 이메일 목록이나 **Google 그룹**(관리하기 편함)을 추가합니다. 탈락에 대비해 **15명 이상** 모으세요.
2. 내부 테스트의 같은 버전을 **비공개 테스트로 승격**(또는 같은 AAB로 새 버전) → 검토에 보냄. 첫 검토는 며칠 걸릴 수 있습니다.
3. 승인되면 **옵트인 링크**를 테스터에게 보냅니다. 테스터는 링크에서 참여 → Play에서 설치 → **14일 동안 참여를 유지**(나가거나 앱을 바로 지우지 않기).
4. 테스터에게 부탁할 것: 앱을 몇 번 열어 보기, 가능하면 Logitech 장치로 써 보고 의견 보내기. Logitech 장치가 없는 테스터도 참여 자체는 됩니다.
5. 14일 동안 의견을 반영해 업데이트를 한 번 이상 올리면 프로덕션 신청 설문에 쓰기 좋습니다(versionCode 14 이상).
6. **대시보드 → 프로덕션 액세스 신청**: 테스터를 어떻게 모았는지, 받은 의견과 고친 점, 앱의 대상 사용자, 출시 준비가 됐다고 판단한 근거를 묻습니다. 예:
   - 대상: Logitech MX Master 마우스와 Logitech 키보드를 컴퓨터와 안드로이드 태블릿 사이에서 함께 쓰는 사용자
   - 테스터: 지인과 기존 GitHub 사용자에게 Google 그룹으로 모집
   - 의견과 변경: 테스트 기간에 받은 실제 의견을 적기
7. 승인되면 **프로덕션 → 새 버전 만들기**(비공개 테스트 버전 승격) → 국가/지역 선택 → 검토 → 출시.

---

## 9. 전체 순서 (체크리스트)

1. [ ] 1장의 코드 변경, `PRIVACY.md`, `packaging/play/` 커밋·푸시 (태그는 필요 없음)
2. [ ] 개인정보처리방침 URL이 브라우저에서 열리는지 확인
3. [ ] (권장) 커밋한 상태로 `./gradlew -PplayStore bundleRelease` 다시 실행
4. [ ] Play Console 계정·본인 인증 완료
5. [ ] 앱 만들기 (4장): 이름, 기본 언어 en-US, 앱, 무료
6. [ ] 앱 콘텐츠 (5장): 개인정보처리방침, 앱 액세스, 광고, 콘텐츠 등급, 타겟층, 데이터 보안, 광고 ID, 정부·금융·건강·뉴스
7. [ ] 스토어 등록정보 (7장): en-US와 ko-KR 문구, 아이콘, 그래픽 이미지, 스크린샷(캡처 후), 카테고리, 이메일
8. [ ] 앱 서명 키 결정 (3장: A 또는 B)
9. [ ] 내부 테스트에 AAB 업로드 → 접근성 API·포그라운드 서비스 선언(6장) → 출시
10. [ ] 내부 테스트 설치본 확인 + 심사용 영상 촬영·업로드 → 선언에 링크 넣기
11. [ ] 비공개 테스트 트랙 + 테스터 12명 이상(권장 15명) → 검토 → 옵트인 링크 배포
12. [ ] 14일 유지, 의견 반영 업데이트(versionCode 14+)
13. [ ] 프로덕션 액세스 신청 → 승인
14. [ ] 프로덕션 출시 (국가 선택, 출시 노트)

---

## 10. 개발자가 직접 하거나 결정할 것

- **앱 서명 키**: A(Google 생성) 또는 B(기존 릴리스 키 업로드). 3장.
- **기본 언어**: en-US(권장) 또는 ko-KR.
- **앱 이름**: 긴 이름(`flockswitch: One-Button Switch` / `flockswitch: 마우스·키보드 함께 전환`) 또는 `flockswitch`만.
- **아이콘**: `icon-512-fullbleed.png`(권장) 또는 `icon-512.png`.
- **웹사이트 칸**: 비움 또는 저장소 주소.
- **스크린샷 캡처**: `listing/SCREENSHOTS.md`.
- **심사용 영상 촬영**: 접근성 API(공개·동의 거부·동의·기능 시연), 포그라운드 서비스.
- **테스터 12명 이상 모집**과 14일 유지.
- **1장 코드 변경 검토와 커밋**. 공개 창 문구(`a11y_disclosure_text`)는 실제 기기에서 한 번 읽어 보세요. 길어서 대화상자 안에서 스크롤됩니다.

## 11. 다음 출시부터

1. `app/build.gradle.kts`의 `versionCode`(14, 15, …)와 `versionName`을 올리고 CHANGELOG를 씁니다(GitHub와 같은 번호 사용).
2. `./gradlew -PplayStore bundleRelease` → Play Console에서 트랙에 새 버전 → 출시 노트(`changelogs/<versionCode>.txt`를 만들어 두면 편함) → 검토.
3. 접근성 서비스가 접근하는 것이 바뀌면(새 키, 새 이벤트 등) 공개 창 문구와 접근성 선언, 개인정보처리방침을 함께 고치고 선언을 다시 제출해야 합니다.
