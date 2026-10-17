package com.knowra.safety.filter;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 금칙어 비교 — 여러 언어에서 같은 규칙으로.
 *
 * <p><b>비교 방식은 낱말의 글자가 정한다.</b>
 * <ul>
 *   <li><b>들어 있으면(PART)</b> — 한글·한자·가나·타이 글자가 있는 낱말. 띄어쓰기가 없거나 조사가 붙는 말이라
 *       낱말 경계가 없다. 공백·기호를 지우고 부분 비교한다(「시 . 발」도 잡는다).</li>
 *   <li><b>낱말로 있으면(WORD)</b> — 그 밖(영어·러시아어 등 띄어 쓰는 말). 여기서 부분 비교를 하면
 *       「ass」가 class·pass·assistant 를 막는다. 그래서 낱말 단위로 보고, 한 글자씩 띄운
 *       「f u c k」는 이어 붙여 본다. 변형(asshole 등)은 관리자가 따로 넣는다.</li>
 * </ul>
 *
 * <p>양쪽(글·금칙어) 다 같은 정리를 거친다 — 그래야 어느 쪽에 어떤 글자가 와도 맞는다.
 * <ul>
 *   <li>NFKC: 전각 ｆｕｃｋ·합자 ﬁ 를 보통 글자로</li>
 *   <li>라틴 글자의 악센트만 지운다: fück → fuck. 가나의 탁점은 건드리지 않는다(が 가 か 가 된다)</li>
 *   <li>키릴·그리스 닮은꼴 → 라틴: fuсk(с 는 키릴) → fuck</li>
 * </ul>
 * 낱말 비교에만 더 하는 것: 글자 사이 숫자·기호 치환(sh1t, @ss, $hit)과 같은 글자 3번 이상 반복 줄이기(fuuuck).
 */
final class BannedWords {

    enum Mode { PART, WORD }

    record Entry(String word, Mode mode, String key) {}

    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern LATIN_MARKS = Pattern.compile("(?<=\\p{IsLatin})\\p{Mn}+");
    private static final Pattern NOT_TEXT = Pattern.compile("[^\\p{L}\\p{N}\\p{M}]+");
    /** 띄어 쓰지 않는 글자 — 하나라도 있으면 PART */
    private static final Pattern UNSPACED = Pattern.compile("[\\p{IsHangul}\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsThai}\\p{IsLao}\\p{IsKhmer}\\p{IsMyanmar}]");
    /** 낱말 비교의 낱말 — 띄어 쓰지 않는 글자는 구분자로 본다(「这是fuck的」에서 fuck 을 꺼낸다) */
    private static final Pattern TOKEN = Pattern.compile(
            "[[\\p{L}\\p{N}\\p{M}]&&[^\\p{IsHangul}\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsThai}\\p{IsLao}\\p{IsKhmer}\\p{IsMyanmar}]]+");
    /** 글자에 붙은 @ $ 는 a s 로(@ss, $hit), 글자 사이 ! 는 i 로(sh!t) — 「hello!」 끝의 ! 는 그대로 */
    private static final Pattern SYMBOL_BEFORE = Pattern.compile("[@$](?=\\p{L})|(?<=\\p{L})[@$!](?=\\p{L})");
    private static final Pattern REPEAT = Pattern.compile("(.)\\1{2,}");
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");

    private static final Map<Character, Character> LOOKALIKE = Map.ofEntries(
            // 키릴
            Map.entry('а', 'a'), Map.entry('е', 'e'), Map.entry('о', 'o'), Map.entry('р', 'p'), Map.entry('с', 'c'),
            Map.entry('у', 'y'), Map.entry('х', 'x'), Map.entry('і', 'i'), Map.entry('ј', 'j'), Map.entry('ѕ', 's'),
            Map.entry('һ', 'h'), Map.entry('ԁ', 'd'), Map.entry('к', 'k'), Map.entry('ӏ', 'l'),
            // 그리스
            Map.entry('α', 'a'), Map.entry('ο', 'o'), Map.entry('ρ', 'p'), Map.entry('ν', 'v'), Map.entry('τ', 't'),
            Map.entry('ι', 'i'), Map.entry('κ', 'k'), Map.entry('υ', 'u'), Map.entry('ε', 'e'));
    private static final Map<Character, Character> LEET = Map.of(
            '0', 'o', '1', 'i', '3', 'e', '4', 'a', '5', 's', '7', 't', '8', 'b', '@', 'a', '$', 's', '!', 'i');

    private final List<Entry> entries;

    private BannedWords(List<Entry> entries) { this.entries = entries; }

    /** 관리자 목록에서 — 비교할 게 남지 않는 낱말(기호만, 한 글자)은 뺀다 */
    static BannedWords of(List<String> words) {
        List<Entry> list = new ArrayList<>();
        for (String w : words) {
            if (w == null || glued(w).length() < 2) continue;
            Mode mode = modeOf(w);
            String key = mode == Mode.PART ? glued(w) : String.join(" ", tokens(w));
            if (!key.isBlank()) list.add(new Entry(w, mode, key));
        }
        return new BannedWords(List.copyOf(list));
    }

    static Mode modeOf(String word) { return UNSPACED.matcher(word).find() ? Mode.PART : Mode.WORD; }

    /** 걸린 금칙어(관리자가 넣은 그대로), 없으면 null */
    String find(String text) {
        if (text == null || text.isBlank() || entries.isEmpty()) return null;
        String glued = null, spaced = null;
        for (Entry e : entries) {
            if (e.mode() == Mode.PART) {
                if (glued == null) glued = glued(text);
                if (glued.contains(e.key())) return e.word();
            } else {
                if (spaced == null) spaced = " " + String.join(" ", tokens(text)) + " ";
                if (spaced.contains(" " + e.key() + " ")) return e.word();
            }
        }
        return null;
    }

    /** 공백·기호를 다 지운 비교용 글 */
    static String glued(String s) { return NOT_TEXT.matcher(clean(s)).replaceAll(""); }

    /** 낱말 비교용 낱말들 — 숫자·기호 치환, 반복 줄이기, 한 글자씩 띄운 것 이어 붙이기 */
    static List<String> tokens(String s) {
        String c = SYMBOL_BEFORE.matcher(clean(s)).replaceAll(m -> String.valueOf(LEET.get(m.group().charAt(0))));
        List<String> raw = new ArrayList<>();
        Matcher m = TOKEN.matcher(c);
        while (m.find()) {
            String tok = m.group();
            // 글자가 섞인 낱말만 숫자를 글자로 — 「2024」·「88」 같은 숫자만 있는 건 그대로 둔다
            if (HAS_LETTER.matcher(tok).find()) tok = leet(tok);
            raw.add(REPEAT.matcher(tok).replaceAll("$1"));
        }
        List<String> out = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        int runLen = 0;
        for (String tok : raw) {
            if (tok.codePointCount(0, tok.length()) == 1) { run.append(tok); runLen++; continue; }
            flush(out, run, runLen);
            runLen = 0;
            out.add(tok);
        }
        flush(out, run, runLen);
        return out;
    }

    private static void flush(List<String> out, StringBuilder run, int runLen) {
        if (runLen == 0) return;
        if (runLen == 1) out.add(run.toString());
        else out.add(REPEAT.matcher(run).replaceAll("$1"));   // 「f u u u c k」
        run.setLength(0);
    }

    private static String leet(String tok) {
        StringBuilder b = new StringBuilder(tok.length());
        for (char ch : tok.toCharArray()) b.append(LEET.getOrDefault(ch, ch));
        return b.toString();
    }

    /** 태그 제거 · NFKC · 소문자 · 라틴 악센트 제거 · 닮은꼴 → 라틴 */
    private static String clean(String s) {
        String t = TAGS.matcher(s).replaceAll(" ").replace("&nbsp;", " ");
        t = Normalizer.normalize(t, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        t = Normalizer.normalize(LATIN_MARKS.matcher(Normalizer.normalize(t, Normalizer.Form.NFD)).replaceAll(""), Normalizer.Form.NFC);
        StringBuilder b = new StringBuilder(t.length());
        for (char ch : t.toCharArray()) b.append(LOOKALIKE.getOrDefault(ch, ch));
        return b.toString();
    }
}
