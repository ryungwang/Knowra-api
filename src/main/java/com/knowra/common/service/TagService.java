package com.knowra.common.service;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.util.FileUtil;
import com.knowra.common.entity.QTblTag;
import com.knowra.common.entity.TblComFile;
import com.knowra.common.entity.TblTag;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.common.repository.TblTagRepository;
import com.knowra.community.entity.QTblCommPostTag;
import com.knowra.community.entity.TblCommPostTag;
import com.knowra.community.repository.TblCommPostTagRepository;
import com.knowra.post.entity.QTblPostTag;
import com.knowra.post.entity.TblPostTag;
import com.knowra.post.repository.TblPostTagRepository;
import com.knowra.user.entity.TblUserTag;
import com.knowra.user.repository.TblUserRepository;
import com.knowra.user.repository.TblUserTagRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Transactional
public class TagService {

    private static final Logger logger = LoggerFactory.getLogger(TagService.class);

    private final EntityManager em;

    private final TblTagRepository tblTagRepository;
    private final TblCommPostTagRepository tblCommPostTagRepository;
    private final TblPostTagRepository tblPostTagRepository;
    private final TblUserTagRepository tblUserTagRepository;

    /**
     * 게시글 목록의 태그를 배치 조회한다.
     *
     * @param postSns 게시글 SN 목록
     * @param target  "commPost" | "post"
     * @return postSn → 태그명 리스트 Map
     */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> fetchTagMap(List<Long> postSns, String target) {
        if (postSns == null || postSns.isEmpty()) return Map.of();

        JPAQueryFactory q = new JPAQueryFactory(em);
        QTblTag qTag = QTblTag.tblTag;
        Map<Long, List<String>> tagMap = new HashMap<>();

        if ("commPost".equals(target)) {
            QTblCommPostTag qCommPostTag = QTblCommPostTag.tblCommPostTag;
            q.select(qCommPostTag.commPostSn, qTag.tagNm)
             .from(qCommPostTag).join(qTag).on(qCommPostTag.tagSn.eq(qTag.tagSn))
             .where(qCommPostTag.commPostSn.in(postSns))
             .fetch()
             .forEach(t -> tagMap.computeIfAbsent(t.get(qCommPostTag.commPostSn), k -> new ArrayList<>())
                                 .add(t.get(qTag.tagNm)));
        } else {
            QTblPostTag qPostTag = QTblPostTag.tblPostTag;
            q.select(qPostTag.postSn, qTag.tagNm)
             .from(qPostTag).join(qTag).on(qPostTag.tagSn.eq(qTag.tagSn))
             .where(qPostTag.postSn.in(postSns))
             .fetch()
             .forEach(t -> tagMap.computeIfAbsent(t.get(qPostTag.postSn), k -> new ArrayList<>())
                                 .add(t.get(qTag.tagNm)));
        }
        return tagMap;
    }

    /**
     * 태그 수정 — 기존 태그 매핑 삭제(useCount 차감) 후 새 태그 등록
     *
     * @param tagNms 새 태그명 목록
     * @param userSn 수정자 SN
     * @param target "commPost" | "post"
     * @param postSn 게시글 SN
     */
    public void updateTag(List<String> tagNms, long userSn, String target, long postSn) {
        // 기존 태그 매핑 조회 후 useCount 차감
        if ("commPost".equals(target)) {
            tblCommPostTagRepository.findByCommPostSn(postSn).forEach(mapping -> {
                tblTagRepository.findById(mapping.getTagSn()).ifPresent(tag -> {
                    if (tag.getUseCount() > 0) tag.setUseCount(tag.getUseCount() - 1);
                    tblTagRepository.save(tag);
                });
            });
            tblCommPostTagRepository.deleteByCommPostSn(postSn);
        } else {
            tblPostTagRepository.findByPostSn(postSn).forEach(mapping -> {
                tblTagRepository.findById(mapping.getTagSn()).ifPresent(tag -> {
                    if (tag.getUseCount() > 0) tag.setUseCount(tag.getUseCount() - 1);
                    tblTagRepository.save(tag);
                });
            });
            tblPostTagRepository.deleteByPostSn(postSn);
        }
        // 새 태그 등록
        setTag(tagNms, userSn, target, postSn);
    }

    public void setTag(List<String> tagNms, long userSn, String target, long postSn) {
        try {
            for (String tagNm : tagNms) {
                TblTag tblTag = tblTagRepository.findByTagNm(tagNm);
                if(tblTag != null){
                    tblTag.setUseCount(tblTag.getUseCount() + 1);
                    tblTagRepository.save(tblTag);
                }else{
                    tblTag = tblTagRepository.save(TblTag.builder().tagNm(tagNm).useCount(1).build());
                    tblTag.setCreatrSn(userSn);
                }

                if(target.equals("commPost")){
                    TblCommPostTag tblCommPostTag = TblCommPostTag.builder()
                            .commPostSn(postSn)
                            .tagSn(tblTag.getTagSn())
                            .build();
                    tblCommPostTagRepository.save(tblCommPostTag);
                }else{
                    TblPostTag tblPostTag = TblPostTag.builder()
                            .postSn(postSn)
                            .tagSn(tblTag.getTagSn())
                            .build();
                    tblPostTagRepository.save(tblPostTag);
                }

                TblUserTag tblUserTag = tblUserTagRepository.findByUserSnAndTagSn(userSn, tblTag.getTagSn());
                if(tblUserTag != null){
                    tblUserTag.setUseCount(tblUserTag.getUseCount() + 1);
                    tblUserTagRepository.save(tblUserTag);
                }else{
                    tblUserTag = tblUserTagRepository.save(
                        TblUserTag.builder()
                            .userSn(userSn)
                            .tagSn(tblTag.getTagSn())
                            .useCount(1).build()
                    );
                    tblUserTag.setCreatrSn(userSn);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}