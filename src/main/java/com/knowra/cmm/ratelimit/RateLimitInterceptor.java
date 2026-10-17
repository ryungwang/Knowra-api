package com.knowra.cmm.ratelimit;

import com.knowra.common.util.ComUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.knowra.cmm.i18n.Lang;
import com.knowra.cmm.i18n.Msg;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 쓰기·발송 창구의 도배 제한. 한도는 아래 표 <b>한 곳</b>에서 고친다.
 *
 * <p>로그인한 요청은 사람(userSn) 기준, 아니면 IP 기준으로 센다. 넘으면 HTTP 429 와 함께
 * 평소와 같은 모양의 본문({@code resultCode/resultMessage})을 돌려 화면이 그 문구를 띄운다.
 *
 * <p>값은 "정상 사용자는 절대 안 닿고, 스크립트는 바로 닿는" 선으로 잡았다. 특히 인증 코드
 * 발송은 문자 요금이 나가는 자리라 IP 기준으로 빡빡하게 둔다(대상 번호를 바꿔 가며 보내면
 * 대상별 1분 제한은 소용없다).
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 한도: {@code limit} 회 / {@code windowSec} 초. */
    record Rule(String name, int limit, long windowSec) {}

    static final Map<String, Rule> RULES = Map.ofEntries(
            // 글·댓글·대화
            Map.entry("/api/post/setPost",            new Rule("post", 15, 600)),
            Map.entry("/api/post/setPostCmt",         new Rule("cmt", 15, 60)),
            Map.entry("/api/chat/sendMsg",            new Rule("msg", 30, 60)),
            Map.entry("/api/chat/openDm",             new Rule("dm", 20, 600)),
            Map.entry("/api/chat/createGroup",        new Rule("group", 10, 3600)),   // 모르는 사람을 방에 몰아넣는 도배
            Map.entry("/api/chat/inviteGroup",        new Rule("group-inv", 30, 3600)),
            Map.entry("/api/community/setCommunity",  new Rule("comm", 5, 3600)),
            Map.entry("/api/community/event/save",    new Rule("event", 10, 600)),
            Map.entry("/api/community/invite/create", new Rule("invite", 20, 3600)),
            Map.entry("/api/community/modmail/create", new Rule("modmail", 5, 3600)),   // 운영진 문의함을 덮는 도배
            Map.entry("/api/tag/follow",              new Rule("tag-follow", 60, 600)),
            Map.entry("/api/chat/react",              new Rule("chat-react", 60, 60)),
            Map.entry("/api/appeal/submit",           new Rule("appeal", 10, 3600)),
            Map.entry("/api/copyright/notice",        new Rule("copyright", 10, 3600)),   // 로그인 없이 받으니 IP 로 묶인다
            Map.entry("/api/appeal/suspended",        new Rule("appeal", 10, 3600)),
            Map.entry("/api/chat/searchMsg",          new Rule("chat-search", 30, 60)),
            Map.entry("/api/translate/post",          new Rule("translate", 300, 3600)),   // 번역 한도(달마다)를 한 사람이 다 쓰지 못하게
            Map.entry("/api/translate/comments",      new Rule("translate-cmt", 300, 3600)),
            Map.entry("/api/translate/messages",      new Rule("translate-msg", 600, 3600)),   // 새 메시지가 올 때마다 그것만 묻는다
            Map.entry("/api/translate/titles",        new Rule("translate-ttl", 600, 3600)),   // 목록을 내릴 때마다 새로 보인 카드만 묻는다
            Map.entry("/api/community/modmail/reply",  new Rule("modmail-re", 30, 600)),
            Map.entry("/api/community/invite/accept", new Rule("invite-use", 30, 3600)),
            Map.entry("/api/community/wiki/page/save", new Rule("wiki", 30, 600)),
            Map.entry("/api/common/uploadImage",      new Rule("upload", 30, 600)),
            // 쓰기인데 표에서 빠져 있던 창구들 — 하나하나는 가볍지만 알림을 일으키거나(가입 신청·메시지 요청 응답)
            // 행을 끝없이 만든다(키워드·폴더·연재·임시 저장)
            Map.entry("/api/post/setPostCmtEdit",     new Rule("cmt-edit", 30, 60)),
            Map.entry("/api/community/setMember",     new Rule("join-req", 20, 600)),   // 승인제면 운영진에게 알림이 간다
            Map.entry("/api/community/updateCommunity", new Rule("comm-edit", 20, 600)),
            Map.entry("/api/chat/respondRequest",     new Rule("dm-resp", 60, 600)),
            Map.entry("/api/poll/vote",               new Rule("vote", 60, 60)),
            Map.entry("/api/safety/block",            new Rule("block", 30, 600)),
            Map.entry("/api/safety/mute",             new Rule("block", 30, 600)),
            Map.entry("/api/keyword/add",             new Rule("keyword", 20, 600)),
            Map.entry("/api/saveFolder/create",       new Rule("folder", 20, 600)),
            Map.entry("/api/series/create",           new Rule("series", 10, 600)),
            Map.entry("/api/draft/save",              new Rule("draft", 30, 600)),   // 자동 저장은 기기 안(localStorage) — 서버는 「임시 저장」 버튼만
            // 상대에게 알림이 가는 반응 — 눌렀다 뗐다로 알림 폭탄을 만들 수 있다
            Map.entry("/api/post/setPostLike",        new Rule("react", 60, 60)),
            Map.entry("/api/post/setPostCmtReact",    new Rule("react", 60, 60)),
            Map.entry("/api/user/setFollow",          new Rule("follow", 30, 60)),
            Map.entry("/api/safety/report",           new Rule("report", 10, 600)),
            // 서버가 바깥 주소를 대신 불러오는 창구
            Map.entry("/api/link/preview",            new Rule("link", 60, 60)),
            Map.entry("/api/link/image",              new Rule("link", 60, 60)),
            // 메일·문자가 나가는 창구 — IP 기준
            Map.entry("/api/auth/verify/send",        new Rule("verify", 10, 3600)),
            Map.entry("/api/auth/password/forgot",    new Rule("forgot", 5, 600)),
            Map.entry("/api/auth/join",               new Rule("join", 10, 3600)),
            // 인증·계정 — 대입·남용 창구
            Map.entry("/api/auth/verify/check",       new Rule("verify-check", 30, 600)),
            Map.entry("/api/auth/refresh",            new Rule("refresh", 60, 600)),
            Map.entry("/api/auth/login/2fa",          new Rule("2fa", 20, 600)),       // 확인표마다 5번 + IP 단위 상한
            Map.entry("/api/account/2fa/disable",     new Rule("2fa-mgmt", 10, 600)),
            Map.entry("/api/account/2fa/recovery",    new Rule("2fa-mgmt", 10, 600)),
            Map.entry("/api/auth/snsLogin",           new Rule("sns", 30, 600)),      // 매번 제공자에게 바깥 요청이 나간다
            Map.entry("/api/auth/password/reset",     new Rule("pw-reset", 10, 600)),
            Map.entry("/api/account/changeEmail",     new Rule("chg-email", 10, 600)), // 비밀번호 대입 창구
            Map.entry("/api/account/sessions/revoke", new Rule("sess", 30, 600)),
            Map.entry("/api/account/sns/link",        new Rule("sns-link", 20, 600)),   // 매번 제공자에게 바깥 요청이 나간다
            Map.entry("/api/account/export",          new Rule("export", 5, 3600)),   // 내 글·댓글·DM 을 통째로 읽는다
            Map.entry("/api/user/setUserInfo",        new Rule("profile", 20, 600)),
            Map.entry("/api/user/changePassword",     new Rule("chg-pw", 10, 600)),
            Map.entry("/api/user/deleteAccount",      new Rule("del-acct", 10, 600)),
            // 무거운 조회·발송
            Map.entry("/api/search",                  new Rule("search", 60, 60)),
            Map.entry("/api/search/suggest",          new Rule("suggest", 120, 60)),
            Map.entry("/api/push/test",               new Rule("push-test", 5, 600)),
            Map.entry("/api/digest/preview",          new Rule("digest", 20, 600)),
            // 화면 오류 접수 — 오류 루프 한 번에 DB 가 차지 않게
            Map.entry("/api/err/report",              new Rule("err", 20, 60))
    );

    private final RateLimiter limiter;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;
        Rule rule = RULES.get(req.getRequestURI());
        if (rule == null) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String who = auth != null && auth.getPrincipal() instanceof Long userSn
                ? "u:" + userSn : "ip:" + ComUtil.extractClientIp(req);
        String key = "api:" + rule.name() + ":" + who;
        if (limiter.tryHit(key, rule.limit(), rule.windowSec())) return true;

        long min = limiter.minutesLeft(key);
        // 응답을 직접 써서 TranslateResultAdvice 를 거치지 않는다 — 여기서 요청 언어로
        String lang = Lang.of(req);
        String msg = rule.windowSec() <= 60
                ? Msg.tl(lang, "너무 빠르게 요청하고 있어요. 잠시 뒤에 다시 시도해 주세요.")
                : Msg.tl(lang, "너무 빠르게 요청하고 있어요. {0}분 뒤에 다시 시도해 주세요.", min);
        res.setStatus(429);
        res.setHeader("Retry-After", String.valueOf(min * 60));
        res.setContentType("application/json;charset=UTF-8");
        res.getOutputStream().write(("{\"resultCode\":429,\"resultMessage\":\""
                + msg.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}").getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
