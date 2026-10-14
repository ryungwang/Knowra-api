package com.knowra.chat.repository;

import com.knowra.chat.entity.TblChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TblChatRoomRepository extends JpaRepository<TblChatRoom, Long> {
    Optional<TblChatRoom> findByEventSn(Long eventSn);

    Optional<TblChatRoom> findByDmKey(String dmKey);
}
