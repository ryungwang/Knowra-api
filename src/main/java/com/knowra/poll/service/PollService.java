package com.knowra.poll.service;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.poll.entity.TblPoll;
import com.knowra.poll.entity.TblPollOption;
import com.knowra.poll.entity.TblPollVote;
import com.knowra.poll.repository.TblPollOptionRepository;
import com.knowra.poll.repository.TblPollRepository;
import com.knowra.poll.repository.TblPollVoteRepository;
import com.knowra.post.service.PostQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static com.knowra.post.service.PostQuery.POST;

/**
 * 투표. 글 하나에 하나, 글을 쓸 때 같이 만든다(수정에서 선택지를 바꾸지 않는다 — 이미 들어온 표의
 * 의미가 바뀐다).
 *
 * <p><b>결과 공개 규칙</b>: 투표했거나, 마감됐거나, 글쓴이일 때만 표 수를 준다. 그 전엔 null —
 * 중간 결과를 보고 다수 쪽으로 쏠리는 걸 막는다(밴드왜건). 투표자 수는 늘 준다.
 */
@Service
@RequiredArgsConstructor
public class PollService {

    public static final int MIN_OPTIONS = 2, MAX_OPTIONS = 6, MAX_LABEL = 80, MAX_DAYS = 30;

    private final TblPollRepository pollRepo;
    private final TblPollOptionRepository optionRepo;
    private final TblPollVoteRepository voteRepo;
    private final PostQuery postQuery;

    /** 글쓰기 요청의 poll 을 검사·정리한다. 없으면 null. 글을 저장하기 <b>전에</b> 부른다. */
    public Draft validate(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return null;
        Object opts = m.get("options");
        if (!(opts instanceof List<?> list)) throw new IllegalArgumentException("투표 선택지가 없어요.");
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (Object o : list) {
            String s = o == null ? "" : o.toString().trim();
            if (s.isEmpty()) continue;
            if (s.length() > MAX_LABEL) throw new IllegalArgumentException("선택지는 " + MAX_LABEL + "자까지 쓸 수 있어요.");
            labels.add(s);
        }
        if (labels.size() < MIN_OPTIONS) throw new IllegalArgumentException("서로 다른 선택지를 2개 이상 넣어 주세요.");
        if (labels.size() > MAX_OPTIONS) throw new IllegalArgumentException("선택지는 " + MAX_OPTIONS + "개까지예요.");
        int days = 7;
        if (m.get("days") != null) {
            try { days = Integer.parseInt(m.get("days").toString()); } catch (NumberFormatException e) { days = 7; }
        }
        days = Math.max(1, Math.min(MAX_DAYS, days));
        boolean multi = Boolean.parseBoolean(String.valueOf(m.get("multi")));
        return new Draft(new ArrayList<>(labels), multi, days);
    }

    public record Draft(List<String> labels, boolean multi, int days) {}

    @Transactional
    public void create(long postSn, Draft d) {
        TblPoll p = pollRepo.save(TblPoll.builder().postSn(postSn).multiYn(d.multi() ? "Y" : "N")
                .endsAt(LocalDateTime.now().plusDays(d.days())).build());
        for (int i = 0; i < d.labels().size(); i++) {
            optionRepo.save(TblPollOption.builder().pollSn(p.getPollSn()).ord(i).label(d.labels().get(i)).build());
        }
    }

    /** 글 상세에 붙일 투표. 없으면 null. */
    public Map<String, Object> view(long postSn, long authorSn, Long me) {
        TblPoll p = pollRepo.findByPostSn(postSn).orElse(null);
        if (p == null) return null;
        List<TblPollOption> opts = optionRepo.findAllByPollSnOrderByOrdAsc(p.getPollSn());
        Set<Long> mine = new HashSet<>();
        if (me != null) voteRepo.findAllByPollSnAndUserSn(p.getPollSn(), me).forEach(v -> mine.add(v.getOptionSn()));
        boolean closed = !p.getEndsAt().isAfter(LocalDateTime.now());
        boolean reveal = closed || !mine.isEmpty() || (me != null && me == authorSn);

        List<Map<String, Object>> list = new ArrayList<>();
        for (TblPollOption o : opts) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("optionSn", o.getOptionSn());
            m.put("label", o.getLabel());
            m.put("voteCnt", reveal ? o.getVoteCnt() : null);
            list.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pollSn", p.getPollSn());
        r.put("multi", "Y".equals(p.getMultiYn()));
        r.put("endsAt", p.getEndsAt());
        r.put("closed", closed);
        r.put("revealed", reveal);
        r.put("voterCnt", voteRepo.countVoters(p.getPollSn()));
        r.put("totalVotes", reveal ? opts.stream().mapToInt(TblPollOption::getVoteCnt).sum() : null);
        r.put("myVotes", mine);
        r.put("options", list);
        return r;
    }

    /** 피드 카드용 — 어떤 글에 투표가 있나. */
    public Set<Long> postsWithPoll(Collection<Long> postSns) {
        if (postSns.isEmpty()) return Set.of();
        Set<Long> s = new HashSet<>();
        pollRepo.findAllByPostSnIn(postSns).forEach(p -> s.add(p.getPostSn()));
        return s;
    }

    /**
     * 투표·표 바꾸기·취소(optionSns 비우면 취소). params: pollSn, optionSns[].
     * 볼 수 없는 글(비공개 커뮤니티·차단·숨김)의 투표에는 못 들어간다 — 글 조회와 같은 조건.
     */
    @Transactional
    public ResultVO vote(Map<String, Object> params, Long me) {
        ResultVO r = new ResultVO();
        try {
            long pollSn = Long.parseLong(String.valueOf(params.get("pollSn")));
            TblPoll p = pollRepo.findById(pollSn).orElseThrow(() -> new IllegalArgumentException("투표를 찾을 수 없어요."));
            if (postQuery.base().where(POST.postSn.eq(p.getPostSn()), PostQuery.alive(), PostQuery.visibleTo(me)).fetchFirst() == null)
                throw new IllegalArgumentException("투표를 찾을 수 없어요.");
            if (!p.getEndsAt().isAfter(LocalDateTime.now())) throw new IllegalArgumentException("마감된 투표예요.");

            Set<Long> valid = new HashSet<>();
            optionRepo.findAllByPollSnOrderByOrdAsc(pollSn).forEach(o -> valid.add(o.getOptionSn()));
            LinkedHashSet<Long> picked = new LinkedHashSet<>();
            Object raw = params.get("optionSns");
            if (raw instanceof List<?> l) for (Object o : l) picked.add(Long.parseLong(String.valueOf(o)));
            if (!valid.containsAll(picked)) throw new IllegalArgumentException("선택지가 올바르지 않아요.");
            if (!"Y".equals(p.getMultiYn()) && picked.size() > 1) throw new IllegalArgumentException("하나만 고를 수 있어요.");

            voteRepo.deleteMine(pollSn, me);
            for (Long opt : picked) voteRepo.save(TblPollVote.builder().pollSn(pollSn).optionSn(opt).userSn(me).build());
            voteRepo.flush();
            voteRepo.recount(pollSn);

            long authorSn = postQuery.base().where(POST.postSn.eq(p.getPostSn())).fetchFirst().get(POST).getUserSn();
            r.putResult("poll", view(p.getPostSn(), authorSn, me));
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return r;
    }
}
