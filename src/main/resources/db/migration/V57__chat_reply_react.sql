-- ============================================================================
-- V57: 채팅 답장·반응
--
-- REPLY_TO_MSG_SN: 이 메시지가 답한 메시지(같은 방). 원 메시지가 지워져도 답장은 남고, 화면은 「지운 메시지」로 보인다.
-- tbl_chat_msg_react: 메시지 반응 — 한 사람이 한 메시지에 같은 반응은 한 번. 고른 몇 가지 이모지만 받는다(ChatService.REACTIONS).
-- 입력 중 표시·대화 안 검색은 저장하지 않는다(웹소켓으로 흘려보내고, 검색은 메시지 표를 그대로 찾는다).
-- ============================================================================
ALTER TABLE knowra_user.tbl_chat_msg ADD COLUMN IF NOT EXISTS REPLY_TO_MSG_SN BIGINT DEFAULT NULL;
COMMENT ON COLUMN knowra_user.tbl_chat_msg.REPLY_TO_MSG_SN IS '답한 메시지 (NULL = 답장 아님)';

CREATE TABLE IF NOT EXISTS knowra_user.tbl_chat_msg_react
(
    MSG_SN     BIGINT NOT NULL,
    USER_SN    BIGINT NOT NULL,
    EMOJI      VARCHAR(16) NOT NULL,
    CREATED_AT TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (MSG_SN, USER_SN, EMOJI),
    CONSTRAINT FK_CHAT_REACT_MSG  FOREIGN KEY (MSG_SN)  REFERENCES knowra_user.tbl_chat_msg (MSG_SN) ON DELETE CASCADE,
    CONSTRAINT FK_CHAT_REACT_USER FOREIGN KEY (USER_SN) REFERENCES knowra_user.tbl_user (USER_SN) ON DELETE CASCADE
);
COMMENT ON TABLE knowra_user.tbl_chat_msg_react IS '채팅 메시지 반응';
