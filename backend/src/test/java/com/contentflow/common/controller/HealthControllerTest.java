package com.contentflow.common.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test
    void returnsUpStatus() {
        HealthController controller = new HealthController();

        assertThat(controller.health()).containsEntry("status", "UP");
    }
}
