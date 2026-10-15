package com.knowra.savefolder;

import com.knowra.cmm.i18n.Msg;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.post.entity.QTblPostSave;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 저장 폴더 — 내 것만. 이름 40자, 사람당 30개. 폴더를 지워도 안의 저장 글은 남는다(폴더만 빠진다). */
@RestController
@RequestMapping("/api/saveFolder")
@RequiredArgsConstructor
public class SaveFolderController {

    public static final int MAX_FOLDERS = 30, MAX_NAME = 40;
    private final TblPostSaveFolderRepository repo;

    @PersistenceContext
    private EntityManager em;

    @PostMapping("/list")
    public ResultVO list(@CurrentUser Long me) {
        QTblPostSave s = QTblPostSave.tblPostSave;
        Map<Long, Long> counts = new HashMap<>();
        new JPAQueryFactory(em).select(s.folderSn, s.count()).from(s)
                .where(s.userSn.eq(me), s.actvtnYn.eq("Y"), s.folderSn.isNotNull()).groupBy(s.folderSn).fetch()
                .forEach(t -> counts.put(t.get(s.folderSn), t.get(s.count())));
        long total = Optional.ofNullable(new JPAQueryFactory(em).select(s.count()).from(s).where(s.userSn.eq(me), s.actvtnYn.eq("Y")).fetchOne()).orElse(0L);
        List<Map<String, Object>> list = new ArrayList<>();
        for (TblPostSaveFolder f : repo.findAllByUserSnOrderByFrstCrtDtAsc(me)) {
            list.add(Map.of("folderSn", f.getFolderSn(), "name", f.getName(), "count", counts.getOrDefault(f.getFolderSn(), 0L)));
        }
        ResultVO r = ok("list", list);
        r.putResult("total", total);
        return r;
    }

    /** body: name */
    @PostMapping("/create")
    public ResultVO create(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        String name = clean(b.get("name"));
        if (name == null) return fail(Msg.t("폴더 이름을 1~{0}자로 지어 주세요.", MAX_NAME));
        if (repo.countByUserSn(me) >= MAX_FOLDERS) return fail(Msg.t("폴더는 {0}개까지 만들 수 있어요.", MAX_FOLDERS));
        if (repo.existsByUserSnAndName(me, name)) return fail("같은 이름의 폴더가 있어요.");
        TblPostSaveFolder f = repo.save(TblPostSaveFolder.builder().userSn(me).name(name).build());
        return ok("folderSn", f.getFolderSn());
    }

    /** body: folderSn, name */
    @PostMapping("/rename")
    public ResultVO rename(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        String name = clean(b.get("name"));
        if (name == null) return fail(Msg.t("폴더 이름을 1~{0}자로 지어 주세요.", MAX_NAME));
        TblPostSaveFolder f = mine(b, me);
        if (f == null) return fail("폴더를 찾을 수 없어요.");
        if (!f.getName().equals(name) && repo.existsByUserSnAndName(me, name)) return fail("같은 이름의 폴더가 있어요.");
        f.setName(name); repo.save(f);
        return ok("folderSn", f.getFolderSn());
    }

    /** body: folderSn — 안의 글은 「전체」로 돌아간다(DB 외래키 ON DELETE SET NULL). */
    @PostMapping("/delete")
    @Transactional
    public ResultVO delete(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        TblPostSaveFolder f = mine(b, me);
        if (f != null) repo.delete(f);
        return ok("deleted", f != null);
    }

    private TblPostSaveFolder mine(Map<String, Object> b, long me) {
        if (b.get("folderSn") == null) return null;
        return repo.findById(Long.valueOf(String.valueOf(b.get("folderSn")))).filter(x -> x.getUserSn() == me).orElse(null);
    }

    private static String clean(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim().replaceAll("\\s+", " ");
        return s.isEmpty() || s.length() > MAX_NAME ? null : s;
    }

    private static ResultVO ok(String k, Object v) { ResultVO r = new ResultVO(); r.putResult(k, v); r.setResultCode(ResponseCode.SUCCESS.getCode()); return r; }
    private static ResultVO fail(String m) { ResultVO r = new ResultVO(); r.setResultCode(ResponseCode.SAVE_ERROR.getCode()); r.setResultMessage(m); return r; }
}
