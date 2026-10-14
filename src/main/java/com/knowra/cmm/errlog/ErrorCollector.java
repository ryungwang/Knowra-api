package com.knowra.cmm.errlog;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 운영 오류 모음. <b>ERROR 로그 한 곳</b>에서 받는다 — 처리 안 된 요청 예외(톰캣이 ERROR 로 찍는다),
 * 예약 작업, 웹소켓, {@link ErrorLog#report} 가 전부 여기로 온다. 화면 오류는 {@link #record} 로 직접.
 *
 * <p>⚠️ DB 쓰기는 <b>큐 + 작업 스레드 하나</b>에서 한다. 요청 스레드에서 바로 쓰면 DB 가 죽었을 때
 * 오류마다 커넥션 대기(수십 초)가 붙어 장애가 번진다. 큐가 차면 버린다(오류 폭주 때 일부 유실은 감수).
 * 이 수집기 자신과 DB 드라이버 쪽 로그는 받지 않는다 — DB 장애가 무한 반복되는 걸 막는다.
 */
@Component
public class ErrorCollector {

    record Item(String source, String type, String message, String stack, String fingerprintBase, String path, Long userSn) {}

    private static final int MSG_MAX = 1000, STACK_MAX = 8000, PATH_MAX = 300;
    private static final String UPSERT = """
            INSERT INTO knowra_com.tbl_err_log (FINGERPRINT, SOURCE, ERR_TYPE, MESSAGE, STACK, LAST_PATH, LAST_USER_SN)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (FINGERPRINT) DO UPDATE SET
                CNT = knowra_com.tbl_err_log.CNT + 1, LAST_SEEN = CURRENT_TIMESTAMP, RESOLVED = FALSE,
                MESSAGE = EXCLUDED.MESSAGE, LAST_PATH = EXCLUDED.LAST_PATH, LAST_USER_SN = EXCLUDED.LAST_USER_SN
            """;

    private final JdbcTemplate jdbc;
    private final BlockingQueue<Item> queue = new ArrayBlockingQueue<>(500);
    private volatile boolean running = true;
    private Thread worker;
    private AppenderBase<ILoggingEvent> appender;

    public ErrorCollector(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        worker = new Thread(this::drain, "error-collector");
        worker.setDaemon(true);
        worker.start();

        appender = new AppenderBase<>() {
            @Override protected void append(ILoggingEvent ev) { onLog(ev); }
        };
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender.setContext(ctx);
        appender.setName("knowra-error-collector");
        appender.start();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (appender != null) appender.stop();
        if (worker != null) worker.interrupt();
    }

    /** 화면 오류 등 로그를 거치지 않는 오류. */
    public void record(String source, String type, String message, String stack, String fingerprintBase, String path, Long userSn) {
        queue.offer(new Item(source, cut(type, 200), cut(message, MSG_MAX), cut(stack, STACK_MAX), fingerprintBase, cut(path, PATH_MAX), userSn));
    }

    private void onLog(ILoggingEvent ev) {
        if (!ev.getLevel().isGreaterOrEqual(Level.ERROR)) return;
        String logger = ev.getLoggerName();
        if (logger.startsWith(ErrorCollector.class.getName()) || logger.startsWith("com.zaxxer.hikari")
                || logger.startsWith("org.postgresql") || logger.startsWith("org.springframework.jdbc")) return;

        IThrowableProxy t = ev.getThrowableProxy();
        // 원인 사슬의 맨 안쪽이 진짜 원인이다 — 종류·지문은 그걸로
        IThrowableProxy root = t;
        while (root != null && root.getCause() != null) root = root.getCause();

        String type = root != null ? root.getClassName() : logger;
        String message = root != null ? String.valueOf(root.getMessage()) : ev.getFormattedMessage();
        StringBuilder stack = new StringBuilder();
        StringBuilder fp = new StringBuilder("API|").append(type).append('|');
        if (t != null) {
            appendStack(stack, t, 0);
            // 지문은 우리 코드(com.knowra) 쪽 위 다섯 줄. 메시지는 넣지 않는다(id 등이 섞여 같은 오류가 갈라진다).
            int n = 0;
            for (IThrowableProxy c = t; c != null && n < 5; c = c.getCause())
                for (StackTraceElementProxy s : c.getStackTraceElementProxyArray()) {
                    String f = s.getStackTraceElement().toString();
                    if (n < 5 && f.startsWith("com.knowra.") && !f.contains(".errlog.")) { fp.append(f).append('|'); n++; }
                }
            if (n == 0) fp.append(logger);
        } else {
            fp.append(logger).append('|').append(ev.getMessage()); // 템플릿(인자 전) — 값이 달라도 같은 오류
        }
        String path = null;
        Long userSn = null;
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a)
                path = a.getRequest().getMethod() + " " + a.getRequest().getRequestURI();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Long u) userSn = u;
            if (path == null) {
                ErrorContextResolver.Ctx c = ErrorContextResolver.take();
                if (c != null) { path = c.path(); userSn = c.userSn(); }
            }
        } catch (Exception ignored) { }
        record("API", type, message, stack.toString(), fp.toString(), path, userSn);
    }

    private static void appendStack(StringBuilder sb, IThrowableProxy t, int depth) {
        if (t == null || depth > 4 || sb.length() > STACK_MAX) return;
        sb.append(depth == 0 ? "" : "Caused by: ").append(t.getClassName()).append(": ").append(t.getMessage()).append('\n');
        StackTraceElementProxy[] arr = t.getStackTraceElementProxyArray();
        for (int i = 0; i < arr.length && i < 40; i++) sb.append("\tat ").append(arr[i].getStackTraceElement()).append('\n');
        appendStack(sb, t.getCause(), depth + 1);
    }

    private void drain() {
        while (running) {
            try {
                Item it = queue.poll(5, TimeUnit.SECONDS);
                if (it == null) continue;
                jdbc.update(UPSERT, sha256(it.source() + "|" + it.fingerprintBase()), it.source(), it.type(),
                        it.message() == null ? "" : it.message(), it.stack() == null ? "" : it.stack(), it.path(), it.userSn());
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                // 여기서 ERROR 로 찍으면 다시 들어온다 — 표준 오류로만
                System.err.println("[error-collector] 저장 실패: " + e);
            }
        }
    }

    private static String cut(String s, int max) { return s == null ? null : s.length() <= max ? s : s.substring(0, max); }

    public static String sha256Public(String s) { return sha256(s); }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }
}
