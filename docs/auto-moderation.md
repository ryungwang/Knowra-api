# 글 자동 검수 (AI)

> 운영진이 모든 언어를 읽을 수 없어서, 새 글·댓글·수정본을 기계가 먼저 보고 해로우면 **신고함에 올린다**.
> 막지 않는다 — 판단은 사람이 한다.

## 켜는 법
```
MODERATION_PROVIDER=openai
OPENAI_API_KEY=<OpenAI 키>      # Moderation API 는 무료다(사용량 과금 없음)
```
끄려면 `MODERATION_PROVIDER=none`(기본). 꺼져 있으면 아무것도 보내지 않는다.
처음 켠 시각부터 본다 — 예전 글을 한꺼번에 보내지 않는다.

## 하는 일 (`AutoModerationJob`, 1분마다)
| 걸린 분류(OpenAI) | 신고 사유 | 동작 |
|---|---|---|
| sexual/minors | 아동 안전 | **바로 가림** + 관리자 알림(아동 안전 신고와 같음, `docs/child-safety.md`) |
| sexual | 성적 내용 | 신고함 |
| harassment · hate · violence | 욕설·혐오 | 신고함 |
| illicit | 불법 | 신고함 |
| 그 밖(self-harm 등) | 기타 | 신고함 |

- 신고함에 「자동 검수(AI)가 올림」과 분류·점수(`AI · harassment 93% · hate 81%`)가 보인다. 커뮤니티 글이면 그 운영진 신고함에도 뜬다.
- 자동 신고는 「신고 5건이면 가림」 문턱에 세지 않는다.
- 같은 내용은 다시 보내지 않는다(`tbl_auto_mod` 해시). 고치면 다시 본다.

## 보내는 것 / 보내지 않는 것
- 보낸다: 글 제목·본문, 댓글 본문의 **글자**(4,000자까지)
- 보내지 않는다: 누가 썼는지, 사진, DM, 사이트 관리자 글
- 처리방침 「5. 누구에게 맡기나요」에 OpenAI 가 적혀 있다 — **운영에서 켜기 전에 처리방침이 공개돼 있어야 한다.**

## 다른 업체로 바꾸려면
`TextModerator` 를 구현한 빈 하나 + `knowra.moderation.provider` 값. `AutoModerationJob` 의 조건도 같이 맞춘다.
