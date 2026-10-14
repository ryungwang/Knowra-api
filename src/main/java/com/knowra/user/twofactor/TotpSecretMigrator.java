package com.knowra.user.twofactor;

import com.knowra.cmm.crypto.SecretBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 기동 때 평문으로 남은 2단계 인증 키를 암호화한다. 몇 번 돌아도 같다(이미 v1: 인 줄은 건너뛴다).
 * SQL 마이그레이션으로 못 하는 이유: 키가 환경변수에만 있다.
 * 조건부 UPDATE(아직 그 평문일 때만)라 서버가 여러 대 동시에 떠도 겹쳐 쓰지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TotpSecretMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final SecretBox box;

    @Override
    public void run(ApplicationArguments args) {
        int n = 0;
        for (var r : jdbc.queryForList("SELECT user_sn, totp_secret FROM knowra_user.tbl_user WHERE totp_secret IS NOT NULL AND totp_secret NOT LIKE 'v1:%'")) {
            long sn = ((Number) r.get("user_sn")).longValue();
            String plain = (String) r.get("totp_secret");
            n += jdbc.update("UPDATE knowra_user.tbl_user SET totp_secret = ? WHERE user_sn = ? AND totp_secret = ?",
                    box.seal(plain, TwoFactorService.aad(sn)), sn, plain);
        }
        if (n > 0) log.info("[2FA] 평문 키 {}개를 암호화했습니다", n);
    }
}
