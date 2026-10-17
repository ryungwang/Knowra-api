package com.knowra.user;

import java.util.regex.Pattern;

/**
 * 아이디·닉네임 규칙 — 서버가 단일 진실이다(화면 검사는 편의일 뿐, API 로 바로 보내면 건너뛴다).
 *
 * <p>⚠️ 아이디에 '@' 를 허용하지 않는다(예외: 이메일 가입에서 방금 인증한 본인 이메일 — AuthService.join).
 * 예전엔 화면에만 규칙이 있어서 API 로 남의 이메일을 아이디로 먼저 만들어 둘 수 있었고, 그 사람이 나중에
 * 같은 이메일로 소셜 로그인하면 그 계정으로 들어갔다.
 * 닉네임은 프로필 주소(/user/{닉네임})에 들어가므로 공백·'/'·'@' 를 막는다. 글자는 어느 언어든 된다.
 */
public final class UserRules {
    private UserRules() { }

    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9_]{3,20}$");
    /**
     * 어느 언어의 글자든(글자 p{L}, 결합 기호 p{M}, 숫자 p{N}) + . _ -, 2~30자. 전 세계 대상이라 한글·영문만 받던 걸 넓혔다.
     * ⚠️ 화면이 NFKC 로 맞춰 보낸다(전각 Ａ → A). 맞추지 않은 값은 받지 않는다 — 같은 모양의 다른 이름이 두 개 생기지 않게.
     */
    private static final Pattern NICKNAME = Pattern.compile("^[\\p{L}\\p{M}\\p{N}_.\\-]{2,30}$");
    /** 라틴 글자와 키릴·그리스 글자를 섞으면 거의 사칭이다(「аdmin」 의 а 는 키릴). 섞인 이름은 막는다. */
    private static final Pattern LATIN = Pattern.compile("\\p{IsLatin}"), LOOKALIKE = Pattern.compile("[\\p{IsCyrillic}\\p{IsGreek}]");
    /** 운영자·시스템처럼 보이는 이름 — 사칭 방지 */
    private static final Pattern RESERVED = Pattern.compile("(?i)^(admin|administrator|moderator|root|system|official|knowra|운영자|관리자|운영진|공지|管理者|運営|公式|管理员|管理員|官方)$");
    /** 탈퇴한 사람에게 붙이는 이름과 그 번역 — 사람이 고르면 탈퇴한 사람처럼 보이고, 화면이 이름을 바꿔 버린다(DeletedUser) */
    private static final Pattern DELETED = Pattern.compile("(?i)^(탈퇴회원|탈퇴한사용자|deleted[_.-]?(user)?|退会ユーザー|已注销用户|已註銷用戶)\\d*$");

    public static String loginIdError(String v) {
        if (v == null || !LOGIN_ID.matcher(v).matches()) return "아이디는 영문 소문자·숫자·밑줄 3~20자로 만들어 주세요.";
        if (RESERVED.matcher(v).matches()) return "쓸 수 없는 아이디예요.";
        return null;
    }

    /**
     * NFKC 로 맞춘 값인가 — 전각 「Ａ」, 원문자 「①」 처럼 모양만 다른 글자로 같은 이름을 또 만들지 못하게.
     * ⚠️ 한글 호환 자모(ㄱ·ㅋ 등, U+3130~318F)는 예외다. NFKC 가 이걸 첫가끝 자모로 바꿔 「ㅋㅋ」 닉네임이 전부 거절된다.
     */
    static boolean isNormalizedNick(String v) {
        String rest = v.replaceAll("[\u3130-\u318F]", "");
        return java.text.Normalizer.isNormalized(rest, java.text.Normalizer.Form.NFKC);
    }

    public static String nickNameError(String v) {
        if (v == null || !NICKNAME.matcher(v).matches()) return "닉네임은 글자·숫자와 . _ - 로 2~30자예요.";
        if (!isNormalizedNick(v)) return "닉네임에 쓸 수 없는 글자가 있어요.";
        if (LATIN.matcher(v).find() && LOOKALIKE.matcher(v).find()) return "로마자와 키릴·그리스 문자를 섞은 닉네임은 쓸 수 없어요.";
        if (RESERVED.matcher(v).matches() || DELETED.matcher(v).matches()) return "쓸 수 없는 닉네임이에요.";
        return null;
    }
}
