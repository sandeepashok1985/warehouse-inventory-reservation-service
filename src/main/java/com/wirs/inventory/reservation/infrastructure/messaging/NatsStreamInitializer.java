package com.wirs.inventory.reservation.infrastructure.messaging;

import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Creates the RESERVATIONS JetStream stream on startup if it does not already exist. */
@Component
@ConditionalOnProperty(name = "app.nats.enabled", havingValue = "true")
public class NatsStreamInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NatsStreamInitializer.class);
    private static final int STREAM_ALREADY_EXISTS_CODE = 10058;

    private final JetStreamManagement jsm;

    public NatsStreamInitializer(JetStreamManagement jsm) {
        this.jsm = jsm;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        StreamConfiguration config = StreamConfiguration.builder()
            .name("RESERVATIONS")
            .subjects("reservations.created", "reservations.confirmed", "reservations.cancelled")
            .retentionPolicy(RetentionPolicy.Limits)
            .storageType(StorageType.File)
            .replicas(1)
            .maxAge(Duration.ofDays(7))
            .maxBytes(10L * 1024 * 1024 * 1024)
            .maxMsgSize(1024L * 1024)
            .duplicateWindow(Duration.ofMinutes(2))
            .build();

        try {
            jsm.addStream(config);
            log.info("NATS stream RESERVATIONS created");
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == STREAM_ALREADY_EXISTS_CODE) {
                log.info("NATS stream RESERVATIONS already exists — skipping creation");
            } else {
                throw new IOException("Failed to create NATS stream", e);
            }
        }
    }
}
