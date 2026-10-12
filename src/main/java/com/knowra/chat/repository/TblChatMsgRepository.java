package com.knowra.chat.repository;

import com.knowra.chat.entity.TblChatMsg;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TblChatMsgRepository extends JpaRepository<TblChatMsg, Long> {
}
