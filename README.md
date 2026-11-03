# 사이드포커스 Android

측면 자세 인식 기반 집중 관리 앱의 Kotlin / Jetpack Compose 클라이언트입니다.
첨부 기획서의 Android 앱 범위를 구현하며, AI 연구 및 C++ 서버와의 연결 지점을 분리합니다.

## 개발 환경

- JDK 21, Android SDK 35, Android 8.0(API 26) 이상
- Android Studio에서 이 폴더를 열고 SDK 설치 후 실행
- `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
- Windows에서는 `gradlew.bat` 사용

프레임과 랜드마크는 기기 메모리에서만 사용합니다. 원본 영상 저장 권한이나 업로드 API는 없습니다.
라이브러리는 재현 가능한 빌드를 위해 버전을 고정합니다.
