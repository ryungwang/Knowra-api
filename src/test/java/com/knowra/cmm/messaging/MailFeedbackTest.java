package com.knowra.cmm.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MailFeedbackTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 실제 SNS 처럼 서명한 알림 */
    private static ObjectNode signed(KeyPair kp, String version, String alg) throws Exception {
        ObjectNode m = JSON.createObjectNode();
        m.put("Type", "Notification");
        m.put("MessageId", "22b80b92-fdea-4c2c-8f9d-bdfb0c7bf324");
        m.put("TopicArn", "arn:aws:sns:ap-northeast-2:123456789012:ses-feedback");
        m.put("Message", "{\"notificationType\":\"Bounce\"}");
        m.put("Timestamp", "2026-10-19T01:00:00.000Z");
        m.put("SignatureVersion", version);
        Signature s = Signature.getInstance(alg);
        s.initSign(kp.getPrivate());
        s.update(SnsVerifier.stringToSign(m).getBytes(StandardCharsets.UTF_8));
        m.put("Signature", Base64.getEncoder().encodeToString(s.sign()));
        return m;
    }

    @Test
    void 서명이_맞으면_통과하고_한_글자라도_바뀌면_막는다() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        for (String[] v : new String[][]{{"1", "SHA1withRSA"}, {"2", "SHA256withRSA"}}) {
            ObjectNode m = signed(kp, v[0], v[1]);
            assertThat(SnsVerifier.verify(m, kp.getPublic())).isTrue();
            m.put("Message", "{\"notificationType\":\"Complaint\"}");
            assertThat(SnsVerifier.verify(m, kp.getPublic())).isFalse();
        }
        KeyPair other = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        assertThat(SnsVerifier.verify(signed(kp, "2", "SHA256withRSA"), other.getPublic())).isFalse();
        ObjectNode unknown = signed(kp, "2", "SHA256withRSA");
        unknown.put("SignatureVersion", "3");
        assertThat(SnsVerifier.verify(unknown, kp.getPublic())).isFalse();
    }

    @Test
    void 서명할_글은_정해진_칸만_순서대로_없는_칸은_뺀다() {
        ObjectNode m = JSON.createObjectNode();
        m.put("Type", "Notification"); m.put("Message", "hi"); m.put("MessageId", "1"); m.put("Timestamp", "t"); m.put("TopicArn", "a");
        m.put("UnsubscribeURL", "https://x");   // 서명 대상이 아니다
        assertThat(SnsVerifier.stringToSign(m)).isEqualTo("Message\nhi\nMessageId\n1\nTimestamp\nt\nTopicArn\na\nType\nNotification\n");
    }

    @Test
    void 인증서와_구독_주소는_AWS_SNS_주소만_믿는다() {
        assertThat(SnsVerifier.isSnsUrl("https://sns.ap-northeast-2.amazonaws.com/SimpleNotificationService-abc.pem")).isTrue();
        assertThat(SnsVerifier.isSnsUrl("https://sns.cn-north-1.amazonaws.com.cn/x.pem")).isTrue();
        assertThat(SnsVerifier.isSnsUrl("http://sns.us-east-1.amazonaws.com/x.pem")).isFalse();
        assertThat(SnsVerifier.isSnsUrl("https://sns.us-east-1.amazonaws.com.evil.com/x.pem")).isFalse();
        assertThat(SnsVerifier.isSnsUrl("https://evil.com/sns.us-east-1.amazonaws.com/x.pem")).isFalse();
        assertThat(SnsVerifier.isSnsUrl("https://sns.us-east-1.amazonaws.com:8443/x.pem")).isFalse();
        assertThat(SnsVerifier.isSnsUrl("https://user@sns.us-east-1.amazonaws.com/x.pem")).isTrue();   // 호스트는 같다 — 내용은 서명으로 본다
    }

    /** 적은 것만 기억하는 가짜 */
    static class Recorder extends MailSuppression {
        final List<String> added = new ArrayList<>();
        Recorder() { super(null); }
        @Override public void add(String email, String reason, String detail) { added.add(reason + ":" + email); }
    }

    @Test
    void 영구_반송과_스팸_신고만_적고_일시_반송은_넘긴다() throws Exception {
        Recorder r = new Recorder();
        MailFeedbackController c = new MailFeedbackController(null, r, "arn");
        c.apply(JSON.readTree("""
                {"notificationType":"Bounce","bounce":{"bounceType":"Permanent","bounceSubType":"General",
                 "bouncedRecipients":[{"emailAddress":"gone@example.com","diagnosticCode":"smtp; 550 5.1.1 user unknown"}]}}"""));
        c.apply(JSON.readTree("""
                {"notificationType":"Bounce","bounce":{"bounceType":"Transient","bouncedRecipients":[{"emailAddress":"full@example.com"}]}}"""));
        c.apply(JSON.readTree("""
                {"eventType":"Complaint","complaint":{"complaintFeedbackType":"abuse","complainedRecipients":[{"emailAddress":"angry@example.com"}]}}"""));
        assertThat(r.added).containsExactly("BOUNCE:gone@example.com", "COMPLAINT:angry@example.com");
    }
}
