# Android / 백엔드 연결 계약

기획서의 `/api/v1` 경로와 세션 업로드 예시를 반영했습니다. 실제 서버는 제공되지 않았으므로 아래 응답 필드는 백엔드 담당자와 합의할 초기 계약입니다. 기획서 속 미정 필드를 확정된 사실로 취급하지 않습니다.

## 공통

- 기본 주소: `https://host/api/v1/`. 앱에는 기본 호스트 또는 이 경로를 입력합니다.
- JSON UTF-8, 인증은 `Authorization: Bearer <JWT>`
- 시각은 한국 시간의 ISO 8601 `+09:00`, 구간은 세션 시작 기준 ms
- UUID는 앱에서 생성합니다. 영상·이미지·좌표는 API 모델에 없습니다.
- HTTPS 인증서 검증을 유지하고 다른 주소로의 자동 리다이렉트는 따르지 않습니다.

## 1. 설치 인증

`POST /auth/device`

```json
{"device_uuid":"무작위 설치 UUID"}
```

```json
{"access_token":"JWT"}
```

**합의 필요:** 기획서는 요청·응답의 정확한 키를 지정하지 않습니다. 이 앱은 `device_uuid`와 `access_token`을 사용합니다. 로그인 없이 동일 UUID로 재발급할 수 있어야 합니다.

## 2. 판정 설정

`GET /config`

```json
{"version":1,"t1_ratio":0.35,"t2_angle":20.0,"stable_sec":2.0,"analysis_fps":5}
```

**합의 필요:** 최상위 필드 이름과 구조. 앱은 위 평탄한 응답을 사용합니다. 버전 1의 초기값으로 만든 오프라인 세션도 서버가 수용해야 합니다. 활성 세션의 설정은 시작할 때 고정하고, 새 설정은 다음 세션부터 적용합니다.

## 3. 세션 업로드

`POST /sessions`

```json
{
  "client_session_id":"UUID",
  "started_at":"2026-11-03T14:00:00+09:00",
  "ended_at":"2026-11-03T14:01:00+09:00",
  "threshold_version":1,
  "app_version":"0.1.0",
  "summary":{"study_sec":40,"away_sec":10,"normal_sec":30,"fhp_sec":5,"lean_sec":5,"alert_count":1},
  "segments":[
    {"state":"NORMAL","start_ms":0,"end_ms":30000},
    {"state":"FHP","start_ms":30000,"end_ms":35000},
    {"state":"LEAN","start_ms":35000,"end_ms":40000},
    {"state":"AWAY","start_ms":40000,"end_ms":50000}
  ]
}
```

신규는 201, 동일 `client_session_id`의 재전송은 200. 두 응답 모두 `{"session_id":1024}` 필수입니다. 서버의 추가 응답 필드는 무시합니다.

**합의 필요:** 일시정지·인식 확인 시간은 로컬에만 저장하므로 서버 구간 사이에 빈 시간이 있을 수 있습니다. 상태는 기획서대로 `NORMAL/FHP/LEAN/AWAY`만 전송합니다. 공부 시간은 자세별 시간을 초로 내린 값의 합이며, 전체 벽시계 시간과 다를 수 있습니다. `avg_ratio/avg_angle`은 선택값이며 현재 전송하지 않습니다.

400 등 영구 오류는 `ERROR`로 보존하고 자동 반복하지 않습니다. 설정의 '지금 동기화'로 명시적으로 재시도할 수 있습니다. 408·429·5xx·연결 오류는 지수 백오프로 재시도합니다. 401은 토큰을 한 번 재발급한 뒤 같은 UUID·본문으로 한 번만 재전송합니다.

## 4. 본인 데이터 삭제

`DELETE /users/me`, 성공 응답 200 또는 204.

서버 삭제 성공 이후 토큰·기기 기록·설정을 초기화하고 연결을 해제합니다. 실패하면 로컬 기록을 보존합니다. 기기에서만 삭제하는 기능은 서버 삭제를 호출하지 않습니다.

## 조회 범위

MVP 화면의 목록·상세·통계는 Room에서 조회합니다. 서버의 `GET /sessions`, `GET /sessions/{id}`, `GET /stats/daily` 다운로드와 복원은 별도의 응답 명세가 정해진 뒤 추가할 범위입니다. 주·월 통계, 설정 서버 백업, 관리자 CSV는 이 Android 구현에 포함하지 않습니다.
