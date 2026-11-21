# 사이드포커스 Android

측면 자세 인식 기반 집중 관리 앱의 Kotlin / Jetpack Compose 클라이언트입니다.
기획서의 Android 앱 범위를 기준으로 만들었습니다. AI의 초기 규칙은 교체 가능한 `core` 모듈로 분리했습니다.

## 개발 환경

- JDK 21, Android SDK 35, Android 8.0(API 26) 이상
- Android Studio에서 이 폴더를 열고 SDK 설치 후 실행
- `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
- Windows에서는 `gradlew.bat` 사용

SDK가 없는 Windows PC에서는 `./tools/bootstrap-android.ps1 -InstallSdk`로 프로젝트의 `.tools` 폴더에 SDK를 설치할 수 있습니다.
이 저장소에는 실행에 필요한 MediaPipe Lite 모델과 Gradle Wrapper를 포함합니다.
일부 관리형 Windows 호스트의 Java 내부 소켓 오류가 발생하면 `./tools/verify.ps1 -Bootstrap`을 사용하세요.

APK: `app/build/outputs/apk/debug/app-debug.apk`

## 구현 기능

1. 홈: 오늘의 공부 시간, 바른 자세 비율, 최근 세션, 카메라 없는 데모
2. 세팅: 권한 안내, 좌우 카메라 전환, 측면 가이드, 흔들림 감지, 3초 구도 안정 + 3초 카운트다운 자동 시작
3. 공부: CameraX + MediaPipe 온디바이스 분석, 기본 5fps, 안정화된 자세 표시, 자리비움 1fps, 일시정지/재개/종료
4. 기록: Room에 상태 구간과 시간 저장, 5초마다 임시 저장, 프로세스 중단 시 마지막 저장 시점까지 복구
5. 리포트: 시간·자세 비율·알림 횟수·상태 타임라인, 기록 상세와 로컬 삭제
6. 일별 통계: 한국 시간 기준 달력, 바른 자세 비율 색 농도, 날짜별 세션
7. 설정: 소리/진동/끄기, 스켈레톤 모드, 저밝기, 한국어 시작 음성, 전체 데이터 삭제
8. 서버 연결: HTTPS 기기 인증, 암호화 토큰, 설정 조회, 세션 업로드, WorkManager 재시도, 서버 사용자 삭제

데모는 항상 별도 표시되며 실제 통계와 업로드에서 제외됩니다. 앱을 백그라운드로 보내면 카메라를 중지하고 일시정지합니다. 돌아온 뒤 사용자가 이어하기를 누릅니다.

## 모듈

- `core`: Android에 의존하지 않는 자세 안정화·세션 집계·일별 통계·업로드 DTO, JVM 단위 테스트
- `app/camera`: CameraX 수명주기, MediaPipe 프레임 변환, 센서, 스켈레톤
- `app/data`: Room 세션 저장소, 영구 설정
- `app/sync`: Retrofit 계약, Keystore 토큰, WorkManager 업로드
- `app/ui`: Compose 홈·세팅·세션·리포트·기록·설정

## 서버 담당자와 확인할 것

앱은 서버 주소 없이 사용할 수 있습니다. 설정의 **개발용 서버 연결**에 HTTPS 기본 주소를 입력하면 인증·설정 API를 확인한 뒤 저장된 실제 기록을 전송합니다.

[API 계약과 미확정 필드](docs/backend-contract.md)를 먼저 확인하세요. 백엔드가 제공되지 않아 실제 서버와의 연동은 검증하지 않았습니다. 현재 통계·기록 조회는 Room을 기준으로 하며, 서버 기록의 다운로드/복원은 포함하지 않습니다.

## 검증

- `core` 단위 테스트: 공부/휴식 분리, 시간 역행, 날짜 경계, 가중 비율, 안정화, 구도 카운트다운, API 직렬화
- MockWebServer 테스트: 401 재발급 한도, 멱등 요청, 200 중복 응답, 400/503 분기, 204 삭제
- Robolectric UI 테스트: 홈 → 기록 → 설정 → 권한 안내 → 데모 → 일시정지/재개 → 종료/저장/리포트
- UI 테스트 이미지: `app/build/screenshots/`
- Android Lint 및 debug APK 빌드

[중간 발표용 화면과 서버 연결 확인 결과](docs/presentation/2026-11-21/README.md)를 참고하세요.

[실기기 확인 항목](docs/device-validation.md)에 카메라·음성·진동·성능 검증 방법을 정리했습니다. 백엔드 V4의 v2 설정은 골반→귀 기울기 15도와 안정화 2초를 제공합니다. 고개 숙임은 앱의 기본값 42도를 사용합니다. 구도가 안정되면 개인 기준 측정 없이 세션이 시작됩니다.

프레임과 랜드마크는 기기 메모리에서만 사용합니다. 원본 영상 저장 권한이나 업로드 API는 없습니다.
라이브러리는 재현 가능한 빌드를 위해 버전을 고정합니다.
