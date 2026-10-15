package com.knowra.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventReminderTest {

    @Test
    void 세_시간_뒤_모임은_하루전_알림이_가지_않는다() {
        assertThat(CommEventService.reminderType(180, false, false)).isNull();
    }

    @Test
    void 하루전_알림은_20에서_24시간_사이에_한번() {
        assertThat(CommEventService.reminderType(23 * 60, false, false)).isEqualTo("EVENT_TOMORROW");
        assertThat(CommEventService.reminderType(20 * 60, false, false)).isEqualTo("EVENT_TOMORROW");
        assertThat(CommEventService.reminderType(21 * 60, true, false)).isNull();
        assertThat(CommEventService.reminderType(19 * 60, false, false)).isNull();
    }

    @Test
    void 한시간전_알림은_한번() {
        assertThat(CommEventService.reminderType(45, false, false)).isEqualTo("EVENT_SOON");
        assertThat(CommEventService.reminderType(45, true, false)).isEqualTo("EVENT_SOON");
        assertThat(CommEventService.reminderType(30, true, true)).isNull();
    }
}
