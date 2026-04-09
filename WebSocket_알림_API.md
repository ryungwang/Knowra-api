# WebSocket 알림 API

## 연결

| 항목 | 내용 |
|------|------|
| 프로토콜 | `ws://` (개발) / `wss://` (운영) |
| 엔드포인트 | `/ws/notification` |
| 인증 | 쿼리 파라미터 `?token={JWT}` |
| 인증 실패 | 연결 거부 (HTTP 401) |

**연결 예시**
```
ws://localhost:8080/ws/notification?token=eyJhbGciOiJIUzI1...
```

---

## 메시지 형식

모든 메시지는 JSON 문자열로 송수신합니다.

### 서버 → 클라이언트

#### INIT — 연결 직후 기존 알림 목록 전송
```json
{
  "type": "INIT",
  "data": {
    "unreadCnt": 2,
    "list": [
      {
        "notifSn": 1,
        "notifTyp": "COMMENT",
        "message": "user2가 내 게시글에 댓글을 달았습니다.",
        "senderNickName": "user2",
        "senderPfpUrl": "http://example.com/upload/profile/uuid.jpg",
        "targetSn": 42,
        "targetKind": "POST",
        "createdAt": "2026-04-09T14:30:00",
        "isRead": false
      },
      {
        "notifSn": 2,
        "notifTyp": "LIKE",
        "message": "user3이 내 게시글을 좋아합니다.",
        "senderNickName": "user3",
        "senderPfpUrl": null,
        "targetSn": 42,
        "targetKind": "POST",
        "createdAt": "2026-04-09T13:00:00",
        "isRead": true
      }
    ]
  }
}
```

> `createdAt`은 ISO 8601 형식으로 전달. 상대시간("5분 전") 변환은 프론트에서 처리.

#### NOTIFICATION — 실시간 신규 알림 Push
```json
{
  "type": "NOTIFICATION",
  "data": {
    "notifSn": 3,
    "notifTyp": "FOLLOW",
    "message": "user4가 팔로우를 시작했습니다.",
    "senderNickName": "user4",
    "senderPfpUrl": "http://example.com/upload/profile/uuid2.jpg",
    "targetSn": null,
    "targetKind": null,
    "createdAt": "2026-04-09T15:00:00",
    "isRead": false
  }
}
```

---

### 클라이언트 → 서버

#### MARK_READ — 단건 읽음 처리
```json
{
  "type": "MARK_READ",
  "notifSn": 1
}
```

#### MARK_ALL_READ — 전체 읽음 처리
```json
{
  "type": "MARK_ALL_READ"
}
```

---

## notifTyp 종류

| 값 | 설명 | 발생 시점 | targetKind |
|----|------|-----------|------------|
| `COMMENT` | 댓글 알림 | 내 게시글에 댓글 작성 시 | `POST` \| `COMM_POST` |
| `LIKE` | 좋아요 알림 | 내 게시글에 좋아요 시 | `POST` \| `COMM_POST` |
| `FOLLOW` | 팔로우 알림 | 누군가 나를 팔로우 시 | `null` |
| `SYSTEM` | 시스템 공지 | 관리자 공지 발송 시 | `null` |

---

## 알림 트리거 시점

| 이벤트 | 알림 수신 대상 |
|--------|---------------|
| `POST /api/community/setCommPostCmt` | 게시글 작성자 |
| `POST /api/community/setCommPostLike` | 게시글 작성자 |
| `POST /api/post/setPostCmt` | 게시글 작성자 |
| `POST /api/post/setPostLike` | 게시글 작성자 |
| `POST /api/user/setFollow` | 팔로우 받은 유저 |
| 관리자 공지 | 전체 또는 특정 유저 |

---

## DB 테이블 (참고)

```sql
CREATE TABLE KNOWRA_USER.TBL_USER_NOTIF (
    NOTIF_SN        BIGINT AUTO_INCREMENT PRIMARY KEY,
    USER_SN         BIGINT       NOT NULL,           -- 수신자
    SENDER_SN       BIGINT,                          -- 발신자 (SYSTEM 공지는 NULL)
    NOTIF_TYP       VARCHAR(20)  NOT NULL,           -- COMMENT | LIKE | FOLLOW | SYSTEM
    MESSAGE         VARCHAR(255) NOT NULL,
    TARGET_SN       BIGINT,                          -- 알림 대상 SN (FOLLOW·SYSTEM은 NULL)
    TARGET_KIND     VARCHAR(20),                     -- POST | COMM_POST | null
    IS_READ         CHAR(1)      DEFAULT 'N',
    FRST_CRT_DT     DATETIME     DEFAULT NOW(),
    FOREIGN KEY (USER_SN)   REFERENCES KNOWRA_USER.TBL_USER(USER_SN),
    FOREIGN KEY (SENDER_SN) REFERENCES KNOWRA_USER.TBL_USER(USER_SN)
);
```

---

## 재연결 정책 (프론트엔드)

비정상 종료 시 최대 5회 지수 백오프 재연결

| 시도 | 대기 시간 |
|------|-----------|
| 1회 | 1초 |
| 2회 | 2초 |
| 3회 | 4초 |
| 4회 | 8초 |
| 5회 | 16초 |
