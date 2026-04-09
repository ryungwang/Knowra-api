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

    @Transactional(readOnly = true)
    public List<TblUserNotif> getAll(Long userSn) {
        return notifRepository.findAllByUserSnAndIsDisplayOrderByFrstCrtDtDesc(userSn, "Y");
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userSn) {
        return notifRepository.countByUserSnAndIsRead(userSn, "N");
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
