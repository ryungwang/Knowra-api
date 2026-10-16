package com.knowra.cmm.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 서버 문구 번역표에 네 언어가 다 있는지 — 하나라도 빠지면 그 언어 사용자에게 영어(또는 한국어)가 섞여 나간다. */
class MessagesCompleteTest {

    @Test
    void 모든_문구에_네_언어가_있다() throws Exception {
        Map<String, Map<String, String>> table;
        try (InputStream in = getClass().getResourceAsStream("/i18n/messages.json")) {
            table = new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
        List<String> missing = new ArrayList<>();
        table.forEach((ko, tr) -> {
            for (String lang : List.of("en", "ja", "zh-Hans", "zh-Hant"))
                if (tr == null || tr.get(lang) == null || tr.get(lang).isBlank()) missing.add(lang + " ← " + ko);
        });
        assertTrue(missing.isEmpty(), "번역이 빠진 문구: " + missing);
    }
}
