package com.knowra.user.listener;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JosaTest {
    @Test
    void 을_를() {
        assertThat(NotificationEventListener.objectJosa("「첫 글」")).isEqualTo("을");
        assertThat(NotificationEventListener.objectJosa("「작은 박수」")).isEqualTo("를");
        assertThat(NotificationEventListener.objectJosa("「첫 글」 외 3개")).isEqualTo("를");
        assertThat(NotificationEventListener.objectJosa("「이웃 열 명」")).isEqualTo("을");
    }

    @Test
    void 이_가() {
        assertThat(NotificationEventListener.subjectJosa("번개 모임")).isEqualTo("이");
        assertThat(NotificationEventListener.subjectJosa("독서 스터디")).isEqualTo("가");
    }
}
