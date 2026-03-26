# 커뮤니티 게시글 프론트 작업 명세

> 최종 수정: 2026-03-26

---

## API 목록

| 메서드 | URL | 설명 |
|--------|-----|------|
| POST | `/api/community/getCommPostList` | 게시글 피드 조회 |
| POST | `/api/community/viewCommPost` | 게시글 열람 기록 (시청자 수) |

---

## 2. 게시글 피드 조회 (무한스크롤)

### listTyp 종류

| 값 | 설명 | 페이징 방식 |
|----|------|------------|
| `LATEST` | 최신순 (기본값) | cursor 기반 |
| `ALL` | 전체 (최신순 동일) | cursor 기반 |
| `POPULAR` | 인기순 (likeCnt) | page 기반 |
| `NOTICE` | 공지만 | cursor 기반 |

### Request — 첫 로드
```json
POST /api/community/getCommPostList
Authorization: Bearer {token}
Content-Type: application/json

{
  "commSn": 1,
  "listTyp": "LATEST"
}
```

### Request — 다음 페이지 (스크롤 바닥 도달 시)
```json
{
  "commSn": 1,
  "listTyp": "LATEST",
  "cursor": 38
}
```

### Request — 인기 탭 (page 방식)
```json
{
  "commSn": 1,
  "listTyp": "POPULAR",
  "page": 0
}
```

### Response
```json
{
  "resultCode": "200",
  "resultMessage": "성공",
  "list": [
    {
      "commPostSn": 42,
      "commSn": 1,
      "userSn": 7,
      "authorNm": "user2",
      "postTyp": "NORMAL",
      "postTtl": "여친이 갑자기 이러는데 뭐냐?",
      "frstCrtDt": "2026-03-26T14:30:00",
      "viewCnt": 0,
      "likeCnt": 320,
      "cmtCnt": 120,
      "viewCnt": 320,
      "tagNms": ["#연애", "#고민"]
    }
  ],
  "nextCursor": 38
}
```

> `nextCursor`가 `null`이면 마지막 페이지 → 로딩 중단

---

## 3. 게시글 열람 기록 (시청자 수)

상세 페이지 진입 시 호출. Redis에 5분 TTL로 기록됨.

```json
POST /api/community/viewCommPost
Authorization: Bearer {token}
Content-Type: application/json

{
  "commPostSn": 42
}
```

> 상세 페이지 **진입 시 1번만** 호출. 재호출 불필요.

---

## 4. 피드 카드 UI 매핑

```
┌─────────────────────────────────────────────┐
│ [프로필] authorNm   frstCrtDt(상대시간)         │
│          • viewCnt명이 읽었어요          #태그들 │
│                                              │
│  postTtl                                     │
│                                              │
│  ↑ likeCnt ↓   □ cmtCnt   ≪ 공유   □ 저장   │
└─────────────────────────────────────────────┘
```

| 필드 | 표시 방법 |
|------|----------|
| `authorNm` | 작성자명 |
| `frstCrtDt` | "N분 전 / N시간 전 / N일 전" 상대시간 변환 |
| `viewCnt` | "N명이 읽었어요" |
| `postTyp` | `NOTICE`이면 [공지] 뱃지 표시 |
| `tagNms` | `#태그` 형태로 나열 |
| `likeCnt` | 업/다운 버튼 사이 숫자 (업 +1, 다운 -1) |
| `cmtCnt` | 댓글 수 |

---

## 5. 무한스크롤 구현 흐름

```
1. 컴포넌트 마운트 → getCommPostList (cursor 없음)
2. 응답 list 렌더링, nextCursor 저장
3. 스크롤 바닥 감지 (IntersectionObserver 권장)
4. nextCursor가 null이 아니면 → cursor: nextCursor로 재요청
5. 응답 list를 기존 목록에 append
6. nextCursor가 null → "모든 게시글을 확인했습니다" 표시
```

---

## 6. 탭 전환 시 주의사항

- 탭 전환 시 기존 list 초기화 + cursor/page 리셋 후 재조회
- `POPULAR` 탭은 cursor 무시하고 page=0부터 시작
- `NOTICE` 탭은 postTyp=NOTICE인 게시글만 노출됨
