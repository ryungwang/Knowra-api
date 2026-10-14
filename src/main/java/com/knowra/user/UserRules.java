package com.knowra.user;

import java.util.regex.Pattern;

/**
 * 아이디·닉네임 규칙 — 서버가 단일 진실이다(화면 검사는 편의일 뿐, API 로 바로 보내면 건너뛴다).
 *
 * <p>⚠️ 아이디에 '@' 를 허용하지 않는다(예외: 이메일 가입에서 방금 인증한 본인 이메일 — AuthService.join).
 * 예전엔 화면에만 규칙이 있어서 API 로 남의 이메일을 아이디로 먼저 만들어 둘 수 있었고, 그 사람이 나중에
 * 같은 이메일로 소셜 로그인하면 그 계정으로 들어갔다.
 * 닉네임은 프로필 주소(/user/{닉네임})에 들어가므로 공백·'/'·'@' 를 막는다.
 */
public final class UserRules {
    private UserRules() { }

    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9_]{3,20}$");
    private static final Pattern NICKNAME = Pattern.compile("^[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9_.\\-]{2,30}$");   // 가입 화면 안내와 같게(한글·영문·숫자·. _ -, 30자)
    /** 운영자·시스템처럼 보이는 이름 — 사칭 방지 */
    private static final Pattern RESERVED = Pattern.compile("(?i)^(admin|administrator|root|system|knowra|운영자|관리자|운영진|공지)$");

    public static String loginIdError(String v) {
        if (v == null || !LOGIN_ID.matcher(v).matches()) return "아이디는 영문 소문자·숫자·밑줄 3~20자로 만들어 주세요.";
        if (RESERVED.matcher(v).matches()) return "쓸 수 없는 아이디예요.";
        return null;
    }

    public static String nickNameError(String v) {
        if (v == null || !NICKNAME.matcher(v).matches()) return "닉네임은 한글·영문·숫자와 . _ - 로 2~30자예요.";
        if (RESERVED.matcher(v).matches()) return "쓸 수 없는 닉네임이에요.";
        return null;
    }
}
