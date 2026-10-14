package com.knowra.user.suspension;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 계정 정지(status='S'). 기간이 있으면 끝나는 때 저절로 풀린다.
 *
 * <p>풀리는 길이 둘이다 — 1분마다 도는 정리, 그리고 로그인·갱신 때 바로 보기. 정리만 믿으면 끝난 지
 * 1분 안에 들어온 사람이 「정지됨」을 보고, 바로 보기만 믿으면 멘션·검색처럼 status 로 거르는 곳에서
 * 그 사람이 들어올 때까지 계속 정지로 보인다.
 */
@Service
@RequiredArgsConstructor
public class SuspensionService {

    private static final DateTimeFormatter UNTIL_FMT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm");

    private final TblUserRepository userRepository;
    private final JdbcTemplate jdbc;

    /** 지금 정지 중인가. 기간이 끝났으면 여기서 풀고 false. */
    public boolean isSuspended(TblUser u) {
        if (!"S".equals(u.getStatus())) return false;
        if (u.getSuspendedUntil() == null || u.getSuspendedUntil().isAfter(LocalDateTime.now())) return true;
        u.setStatus("Y"); u.setSuspendedUntil(null); u.setSuspendReason(null);
        userRepository.save(u);
        return false;
    }

    /** 본인에게 보이는 안내 — 언제까지, 왜. */
    public static String message(TblUser u) {
        StringBuilder sb = new StringBuilder();
        if (u.getSuspendedUntil() != null) sb.append(u.getSuspendedUntil().format(UNTIL_FMT)).append("까지 ");
        sb.append("이용이 정지된 계정입니다.");
        if (u.getSuspendReason() != null && !u.getSuspendReason().isBlank()) sb.append(" 사유: ").append(u.getSuspendReason());
        if (u.getSuspendedUntil() == null) sb.append(" 문의해 주세요.");
        return sb.toString();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void liftExpired() {
        try {
            // 시각은 JVM 에서 넘긴다 — 저장도 JVM 시각(LocalDateTime)이라 DB now() 와는 시간대가 어긋날 수 있다
            jdbc.update("""
                    UPDATE knowra_user.tbl_user SET status = 'Y', suspended_until = NULL, suspend_reason = NULL
                    WHERE status = 'S' AND suspended_until IS NOT NULL AND suspended_until <= ?""", LocalDateTime.now());
        } catch (Exception e) {
            ErrorLog.report(e);
        }
    }
}
