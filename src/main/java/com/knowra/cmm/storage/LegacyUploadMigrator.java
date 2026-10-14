package com.knowra.cmm.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 예전 디스크 업로드({@code File.Store.Path}, 주소 {@code /upload/...})를 저장소(운영=S3)로 옮긴다.
 * {@code knowra.storage.migrate-legacy=true} 로 띄우면 기동 직후 한 번 돈다. 여러 번 돌려도 같다
 * — 이미 옮긴 줄(file_key 있음)과 이미 바뀐 주소는 다시 손대지 않는다.
 *
 * <ol>
 *   <li>첨부(tbl_com_file) 중 file_key 가 없는 줄 → 파일을 올리고 키·경로를 채운다(프로필·로고·배너).</li>
 *   <li>본문·사진 칸에 박힌 {@code /upload/...} 주소(절대·상대 모두) → 그 파일을 올리고 새 주소로 바꾼다.
 *       글 본문·임시글·위키(HTML), 댓글·DM 사진.</li>
 * </ol>
 * 디스크에 없는 파일은 건드리지 않고 남긴 수를 로그로 알린다. 확장자 허용 목록(이미지) 밖은 옮기지 않는다
 * — 허용 목록이 생기기 전에 올라간 html·svg 같은 파일을 새 저장소로 퍼뜨리지 않으려고.
 */
@Component
public class LegacyUploadMigrator {

    private static final Logger log = LoggerFactory.getLogger(LegacyUploadMigrator.class);
    /** {@code http(s)://아무호스트/upload/...} 또는 {@code /upload/...} — 경로는 영숫자·_-./ 만 */
    private static final Pattern REF = Pattern.compile("(?:https?://[^\"'\\s<>/]+)?/upload/([A-Za-z0-9_./-]+\\.(?:png|jpe?g|gif|webp))", Pattern.CASE_INSENSITIVE);
    /** 표, 키, 칸, 소유자 칸 — 새 키는 전역 규칙 구조 public/{prefix}/{소유자}/{category}/{uuid}.{ext} */
    private static final String[][] TARGETS = {
            {"knowra_post.tbl_post", "post_sn", "post_cntnt", "user_sn"},
            {"knowra_post.tbl_post_draft", "draft_sn", "content", "user_sn"},
            {"knowra_community.tbl_comm_page", "page_sn", "content", "updated_by"},
            {"knowra_post.tbl_post_cmt", "post_cmt_sn", "img_url", "user_sn"},
            {"knowra_user.tbl_chat_msg", "msg_sn", "img_url", "user_sn"},
    };

    private final JdbcTemplate jdbc;
    private final StorageService storage;
    private final boolean enabled;
    private final Path legacyRoot;

    public LegacyUploadMigrator(JdbcTemplate jdbc, StorageService storage,
                                @Value("${knowra.storage.migrate-legacy:false}") boolean enabled,
                                @Value("${File.Store.Path:}") String legacyRoot) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.enabled = enabled;
        this.legacyRoot = legacyRoot.isBlank() ? null : Path.of(legacyRoot).toAbsolutePath().normalize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) return;
        if (legacyRoot == null) { log.warn("[legacy-upload] File.Store.Path 가 없어 건너뜀"); return; }
        Result r = run();
        log.info("[legacy-upload] 첨부 {}건, 주소 {}개(칸 {}곳) 옮김 · 파일 없음 {}건 · 허용 밖 {}건",
                r.files, r.refs, r.cells, r.missing, r.skipped);
    }

    public record Result(int files, int refs, int cells, int missing, int skipped) { }

    public Result run() {
        int[] c = new int[5];
        Map<String, String> moved = new HashMap<>();   // 예전 상대경로 → 새 URL (같은 파일은 한 번만 올린다)

        // 1) 첨부
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT atch_file_sn, atch_file_path_nm, strg_file_nm, atch_file_extn_nm, creatr_sn FROM knowra_com.tbl_com_file WHERE file_key IS NULL")) {
            String dir = String.valueOf(row.get("atch_file_path_nm"));
            String rel = (dir.startsWith("/upload") ? dir.substring("/upload".length()) : dir).replaceAll("^/+", "")
                    + "/" + row.get("strg_file_nm") + "." + row.get("atch_file_extn_nm");
            String category = dir.contains("/user/") ? com.knowra.cmm.util.FileUtil.CAT_PROFILE
                    : dir.contains("logo") ? com.knowra.cmm.util.FileUtil.CAT_COMMUNITY_LOGO
                    : dir.contains("banner") ? com.knowra.cmm.util.FileUtil.CAT_COMMUNITY_BANNER : com.knowra.cmm.util.FileUtil.CAT_EDITOR;
            StorageService.PublicFile f = upload(rel, ((Number) row.get("creatr_sn")).longValue(), category, c);
            if (f == null) continue;
            moved.put(rel, f.url());
            int slash = f.key().lastIndexOf('/');
            String name = f.key().substring(slash + 1);
            jdbc.update("UPDATE knowra_com.tbl_com_file SET file_key = ?, atch_file_path_nm = ?, strg_file_nm = ? WHERE atch_file_sn = ?",
                    f.key(), f.key().substring(0, slash), name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name, row.get("atch_file_sn"));
            c[0]++;
        }

        // 2) 본문·사진 칸
        for (String[] t : TARGETS) {
            for (Map<String, Object> row : jdbc.queryForList(
                    "SELECT " + t[1] + " AS id, " + t[2] + " AS v, " + t[3] + " AS owner FROM " + t[0] + " WHERE " + t[2] + " LIKE '%/upload/%'")) {
                long owner = row.get("owner") == null ? 0 : ((Number) row.get("owner")).longValue();
                String before = String.valueOf(row.get("v"));
                Matcher m = REF.matcher(before);
                StringBuilder sb = new StringBuilder();
                boolean changed = false;
                while (m.find()) {
                    String rel = m.group(1);
                    String url = moved.get(rel);
                    if (url == null) {
                        StorageService.PublicFile f = upload(rel, owner, com.knowra.cmm.util.FileUtil.CAT_EDITOR, c);
                        if (f != null) { url = f.url(); moved.put(rel, url); c[1]++; }
                    }
                    m.appendReplacement(sb, Matcher.quoteReplacement(url != null ? url : m.group()));
                    changed |= url != null;
                }
                m.appendTail(sb);
                if (changed) { jdbc.update("UPDATE " + t[0] + " SET " + t[2] + " = ? WHERE " + t[1] + " = ?", sb.toString(), row.get("id")); c[2]++; }
            }
        }
        return new Result(c[0], c[1], c[2], c[3], c[4]);
    }

    /** 예전 상대경로(rel, 예: editor/2026/04/03/x.png) → 저장소 공개 파일(소유자·분류 키). 없거나 허용 밖이면 null. */
    private StorageService.PublicFile upload(String rel, long owner, String category, int[] c) {
        if (rel.contains("..") || !rel.toLowerCase().matches(".*\\.(png|jpe?g|gif|webp)$")) { c[4]++; return null; }
        Path file = legacyRoot.resolve(rel).normalize();
        if (!file.startsWith(legacyRoot) || !Files.isRegularFile(file)) { c[3]++; log.warn("[legacy-upload] 파일 없음: {}", rel); return null; }
        try {
            return storage.storePublic(Files.readAllBytes(file), owner + "/" + category, file.getFileName().toString());
        } catch (Exception e) {
            c[3]++;
            log.warn("[legacy-upload] 올리기 실패: {} — {}", rel, e.toString());
            return null;
        }
    }
}
