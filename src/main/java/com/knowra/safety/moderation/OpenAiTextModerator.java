package com.knowra.safety.moderation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Moderation API(무료) — 다국어. 「걸렸다」는 판단은 OpenAI 가 분류마다 맞춘 기준(categories=true)을 그대로 쓴다.
 * 보내는 건 공개 글·댓글의 글자뿐이다(사진·DM·누가 썼는지는 보내지 않는다). 처리방침의 처리 위탁에 적혀 있어야 한다.
 */
@Component
@ConditionalOnProperty(name = "knowra.moderation.provider", havingValue = "openai")
public class OpenAiTextModerator implements TextModerator {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public OpenAiTextModerator(@Value("${knowra.moderation.openai.api-key:}") String apiKey,
                               @Value("${knowra.moderation.openai.base-url:https://api.openai.com}") String baseUrl,
                               @Value("${knowra.moderation.openai.model:omni-moderation-latest}") String model) {
        if (apiKey.isBlank()) throw new IllegalStateException("knowra.moderation.provider=openai 인데 OPENAI_API_KEY 가 비어 있다");
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
    }

    @Override
    public List<Verdict> check(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        try {
            String body = JSON.writeValueAsString(Map.of("model", model, "input", texts));
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/moderations"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new IllegalStateException("moderation HTTP " + res.statusCode());
            return parse(res.body(), texts.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {"results":[{"flagged":..,"categories":{"hate":true,..},"category_scores":{"hate":0.9,..}}, ..]} */
    static List<Verdict> parse(String body, int expected) {
        JsonNode results = JSON.readTree(body).path("results");
        if (!results.isArray() || results.size() != expected) throw new IllegalStateException("moderation: 결과 수가 맞지 않음");
        List<Verdict> out = new ArrayList<>(expected);
        for (JsonNode r : results) {
            List<Flag> flags = new ArrayList<>();
            JsonNode cats = r.path("categories"), scores = r.path("category_scores");
            for (var e : cats.properties()) {
                if (e.getValue().asBoolean(false)) flags.add(new Flag(e.getKey(), scores.path(e.getKey()).asDouble(0)));
            }
            flags.sort(Comparator.comparingDouble(Flag::score).reversed());
            out.add(new Verdict(List.copyOf(flags)));
        }
        return out;
    }
}
