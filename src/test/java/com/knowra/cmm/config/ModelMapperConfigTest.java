package com.knowra.cmm.config;

import com.knowra.post.entity.TblPost;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 새 글 요청을 엔티티로 옮길 때 이름이 비슷한 칸이 글 번호로 새지 않는다 — 새면 남의 글을 덮어쓴다 */
class ModelMapperConfigTest {

    @Test
    void 비슷한_이름은_글_번호에_들어가지_않는다() {
        TblPost p = new ModelMapperConfig().modelMapper().map(
                Map.of("postTtl", "제목", "postCntnt", "<p>본문</p>", "quotePostSn", 1L, "anyPostSn", 2L, "commSn", 3L), TblPost.class);
        assertThat(p.getPostSn()).isNull();
        assertThat(p.getPostTtl()).isEqualTo("제목");
        assertThat(p.getCommSn()).isEqualTo(3L);
    }
}
