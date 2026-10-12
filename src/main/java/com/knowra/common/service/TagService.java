package com.knowra.common.service;

import com.knowra.common.entity.QTblTag;
import com.knowra.common.entity.TblTag;
import com.knowra.common.repository.TblTagRepository;
import com.knowra.post.entity.QTblPostTag;
import com.knowra.post.entity.TblPostTag;
import com.knowra.post.repository.TblPostTagRepository;
import com.knowra.user.entity.TblUserTag;
import com.knowra.user.repository.TblUserTagRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class TagService {

    private final EntityManager em;
    private final TblTagRepository tblTagRepository;
    private final TblPostTagRepository tblPostTagRepository;
    private final TblUserTagRepository tblUserTagRepository;

    /**
     * 게시글 목록의 태그를 배치 조회한다.
     *
     * @return postSn → 태그명 리스트
     */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> fetchTagMap(List<Long> postSns) {
        if (postSns == null || postSns.isEmpty()) return Map.of();
        QTblTag     qTag     = QTblTag.tblTag;
        QTblPostTag qPostTag = QTblPostTag.tblPostTag;
        Map<Long, List<String>> tagMap = new HashMap<>();
        new JPAQueryFactory(em)
                .select(qPostTag.postSn, qTag.tagNm)
                .from(qPostTag).join(qTag).on(qPostTag.tagSn.eq(qTag.tagSn))
                .where(qPostTag.postSn.in(postSns))
                .fetch()
                .forEach(t -> tagMap.computeIfAbsent(t.get(qPostTag.postSn), k -> new ArrayList<>())
                                    .add(t.get(qTag.tagNm)));
        return tagMap;
    }

    /** 게시글 하나의 태그 SN 목록 (관심도 점수 갱신용). */
    @Transactional(readOnly = true)
    public List<Long> fetchTagSns(long postSn) {
        QTblPostTag q = QTblPostTag.tblPostTag;
        return new JPAQueryFactory(em)
                .select(q.tagSn)
                .from(q)
                .where(q.postSn.eq(postSn))
                .fetch();
    }

    /** 태그 수정 — 기존 매핑을 지우고(useCount 차감) 새 태그를 등록한다. */
    public void updateTag(List<String> tagNms, long userSn, long postSn) {
        tblPostTagRepository.findByPostSn(postSn).forEach(mapping ->
                tblTagRepository.findById(mapping.getTagSn()).ifPresent(tag -> {
                    if (tag.getUseCount() > 0) tag.setUseCount(tag.getUseCount() - 1);
                    tblTagRepository.save(tag);
                }));
        tblPostTagRepository.deleteByPostSn(postSn);
        setTag(tagNms, userSn, postSn);
    }

    public void setTag(List<String> tagNms, long userSn, long postSn) {
        if (tagNms == null) return;
        for (String tagNm : tagNms) {
            TblTag tblTag = tblTagRepository.findByTagNm(tagNm);
            if (tblTag != null) {
                tblTag.setUseCount(tblTag.getUseCount() + 1);
                tblTagRepository.save(tblTag);
            } else {
                tblTag = tblTagRepository.save(TblTag.builder().tagNm(tagNm).useCount(1).build());
                tblTag.setCreatrSn(userSn);
            }
            tblPostTagRepository.save(TblPostTag.builder()
                    .postSn(postSn)
                    .tagSn(tblTag.getTagSn())
                    .creatrSn(userSn)
                    .build());

            TblUserTag tblUserTag = tblUserTagRepository.findByUserSnAndTagSn(userSn, tblTag.getTagSn());
            if (tblUserTag != null) {
                tblUserTag.setUseCount(tblUserTag.getUseCount() + 1);
                tblUserTagRepository.save(tblUserTag);
            } else {
                tblUserTag = tblUserTagRepository.save(TblUserTag.builder()
                        .userSn(userSn)
                        .tagSn(tblTag.getTagSn())
                        .useCount(1).build());
                tblUserTag.setCreatrSn(userSn);
            }
        }
    }
}
