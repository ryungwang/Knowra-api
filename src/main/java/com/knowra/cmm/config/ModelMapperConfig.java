package com.knowra.cmm.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        // ⚠️ STRICT — 이름이 정확히 같은 칸만 옮긴다. STANDARD 는 낱말 조각으로 짝을 지어, 새 글 요청의
        // quotePostSn(또는 아무 xxxPostSn)이 글 번호 postSn 에 들어갔다. 그러면 저장이 새 글이 아니라
        // 그 번호의 남의 글을 덮어쓰고 글쓴이까지 바꿨다(2026-10-16 인용 기능을 만들다 발견).
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        return modelMapper;
    }
}
