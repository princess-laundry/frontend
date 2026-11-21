# 중간 발표용 앱 화면

2026-11-21, frontend `feat/android-posture-v2`, 커밋 `88ec328` 실행 결과입니다.
Pixel 9 / Android API 35 에뮬레이터에서 최신 debug APK를 실행하고 원본 PNG(1080 × 2424)를 저장했습니다.

| 파일 | 내용 | 발표 위치 |
| --- | --- | --- |
| 01-home.png | 홈, 오늘 기록, 시작 버튼 | 사용자 흐름 |
| 02-setup-permission.png | 세팅 가이드와 카메라 권한 안내 | 세팅 화면 |
| 03-demo-normal.png | 데모의 바른 자세 상태 | 공부 화면 |
| 04-demo-forward-head.png | 데모의 거북목 상태 | 공부 화면 |
| 05-demo-away.png | 데모의 자리비움 상태 | 쉬기와 자리비움 비교 |
| 06-demo-paused.png | 데모의 일시정지 상태 | 쉬기와 자리비움 비교 |
| 07-demo-report.png | 데모 세션의 시간, 자세 비율, 타임라인 | 세션 리포트 |
| 08-statistics.png | 날짜별 기록 달력 | 일별 통계 |
| 09-settings.png | 알림, 스켈레톤, 밝기, 시작 음성 설정 | 설정 |
| 10-server-connected.png | 실제 Docker 서버 연결 및 동기화 상태 | 앱과 서버 연동 |
| 11-android-studio-running.jpg | Android Studio의 Running Devices 안에서 앱을 실행한 화면 | 실행 환경 증빙 |

## 검증 범위

- 공부 상태별 화면과 리포트는 앱의 데모 모드입니다. 자세 상태는 직접 선택했습니다. 실제 카메라 인식 결과나 정확도 평가 자료가 아닙니다.
- 데모 세션은 실제 통계와 서버 업로드에서 제외됩니다. 달력의 빈 상태는 정상이며, 발표용 숫자를 임의로 넣지 않았습니다.
- 세팅 화면은 카메라 권한 안내 상태입니다. 실제 촬영 화면이나 구도 인식 성공 화면이 아닙니다.
- Docker Compose의 DB, 서버, Nginx, API 문서, HTTPS 터널을 실행했습니다. health 응답은 database=ok, status=ok입니다.
- 에뮬레이터 앱의 기기 인증(POST /api/v1/auth/device)과 기준값 조회(GET /api/v1/config)가 HTTP 200으로 성공했습니다. 앱 캐시에서도 version=2, leanDegrees=15, stableMs=2000, analysisFps=5를 확인했습니다.
- 이번 캡처 작업에서는 새 실제 세션을 서버에 업로드하지 않았습니다. 기존 실기기 세션 id=4는 threshold_version=2, study_sec=34, normal_sec=7, fhp_sec=27로 유지되어 있습니다.
- 실기기 처리 지연 p95와 배터리 소모는 이번 작업에서 측정하지 않았습니다. 데모 모드는 SideFocusLatency 로그를 생성하지 않습니다.

현재 앱에 연결한 임시 서버 주소: https://thunder-saying-suspected-bargain.trycloudflare.com
터널을 재시작하면 주소가 변경될 수 있습니다.

프론트 테스트 39개 통과, Android Lint 오류 0개, debug APK 빌드는 앞선 측정 기능 작업에서 확인했습니다. 커밋 `88ec328`은 origin에 push했습니다.
