# Knowra API — 향후 작업 목록

> 진행하면서 나중에 해도 되는 작업들을 모아둔 문서.
> 완료 시 ✅로 변경.

---

## 알고리즘

| # | 항목 | 설명 | 선행 조건 |
|---|------|------|-----------|
| ✅ A-1 | 커뮤니티 탐색 C1 (관심사일치점수) | LIKE/COMMENT/SCRAP 시 태그별 interest score 업데이트 + 커뮤니티 태그 집계로 C1 계산 | — |
| ✅ A-2 | 커뮤니티 관계점수 — 최근 방문 | getCommunity() 호출 시 ACTION_VIEW 로그 → 탐색 API에서 7일 내 방문 여부 조회 후 관계점수 +10 반영 | — |
| ✅ A-3 | 커뮤니티 품질점수 — 활동유저/신규팔로우 | 7일 활동유저 수 × 4 (countDistinct), 신규가입자 수 × 5 (TBL_COMM_MBR) | — |
| ✅ A-4 | 게시글 개인화 피드 | 유저 태그/커뮤니티/작성자 점수 실시간 집계 + 피드 점수 계산 구현 | A-1 |
| A-5 | 핫글 슬라이더 | 최근 1시간 반응속도 기반 실시간 급상승 게시글 API | Redis 카운터 |

---

## Redis

| # | 항목 | 설명 | 우선순위 |
|---|------|------|----------|
| R-1 | 일반 게시글 조회수 | 커뮤니티 게시글과 동일하게 Redis INCR + DB 동기화 스케줄러 적용 | 중간 |
| R-2 | JWT 리프레시 토큰 저장 + 블랙리스트 | `refresh:{userSn}`, `blacklist:{token}` — 로그아웃 시 토큰 즉시 무효화 | 높음 |
| R-3 | 중복 조회 방지 | `viewed:{userSn}:{postSn}` TTL 30분 — 조회수 어뷰징 방지 | 중간 |
| R-4 | 커뮤니티 탐색 품질 통계 캐싱 | `explore:quality` TTL 10분 — 7일 집계 쿼리 반복 호출 부하 감소 | 낮음 |
| R-5 | 핫글 실시간 반응 카운터 | Sorted Set으로 1시간 윈도우 관리 | A-5 이후 |

---

## 기능

| # | 항목 | 설명 |
|---|------|------|
| F-1 | 커뮤니티 탐색 카테고리 탭 필터 | 맞춤/인기 섹션에 ctgrSn 필터 추가 여부 — UI 구조 확정 후 결정 |
| F-2 | 커뮤니티 게시글 조회 액션로그 | 현재 일반 게시글 VIEW만 기록 중 — 커뮤니티 게시글 VIEW도 로그 추가 필요 여부 확인 |
| F-3 | 알림 기능 | 팔로우/댓글/좋아요 알림 — Redis Pub/Sub 또는 SSE |

---

## DDL / 스키마

| # | 항목 | 설명 |
|---|------|------|
| D-1 | TBL_USER DDL 프로필 사진 컬럼 수정 | `ATCH_FILE_SN BIGINT` → `ATCH_FILE_SN BIGINT (FK → KNOWRA_COM.TBL_COM_FILE)` 반영 |
| D-2 | TBL_COM_CATEGORY DDL 추가 | 엔티티는 생성됨, all_DDL.sql에 누락 확인 후 추가 |

---

## 운영 배포 전 필수

| # | 항목 | 설명 |
|---|------|------|
| P-1 | CORS 출처 제한 | `SecurityConfig.corsConfigurationSource()` — `allowedOriginPatterns("*")` → 프론트 도메인으로 변경 |
| P-2 | JWT secret 환경변수 분리 | application.properties의 JWT secret을 환경변수/Vault로 이동 |
