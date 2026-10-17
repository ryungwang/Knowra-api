package com.knowra.translate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Azure AI Translator(무료 요금제 F0 = 매달 200만 자). 우리 화면 언어 코드(ko·en·ja·zh-Hans·zh-Hant)를 그대로 받는다.
 * HTML 로 보내 태그·{@code class="notranslate"} 를 지킨다.
 */
@Component
@ConditionalOnProperty(name = "knowra.translate.provider", havingValue = "azure")
public class AzureTranslator implements Translator {

    /** 한 번에 보낼 수 있는 양(업체 상한 5만 자보다 넉넉히 아래) */
    static final int MAX_REQUEST_CHARS = 40_000;

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final String key, region, endpoint;

    public AzureTranslator(@Value("${knowra.translate.azure.key:}") String key,
                           @Value("${knowra.translate.azure.region:}") String region,
                           @Value("${knowra.translate.azure.endpoint:https://api.cognitive.microsofttranslator.com}") String endpoint) {
        if (key.isBlank()) throw new IllegalStateException("knowra.translate.provider=azure 인데 AZURE_TRANSLATOR_KEY 가 비어 있다");
        this.key = key;
        this.region = region;
        this.endpoint = endpoint.replaceAll("/+$", "");
    }

    @Override
    public List<Out> translate(List<String> html, String to) {
        List<Out> out = new ArrayList<>(html.size());
        List<String> chunk = new ArrayList<>();
        int size = 0;
        for (String h : html) {
            if (!chunk.isEmpty() && size + h.length() > MAX_REQUEST_CHARS) { out.addAll(call(chunk, to)); chunk.clear(); size = 0; }
            chunk.add(h);
            size += h.length();
        }
        if (!chunk.isEmpty()) out.addAll(call(chunk, to));
        return out;
    }

    private List<Out> call(List<String> html, String to) {
        try {
            String body = JSON.writeValueAsString(html.stream().map(h -> Map.of("Text", h)).toList());
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(endpoint + "/translate?api-version=3.0&textType=html&to=" + URLEncoder.encode(to, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Ocp-Apim-Subscription-Key", key)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!region.isBlank()) b.header("Ocp-Apim-Subscription-Region", region);
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new IllegalStateException("translate HTTP " + res.statusCode());
            return parse(res.body(), html.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** [{"detectedLanguage":{"language":"ko","score":1.0},"translations":[{"text":"…","to":"en"}]}, …] */
    static List<Out> parse(String body, int expected) {
        JsonNode arr = JSON.readTree(body);
        if (!arr.isArray() || arr.size() != expected) throw new IllegalStateException("translate: 결과 수가 맞지 않음");
        List<Out> out = new ArrayList<>(expected);
        for (JsonNode r : arr) {
            String from = r.path("detectedLanguage").path("language").asString(null);
            out.add(new Out(r.path("translations").path(0).path("text").asString(""), from));
        }
        return out;
    }
}
