# G2 번역 읽기 · G2 Translate Reader

Even 앱의 한국어 → 영어·일본어 번역문을 읽어 휴대폰 스피커로 들려주는 Android 보조 앱입니다. 화면에서 글자를 인식하고, 휴대폰에 설치된 오프라인 음성으로 재생합니다.

An experimental Android companion that reads Korean-to-English/Japanese translations from the Even app using on-device OCR and offline text-to-speech.

[설치 안내](https://g2.sangsang.tech/) · [APK 다운로드](https://github.com/sangsangtech/g2-translate-reader/releases) · [화면 미리보기](https://g2.sangsang.tech/preview/)

## 어떤 앱인가요?

상대방의 말은 Even 기본 번역으로 G2 안경에서 보고, 내가 한국어로 말한 내용의 번역은 휴대폰에서 들려주는 대화를 돕습니다. 이 앱은 자체 번역기나 G2 Bluetooth 연결 기능을 제공하지 않습니다. Even 화면의 번역 결과를 읽는 역할을 합니다.

- 한국어 → 영어 / 한국어 → 일본어 읽기
- 오프라인 목소리·음성 엔진 선택, 속도와 음높이 조절
- 새 번역문 자동 읽기, 중복 방지, 연속 문장 대기열
- 대화 중 화면 켜두기와 낮은 밝기 설정
- 별도 API 키나 이 앱의 사용료 없음

AI 코딩 도구와 함께 만든 개인 프로젝트입니다. Even Realities가 제작하거나 승인한 공식 앱이 아닙니다.

## 설치와 사용

Android 15 이상, Even 앱과 G2, 선택한 언어의 오프라인 TTS 음성이 필요합니다.

1. Releases에서 APK를 내려받아 설치합니다. 현재 0.2.2는 **디버그 서명된 시험 버전**입니다.
2. 언어를 한국어 ↔ 영어 또는 한국어 ↔ 일본어로 선택하고 `목소리 미리 듣기`로 확인합니다. 음성이 없으면 설정에서 음성 데이터를 설치합니다.
3. Even 앱에서도 같은 언어 조합을 선택합니다. 안경에는 상대 언어 → 한국어를 표시하고, 휴대폰에는 내 말의 번역 방향인 `KO→EN` 또는 `KO→JA`를 표시합니다.
4. `Even 화면 연결하기`를 누르고 Android 공유 창에서 **앱 하나 → Even**을 선택합니다.
5. 최근 번역과 수동 읽기를 확인한 뒤 `자동으로 읽기`를 켭니다. 연결 당시의 기존 문장은 건너뛰고 새 문장부터 읽습니다.

언어를 바꾸려면 읽기를 종료한 뒤 변경합니다. 이 앱의 언어 선택은 Even 앱의 설정을 바꾸지 않습니다.

## 화면 유지와 음성

설정의 `화면 켜두기`는 ‘다른 앱 위에 표시’ 권한을 사용합니다. 캡처 중 작은 버튼으로 밝기를 전환할 수 있으며, 밝기는 5–50% 범위에서 설정합니다. 종료하면 평소 화면 동작으로 돌아갑니다. 시스템 밝기나 화면 시간 제한 자체는 변경하지 않습니다.

**화면을 실제로 끄거나 잠근 상태에서는 동작하지 않습니다.** 전원 버튼으로 잠그면 캡처가 종료됩니다. 낮은 밝기로 켜두는 방식이며 배터리 절감량은 측정하지 않았습니다.

목소리의 자연스러움은 설치한 TTS 엔진과 음성에 따라 다릅니다. 속도는 70–130%, 음높이는 80–120%로 조절할 수 있습니다.

## 개인정보와 권한

- 캡처 이미지와 인식한 번역문은 메모리에서 처리하며 앱이 파일이나 서버에 저장하지 않습니다.
- 앱 자체에는 인터넷 권한, API 키, 광고, 클라우드 음성 합성이 없습니다. 한국어·일본어 OCR 모델을 앱에 포함합니다.
- 화면 캡처에는 Android의 사용자 승인과 실행 중 알림을 사용합니다. 접근성 서비스는 사용하지 않습니다.
- ‘다른 앱 위에 표시’ 권한은 선택 기능인 화면 유지에 사용합니다.
- 음성 데이터 최초 설치에는 TTS 제공업체의 다운로드가 필요할 수 있습니다. Even 번역의 연결·이용 조건과 TTS 제공업체의 데이터 처리는 각각 별도입니다.
- 앱을 다시 시작해도 캡처를 자동 재개하지 않습니다.

## 알려진 제약

OCR 방식이라 화면 구성 변경, 스크롤, 줄바꿈, 인식 오류로 문장을 놓치거나 다시 읽을 수 있습니다. 일본어의 한자만 있는 문장은 판별이 어려울 수 있습니다.

번역문이 잠시 안정된 뒤 읽으므로 지연이 있습니다. 같은 문구는 한 세션에서 한 번만 자동으로 읽습니다. 대기열은 최대 64문장이며, 한도를 넘은 뒤 화면에서 사라진 문장은 보장하지 않습니다. TTS 엔진이 재생을 수락한 뒤 발생한 실패는 자동 재시도하지 않습니다.

Even의 마이크를 제어하지 않으므로 스피커 소리가 다시 번역에 들어갈 수 있습니다. 다른 화면 공유와 동시에 사용하면 캡처가 중단될 수 있습니다.

## 빌드

- JDK 17 이상 (개발 환경: JDK 21)
- Android SDK Platform 35 / Build Tools 35.0.0
- Gradle 8.11.1 (Wrapper 포함), Android Gradle Plugin 8.9.2

Android Studio에서 열어 SDK를 지정하거나, 로컬 `local.properties`에 `sdk.dir`을 설정합니다. 개인 경로가 들어가는 이 파일은 커밋하지 않습니다.

macOS / Linux:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

APK 출력: `app/build/outputs/apk/debug/app-debug.apk`

로컬 디버그 빌드는 각 개발자의 디버그 키로 서명됩니다. 배포 APK와 서명이 다르면 기존 앱 위에 설치할 수 없습니다. 서명 키는 저장소에 포함하지 않습니다.

0.2.2 검증: 문장 처리·OCR 결과 조합·첫 실행 및 탭 전환 테스트 29개 통과, Android lint 오류 없음, APK 빌드 성공. 자동화 검증이 실제 기기의 OCR·음성 품질을 보장하지는 않습니다.

## 구성

- `app/`: Android 앱과 테스트
- `TranslationTracker`: 번역 방향, 문장 안정화, 중복 및 대기열 처리
- `CaptureService`, `OcrRows`: 화면 캡처와 OCR 결과 처리
- `LocalVoice`: 오프라인 TTS
- `ReadingScreen`: 화면 유지와 밝기 오버레이
- `MainActivity`: 통역·설정 화면
- `preview.html`, `icon-preview.html`, `site-index.html`: 웹 미리보기와 설치 안내

웹 미리보기는 시연용 HTML이며 실제 Android 앱의 상태를 가져오지 않습니다. Node.js가 있다면 APK 빌드 후 아래 명령으로 정적 설치 사이트를 만들 수 있습니다.

```sh
node build-site.cjs
```

출력은 `build/public-site/`입니다. HTTPS 다운로드 링크를 인자로 전달하면 APK를 제외한 사이트가 `build/pages-site/`에 생성됩니다.

## 라이선스

이 프로젝트의 코드는 [MIT License](LICENSE)로 공개합니다. 외부 라이브러리·모델·도구에는 각 제공자의 라이선스와 조건이 적용됩니다. Even Realities의 상표나 공식 로고에 대한 권리를 부여하지 않습니다.
