package com.knowra.chat.repository;

import com.knowra.chat.entity.TblChatRoomMbr;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TblChatRoomMbrRepository extends JpaRepository<TblChatRoomMbr, Long> {
    Optional<TblChatRoomMbr> findByRoomSnAndUserSn(long roomSn, long userSn);
    List<TblChatRoomMbr> findAllByRoomSn(long roomSn);
    List<TblChatRoomMbr> findAllByUserSn(long userSn);
}
