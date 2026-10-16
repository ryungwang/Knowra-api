package com.knowra.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserRulesTest {

    @Test
    void 어느_언어의_닉네임이든_된다() {
        for (String ok : new String[]{"deer", "사슴", "ㅋㅋ왕", "田中太郎", "さくら_01", "小明", "Müller", "José.dev", "Анна", "Νίκος", "محمد"})
            assertNull(UserRules.nickNameError(ok), ok);
    }

    @Test
    void 막는_닉네임() {
        for (String bad : new String[]{"a", "deer kim", "a/b", "a@b", "Ｄｅｅｒ", "①②", "аdmin", "admin", "管理者", "운영자"})
            assertNotNull(UserRules.nickNameError(bad), bad);
    }
}
