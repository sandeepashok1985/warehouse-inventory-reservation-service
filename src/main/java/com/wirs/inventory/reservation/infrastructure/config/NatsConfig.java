package com.wirs.inventory.reservation.infrastructure.config;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates NATS JetStream beans when app.nats.enabled=true. */
@Configuration
@ConditionalOnProperty(name = "app.nats.enabled", havingValue = "true")
public class NatsConfig {

    private static final Logger log = LoggerFactory.getLogger(NatsConfig.class);

    /**
     * Creates the NATS connection with indefinite reconnect and connection event logging.
     */
    @Bean
    public Connection natsConnection(@Value("${app.nats.url}") String natsUrl)
            throws IOException, InterruptedException {
        Options options = new Options.Builder()
            .server(natsUrl)
            .connectionListener((conn, type) ->
                log.info("NATS connection event: {}", type))
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        return Nats.connect(options);
    }

    /**
     * Provides the JetStream context from the established connection.
     * Used by {@link NatsEventPublisher} to publish messages with deduplication.
     */
    @Bean
    public JetStream jetStream(Connection connection) throws IOException {
        return connection.jetStream();
    }

    /**
     * Provides the JetStream management context for stream lifecycle operations.
     * Used by {@link com.wirs.inventory.reservation.infrastructure.messaging.NatsStreamInitializer}
     * to create the RESERVATIONS stream on startup.
     */
    @Bean
    public JetStreamManagement jetStreamManagement(Connection connection) throws IOException {
        return connection.jetStreamManagement();
    }
}
