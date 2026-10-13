package com.knowra.user.service;

import com.knowra.user.entity.TblUserNotif;
import com.knowra.user.repository.TblUserNotifRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotifService {

    private final TblUserNotifRepository notifRepository;

    /**
     * 연결 직후 보내는 최근 알림 — 30개까지. 예전엔 지우지 않은 알림을 <b>전부</b> 실어 보냈다
     * (오래 쓴 사람일수록 연결이 무거워진다). 더 오래된 건 알림 페이지가 REST 로 쪽 단위로 읽는다.
     */
    @Transactional(readOnly = true)
    public List<TblUserNotif> getAll(Long userSn) {
        return notifRepository.findTop30ByUserSnAndIsDisplayOrderByNotifSnDesc(userSn, "Y");
    }

    /** 안 읽은 수 — 지운(숨긴) 알림은 세지 않는다. 30개 밖의 안 읽은 것까지 센 진짜 숫자다. */
    @Transactional(readOnly = true)
    public long countUnread(Long userSn) {
        return notifRepository.countByUserSnAndIsReadAndIsDisplay(userSn, "N", "Y");
    }

    @Transactional
    public void markRead(Long notifSn, Long userSn) {
        notifRepository.markRead(notifSn, userSn);
    }

    @Transactional
    public void markAllRead(Long userSn) {
        notifRepository.markAllRead(userSn);
    }

    @Transactional
    public void dismiss(Long notifSn, Long userSn) {
        notifRepository.dismiss(notifSn, userSn);
    }

    @Transactional
    public void dismissAll(Long userSn) {
        notifRepository.dismissAll(userSn);
    }
}
