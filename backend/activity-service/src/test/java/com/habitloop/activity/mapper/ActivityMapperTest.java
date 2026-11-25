package com.habitloop.activity.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityMapperTest {

    private final ActivityMapper mapper = new ActivityMapper(new ObjectMapper());

    @Test
    void readScheduleReturnsStoredFields() {
        assertThat(mapper.readSchedule("{\"targetTime\":\"07:30\"}"))
                .containsEntry("targetTime", "07:30");
    }

    @Test
    void readScheduleSurfacesCorruptStoredData() {
        assertThatThrownBy(() -> mapper.readSchedule("{"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored habit schedule could not be read");
    }
}
