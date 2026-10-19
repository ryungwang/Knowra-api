# 전 세계 서비스용 인프라 설정 (CDN · 메일)

> 코드는 준비돼 있고, **운영 서버·DNS·클라우드 콘솔에서 해야 하는 일**만 남았다. 순서대로 하면 된다.
> 이 저장소에는 nginx 설정이 없다(서버의 `/home/deer/knowra`) — 아래 nginx 항목은 거기에 넣는다.

## 1. CDN

### 무엇을 고르나
| | Cloudflare | AWS CloudFront |
|---|---|---|
| 비용 | 무료 요금제로 충분 | 전송량 과금 |
| 함께 얻는 것 | DDoS 방어, **CSAM Scanning Tool**(무료, `docs/child-safety.md`), 봇 차단 | S3 와 같은 계정에서 관리 |
| 붙이는 법 | 도메인 네임서버를 Cloudflare 로 | 배포(distribution) 만들고 도메인 연결 |

아동 안전 대조까지 생각하면 **Cloudflare 를 권한다**.

### 화면(SPA)
nginx 가 내는 정적 파일에 캐시 머리글을 붙인다 — CDN 은 이걸 보고 캐시한다.

```nginx
# 빌드 산출물 — 파일명에 해시가 있어 바뀌면 다른 파일이다
location /assets/ { add_header Cache-Control "public, max-age=31536000, immutable"; }
# 바뀌면 바로 반영돼야 하는 것 — 캐시하지 않는다
location = /index.html { add_header Cache-Control "no-cache"; }
location = /sw.js      { add_header Cache-Control "no-cache"; }
location ~ ^/manifest.*\.webmanifest$ { add_header Cache-Control "no-cache"; }
location = /offline.html { add_header Cache-Control "no-cache"; }
# /api 는 CDN 에서 캐시하지 않는다(로그인별 응답) — Cloudflare 는 기본으로 HTML·JSON 을 캐시하지 않는다
```
글꼴(Pretendard)은 이제 `/assets/` 아래에서 나간다 — 외부 CDN 을 쓰지 않는다.

### 사용자가 올린 사진(S3)
- 올릴 때 이미 `Cache-Control: public, max-age=31536000, immutable` 을 붙인다(`S3StorageService`).
- CDN 주소로 내려면 **환경변수 하나**: `AWS_S3_PUBLIC_BASE_URL=https://<CDN 도메인>` — 새로 올리는 사진부터 그 주소로 나간다.
  예전 글 본문에 박힌 S3 주소는 그대로 열린다(버킷 공개 정책을 유지).

### 공유 미리보기(/s/...)
`/s/` 는 받는 사람 언어(Accept-Language)마다 문구가 달라 `Vary: Accept-Language` 를 붙인다. CDN 이 Vary 를 무시하면 언어가 섞이므로 **`/s/*` 는 캐시하지 않게** 규칙을 둔다.

## 2. 해외로 가는 메일

### 발송 업체
지금은 `spring.mail.*`(SMTP) 이다. 해외 수신함에 잘 들어가려면 평판 있는 발송 업체를 쓴다 — **Amazon SES**(S3 와 같은 계정) 또는 Postmark.
SES 는 처음엔 샌드박스(인증한 주소에만 발송)라 **프로덕션 액세스 요청**을 해야 한다.

### DNS — 이것 없으면 Gmail·Outlook 이 스팸함으로 보낸다
| 레코드 | 내용 |
|---|---|
| SPF | `v=spf1 include:amazonses.com ~all` (쓰는 업체에 맞게) |
| DKIM | 업체가 주는 CNAME 3개(SES 「Easy DKIM」) |
| DMARC | `_dmarc` TXT: `v=DMARC1; p=quarantine; rua=mailto:<받을 주소>` — 처음엔 `p=none` 으로 보고서만 받다가 올린다 |
| 보내는 주소 | `knowra.mail.from` 을 위 도메인 주소로(예: `no-reply@<도메인>`) |

### 코드가 이미 하는 것
- 메일은 **받는 사람 언어·시간대**로 쓴다(V51).
- 주간 소식은 **한 번 눌러 그만 받기** 머리글(`List-Unsubscribe`, `List-Unsubscribe-Post`, RFC 8058)을 단다 —
  2024년부터 Gmail·Yahoo 가 대량 발송자에게 요구한다. 누르면 `POST /api/digest/oneclick` 이 서명을 확인하고 끈다.
- **반송·스팸 신고 처리**(V67 `tbl_mail_suppression`) — SES 가 알려 주면 그 주소로 더 보내지 않는다.
  - 영구 반송: 본인이 요청한 메일(인증 코드·비밀번호 재설정)만 보낸다. 그 주소로 코드를 확인하거나 재설정 링크를 쓰면 기록이 지워진다.
  - 스팸 신고: 주간 소식만 보내지 않고 주간 소식 설정을 끈다. 보안·계정 안내는 계속 간다. 본인이 주간 소식을 다시 켜면 지워진다.
  - 일시 반송(메일함 가득 등)은 적지 않는다.

### 반송·스팸 신고 알림 연결 (SES 를 쓸 때)
1. SNS 주제를 만든다(예: `ses-feedback`, SES 와 같은 지역).
2. SES › 확인된 도메인 › 알림 — Bounce·Complaint 를 그 주제로 보낸다(「원래 머리글 포함」은 끈다).
3. 서버 환경변수 `MAIL_SNS_TOPIC_ARN=<주제 ARN>` 을 넣고 재시작한다. 비어 있으면 받는 창구가 꺼져 있다(404).
4. SNS › 구독 만들기 — 프로토콜 HTTPS, 엔드포인트 `https://<API 도메인>/api/mail/sns`. 서버가 구독 확인을 스스로 끝낸다(로그 「SES 알림 구독 확인 완료」).
5. SES 시뮬레이터 주소 `bounce@simulator.amazonses.com` 으로 하나 보내 `tbl_mail_suppression` 에 들어오는지 본다.

서명(SNS SignatureVersion 1·2)과 주제 ARN 을 확인하고, 인증서·구독 주소는 `sns.<지역>.amazonaws.com` 만 연다 — 아무나 남의 주소를 막을 수 없다.

## 3. 확인
- [ ] CDN 뒤에서 `/assets/*` 응답에 `cf-cache-status: HIT`(Cloudflare) 또는 `x-cache: Hit`(CloudFront)
- [ ] `index.html` 은 캐시되지 않음 — 배포 직후 새 화면이 보임
- [ ] `https://www.mail-tester.com` 에 주간 소식을 보내 9점 이상
- [ ] Gmail 에서 주간 소식 머리에 「구독 취소」 버튼이 보임
