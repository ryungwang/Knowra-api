# 사이트 운영 역할 (관리자 · 검수자)

> V66 부터 역할은 DB(`knowra_user.tbl_user.site_role`)에 있다. 예전의 「1번 회원 = 관리자」 규칙은 없어졌다 —
> 마이그레이션이 1번 회원을 ADMIN 으로 옮겨 둔다.

| 역할 | 할 수 있는 것 |
|---|---|
| `USER` | 보통 회원 |
| `MOD` 검수자 | 신고 큐 · 이의 제기 · 글·댓글 삭제 · DM 가리기 · **보통 회원** 정지 · 관리 기록 보기 · 아동 안전 신고 알림 받기 |
| `ADMIN` 관리자 | 위 전부 + 역할 지정 · 사이트 공지 · 금칙어 · 오류 기록 · 저작권 신고 · 콘텐츠 생성 · 주간 소식 바로 보내기 |

## 어디서 정하나
- 관리자 콘솔 › 사용자 › 이름 옆 역할 선택. 관리자만 보인다.
- 자기 역할은 바꿀 수 없다 — 마지막 관리자가 스스로 내려 아무도 관리할 수 없게 되는 일을 막는다.
- 운영진(MOD·ADMIN)은 정지할 수 없다 — 관리자가 역할을 먼저 내린 뒤 정지한다.
- 바꾼 내용은 관리 기록(`USER_ROLE`)에 남는다.

## 언제 반영되나
- 토큰의 `role` 클레임이 역할을 싣는다(`JwtProvider`). ADMIN 토큰은 `ROLE_ADMIN`+`ROLE_MOD`, MOD 토큰은 `ROLE_MOD`.
- **올리면**: 다음 토큰 갱신부터(길어야 액세스 토큰 수명). 새로고침하면 바로.
- **내리면**: 그 사람의 세션을 모두 끊는다 — 이미 받은 토큰으로 관리 기능을 쓰지 못한다.

## 엔드포인트 권한
`SecurityConfig` — `/api/admin/console/{stats, reportList, handleReport, deletePost, deleteCmt, hideMsg, userList, setUserSuspend, appealList, appealDecide, auditLog}` 는 `ROLE_MOD`, 나머지 `/api/admin/**` 는 `ROLE_ADMIN`.
새 관리 기능을 만들면 **기본은 관리자 전용**이다. 검수자에게도 열려면 위 목록에 넣는다.

## 운영 DB
배포하면 V66 이 1번 회원을 관리자로 둔다. 다른 사람을 처음 지정할 때는 콘솔을 쓴다(쿼리 불필요).
