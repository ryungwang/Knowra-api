package com.knowra.cmm.storage;

import java.util.Locale;
import java.util.UUID;

/** 객체 키 만들기 — 추측할 수 없고 겹치지 않게(uuid), 확장자는 소문자·영숫자만. */
final class StorageKeys {

    private StorageKeys() { }

    /** {@code public/{keyPrefix}/{path}/{uuid}.{ext}} */
    static String publicKey(String keyPrefix, String path, String originalFilename) {
        StringBuilder sb = new StringBuilder(StorageService.PUBLIC_PREFIX).append('/');
        appendSegment(sb, keyPrefix);
        appendSegment(sb, path);
        return sb.append(UUID.randomUUID().toString().replace("-", "")).append(extension(originalFilename)).toString();
    }

    /** 확장자로 정한 안전한 Content-Type — 클라이언트가 보낸 값은 믿지 않는다(html·svg 위조 → 저장형 XSS) */
    static String safeContentType(String key) {
        String ct = java.net.URLConnection.guessContentTypeFromName(key);
        if (ct == null && key.toLowerCase(Locale.ROOT).endsWith(".webp")) ct = "image/webp";
        return ct != null ? ct : "application/octet-stream";
    }

    private static void appendSegment(StringBuilder sb, String segment) {
        String clean = segment == null ? "" : segment.replaceAll("\\.\\.", "").replaceAll("^/+|/+$", "");
        if (!clean.isEmpty()) sb.append(clean).append('/');
    }

    private static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String cleaned = name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return cleaned.isEmpty() ? "" : "." + (cleaned.length() > 10 ? cleaned.substring(0, 10) : cleaned);
    }
}
