package com.wirs.inventory.reservation.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@Tag("unit")
class NatsConfigTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(NatsConfig.class);

    @Test
    void natsConfig_disabled_beansNotCreated() {
        var runner = new ApplicationContextRunner()
            .withPropertyValues("app.nats.enabled=false")
            .withUserConfiguration(NatsConfig.class);

        runner.run(context -> {
            assertThat(context.containsBean("natsConfig"))
                .as("NatsConfig should not be loaded when app.nats.enabled=false")
                .isFalse();
        });
    }
}
