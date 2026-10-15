package com.knowra.cmm.i18n;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국어 조사 고르기. 원문 템플릿에 「을(를)」「이(가)」「은(는)」「과(와)」처럼 두 꼴을 적어 두면, 앞말의 받침을 보고 하나로 고른다.
 * 번역 표의 키는 이 표시가 붙은 원문 그대로다 — 다른 언어는 조사가 없으니 번역문에는 표시가 없다.
 */
public final class Josa {

    private static final Pattern MARK = Pattern.compile("(을\\(를\\)|이\\(가\\)|은\\(는\\)|과\\(와\\))");

    private Josa() {}

    public static String resolve(String s) {
        if (s == null || !s.contains("(")) return s;
        Matcher m = MARK.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String mark = m.group(1);
            boolean batchim = hasBatchim(s, m.start());
            String pick = batchim ? mark.substring(0, 1) : mark.substring(2, 3);
            m.appendReplacement(out, pick);
        }
        m.appendTail(out);
        return out.toString();
    }

    /** 표시 앞에서 거꾸로 첫 한글(또는 숫자)을 찾아 받침이 있는지. 「첫 글」·외 3개 처럼 따옴표·숫자가 끼어도 */
    static boolean hasBatchim(String s, int before) {
        for (int k = before - 1; k >= 0; k--) {
            char ch = s.charAt(k);
            if (ch >= 0xAC00 && ch <= 0xD7A3) return (ch - 0xAC00) % 28 != 0;
            if (Character.isDigit(ch)) return "013678".indexOf(ch) >= 0;
            if (Character.isLetter(ch)) return false;   // 영문 이름 뒤는 받침 없음으로(「deer가」)
        }
        return false;
    }
}
