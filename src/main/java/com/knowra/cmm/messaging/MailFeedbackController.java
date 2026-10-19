package com.knowra.cmm.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * SES 반송·스팸 신고 알림을 받는다. SES → SNS 주제 → 이 주소(HTTPS 구독).
 * 설정: {@code knowra.mail.sns.topic-arn}(MAIL_SNS_TOPIC_ARN). 비어 있으면 꺼져 있다(404).
 *
 * <p>받는 것: 영구 반송(Permanent bounce)과 스팸 신고(Complaint). 일시 반송(메일함 가득 등)은 적지 않는다 — 다음엔 들어간다.
 * SES 의 알림 방식(notificationType)과 설정 집합 이벤트 방식(eventType) 둘 다 읽는다.
 */
@Slf4j
@RestController
public class MailFeedbackController {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SnsVerifier verifier;
    private final MailSuppression suppression;
    private final String topicArn;

    public MailFeedbackController(SnsVerifier verifier, MailSuppression suppression, @Value("${knowra.mail.sns.topic-arn:}") String topicArn) {
        this.verifier = verifier;
        this.suppression = suppression;
        this.topicArn = topicArn;
    }

    /** SNS 는 본문을 text/plain 으로 보낸다 — 글로 받아 직접 읽는다 */
    @PostMapping("/api/mail/sns")
    public ResponseEntity<Void> sns(@RequestBody String body) {
        if (topicArn.isBlank()) return ResponseEntity.notFound().build();
        JsonNode m;
        try { m = JSON.readTree(body); } catch (Exception e) { return ResponseEntity.badRequest().build(); }
        // 우리 주제가 아니면 서명을 볼 것도 없다 — 남의 주제로 서명된 진짜 SNS 메시지도 여기서 걸린다
        if (!topicArn.equals(m.path("TopicArn").asText()) || !verifier.verify(m)) return ResponseEntity.status(403).build();
        switch (m.path("Type").asText()) {
            case "SubscriptionConfirmation" -> {
                boolean ok = verifier.confirm(m.path("SubscribeURL").asText(""));
                log.info("SES 알림 구독 확인 {}", ok ? "완료" : "실패");
            }
            case "Notification" -> {
                try { apply(JSON.readTree(m.path("Message").asText("{}"))); }
                catch (Exception e) { log.warn("SES 알림을 읽지 못함: {}", e.toString()); }
            }
            default -> { }
        }
        return ResponseEntity.ok().build();
    }

    /** SES 알림 본문 하나를 반영한다. 반영한 주소 수 */
    int apply(JsonNode n) {
        String type = n.path("notificationType").asText(n.path("eventType").asText(""));
        int c = 0;
        if ("Bounce".equals(type)) {
            JsonNode b = n.path("bounce");
            if (!"Permanent".equals(b.path("bounceType").asText())) return 0;
            for (JsonNode r : b.path("bouncedRecipients")) {
                String email = r.path("emailAddress").asText("");
                if (email.contains("@")) { suppression.add(email, MailSuppression.BOUNCE, r.path("diagnosticCode").asText(b.path("bounceSubType").asText(null))); c++; }
            }
        } else if ("Complaint".equals(type)) {
            JsonNode b = n.path("complaint");
            for (JsonNode r : b.path("complainedRecipients")) {
                String email = r.path("emailAddress").asText("");
                if (email.contains("@")) { suppression.add(email, MailSuppression.COMPLAINT, b.path("complaintFeedbackType").asText(null)); c++; }
            }
        }
        if (c > 0) log.info("메일 수신 거부 {}건 반영({})", c, type);
        return c;
    }
}
