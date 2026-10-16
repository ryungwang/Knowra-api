package com.knowra.cmm.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 메일 발송. {@code knowra.mail.enabled=false}(로컬 기본)면 실제로 보내지 않고 로그에만 남긴다 —
 * 로컬에서 SMTP 없이 가입·재설정 흐름을 끝까지 돌리기 위해서다. 운영은 SMTP 환경변수를 넣고
 * {@code enabled=true} 로 켠다. 켜져 있는데 보내기에 실패하면 예외를 던진다(호출부가 실패로 안내).
 */
@Slf4j
@Service
public class MailService {

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final boolean enabled;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> senderProvider,
                       @Value("${knowra.mail.enabled:false}") boolean enabled,
                       @Value("${knowra.mail.from:no-reply@knowra.local}") String from) {
        this.senderProvider = senderProvider;
        this.enabled = enabled;
        this.from = from;
    }

    public boolean isEnabled() { return enabled; }

    public void send(String to, String subject, String text) { send(to, subject, text, null); }

    /**
     * 머리글을 더 붙여 보낸다 — 주간 소식처럼 한꺼번에 보내는 메일은 List-Unsubscribe(한 번 눌러 그만 받기, RFC 8058)가
     * 있어야 Gmail·Yahoo 가 스팸함으로 보내지 않는다(2024년부터 대량 발송자 요건). headers 가 없으면 예전과 같다.
     */
    public void send(String to, String subject, String text, java.util.Map<String, String> headers) {
        if (!enabled) {
            log.info("[MAIL(dry-run)] to={} subject={} headers={}\n{}", to, subject, headers, text);
            return;
        }
        if (headers != null && !headers.isEmpty()) {
            JavaMailSender sender = senderProvider.getIfAvailable();
            if (sender == null) throw new IllegalStateException("메일 발송기가 설정되지 않았습니다 (spring.mail.*)");
            try {
                jakarta.mail.internet.MimeMessage m = sender.createMimeMessage();
                org.springframework.mail.javamail.MimeMessageHelper h = new org.springframework.mail.javamail.MimeMessageHelper(m, false, "UTF-8");
                h.setFrom(from); h.setTo(to); h.setSubject(subject); h.setText(text, false);
                for (var e : headers.entrySet()) m.setHeader(e.getKey(), e.getValue());
                sender.send(m);
            } catch (jakarta.mail.MessagingException ex) { throw new IllegalStateException(ex); }
            return;
        }
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null) throw new IllegalStateException("메일 발송기가 설정되지 않았습니다 (spring.mail.*)");
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(text);
        sender.send(msg);
    }
}
