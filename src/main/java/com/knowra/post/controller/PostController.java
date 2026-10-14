package com.knowra.post.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.post.entity.TblPost;
import com.knowra.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 게시글 API. 전체 공개 글과 커뮤니티 글이 같은 엔드포인트를 쓴다 — 커뮤니티 글은
 * 작성 시 {@code commSn} 을 함께 보내면 된다. 커뮤니티 게시판 목록만 별도다.
 */
@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping("/setPost")
    public ResultVO setPost(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPost(params, me);
    }

    /** 이 글 알림 끄기/켜기 — body: postSn, mute */
    @PostMapping("/setPostMute")
    public ResultVO setPostMute(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostMute(params, me);
    }

    /** 수정 기록 — params: postSn */
    @GetMapping("/getPostHistory")
    public ResultVO getPostHistory(@RequestParam Map<String, Object> params, @CurrentUser Long me) {
        return postService.getPostHistory(params, me);
    }

    @GetMapping("/getPost")
    public ResultVO getPost(@ModelAttribute TblPost tblPost, @CurrentUser Long me) {
        return postService.getPost(tblPost, me);
    }

    /** 내 예약 글 — 내 프로필 「예약된 글」 */
    @GetMapping("/getMyScheduled")
    public ResultVO getMyScheduled(@CurrentUser Long me) {
        return postService.getMyScheduled(me);
    }

    /** 예약 글: 지금 올리기 / 시각 바꾸기. body: postSn, action(PUBLISH|RESCHEDULE), rsrvAt */
    @PostMapping("/setReservation")
    public ResultVO setReservation(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.updateReservation(params, me);
    }

    /** 커뮤니티 게시판 목록 — params: commSn, listTyp(LATEST|POPULAR|NOTICE), cursor | page */
    @PostMapping("/getCommPostList")
    public ResultVO getCommPostList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.getCommPostList(params, me);
    }

    @PostMapping("/viewPost")
    public ResultVO viewPost(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.viewPost(params, me);
    }

    @PostMapping("/setPostLike")
    public ResultVO setPostLike(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostLike(params, me);
    }

    /** body: postSn, pin — 커뮤니티 운영진만 */
    @PostMapping("/setPostPin")
    public ResultVO setPostPin(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostPin(params, me);
    }

    @PostMapping("/setPostCmt")
    public ResultVO setPostCmt(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostCmt(params, me);
    }

    @PostMapping("/setPostCmtEdit")
    public ResultVO setPostCmtEdit(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostCmtEdit(params, me);
    }

    @PostMapping("/setPostCmtDel")
    public ResultVO setPostCmtDel(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostCmtDel(params, me);
    }

    @PostMapping("/setPostCmtReact")
    public ResultVO setPostCmtReact(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostCmtReact(params, me);
    }

    @PostMapping("/setPostDel")
    public ResultVO setPostDel(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostDel(params, me);
    }

    @PostMapping("/getPostCmtList")
    public ResultVO getPostCmtList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.getPostCmtList(params, me);
    }

    @PostMapping("/setPostSave")
    public ResultVO setPostSave(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostSave(params, me);
    }

    @PostMapping("/getPostSaveList")
    public ResultVO getPostSaveList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.getPostSaveList(params, me);
    }

    /** 댓글 고정·풀기 — body: postSn, postCmtSn(없으면 풀기) */
    @PostMapping("/setCmtPin")
    public ResultVO setCmtPin(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setCmtPin(params, me);
    }

    @PostMapping("/getRecentViewList")
    public ResultVO getRecentViewList(@CurrentUser Long me) {
        return postService.getRecentViewList(me);
    }

    @PostMapping("/clearRecentView")
    public ResultVO clearRecentView(@CurrentUser Long me) {
        return postService.clearRecentView(me);
    }
}
