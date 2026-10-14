package com.knowra.user.sns;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 소셜 계정 연결(V33) — (제공자, 제공자 ID) → 계정. 로그인·가입·설정이 같이 쓴다.
 *
 * <p>끊기는 「끊고 나서도 들어올 길이 있을 때만」 — 비밀번호가 있거나 다른 연결이 남아야 한다.
 * 마지막 길을 끊으면 그 계정은 영영 못 들어온다(문의로 돌아온다).
 */
@Service
@RequiredArgsConstructor
public class SnsLinkService {

    private final JdbcTemplate jdbc;

    /** 연결된 활성 계정 — 없으면 null */
    public Long findUser(String provider, String snsId) {
        return jdbc.query("""
                SELECT s.user_sn FROM knowra_user.tbl_user_sns s JOIN knowra_user.tbl_user u ON u.user_sn = s.user_sn
                WHERE s.provider = ? AND s.sns_id = ?""", rs -> rs.next() ? rs.getLong(1) : null, provider, snsId);
    }

    public List<Map<String, Object>> list(long userSn) {
        return jdbc.queryForList("SELECT provider, email, linked_at AS \"linkedAt\" FROM knowra_user.tbl_user_sns WHERE user_sn = ? ORDER BY linked_at", userSn);
    }

    /** 연결 — 이미 다른 계정에 붙은 소셜이면 거절, 같은 제공자의 다른 계정이 붙어 있어도 거절 */
    @Transactional
    public void link(long userSn, SnsIdentity id) {
        Long owner = findUser(id.provider(), id.id());
        if (owner != null && owner == userSn) return;
        if (owner != null) throw new IllegalArgumentException("이 " + label(id.provider()) + " 계정은 이미 다른 knowra 계정에 연결돼 있어요.");
        Long same = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_user.tbl_user_sns WHERE user_sn = ? AND provider = ?", Long.class, userSn, id.provider());
        if (same != null && same > 0) throw new IllegalArgumentException("이미 " + label(id.provider()) + " 계정이 연결돼 있어요. 먼저 끊고 다시 연결해 주세요.");
        jdbc.update("INSERT INTO knowra_user.tbl_user_sns (user_sn, provider, sns_id, email) VALUES (?, ?, ?, ?)",
                userSn, id.provider(), id.id(), id.emailVerified() ? id.email() : null);
    }

    @Transactional
    public void unlink(long userSn, String provider, boolean hasPassword) {
        long links = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_user.tbl_user_sns WHERE user_sn = ?", Long.class, userSn);
        Long mine = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_user.tbl_user_sns WHERE user_sn = ? AND provider = ?", Long.class, userSn, provider);
        if (mine == null || mine == 0) throw new IllegalArgumentException("연결돼 있지 않아요.");
        if (!hasPassword && links <= 1)
            throw new IllegalArgumentException("마지막 로그인 수단이라 끊을 수 없어요. 비밀번호를 먼저 만들거나 다른 소셜 계정을 연결해 주세요.");
        jdbc.update("DELETE FROM knowra_user.tbl_user_sns WHERE user_sn = ? AND provider = ?", userSn, provider);
    }

    public void deleteAll(long userSn) {
        jdbc.update("DELETE FROM knowra_user.tbl_user_sns WHERE user_sn = ?", userSn);
    }

    public static String label(String provider) {
        return switch (provider) { case "GOOGLE" -> "구글"; case "KAKAO" -> "카카오"; case "NAVER" -> "네이버"; case "APPLE" -> "애플"; default -> provider; };
    }
}
