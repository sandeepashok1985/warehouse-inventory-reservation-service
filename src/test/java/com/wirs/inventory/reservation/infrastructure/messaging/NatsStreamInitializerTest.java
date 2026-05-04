package com.wirs.inventory.reservation.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class NatsStreamInitializerTest {

    @Mock
    private JetStreamManagement jsm;

    @Mock
    private ApplicationArguments args;

    @InjectMocks
    private NatsStreamInitializer initializer;

    @Test
    void run_streamDoesNotExist_createsStream() throws Exception {
        initializer.run(args);

        verify(jsm).addStream(any(StreamConfiguration.class));
    }

    @Test
    void run_streamAlreadyExists_logsAndContinues() throws Exception {
        // JetStreamApiException with error code 10058 = stream already exists
        var exception = org.mockito.Mockito.mock(JetStreamApiException.class);
        org.mockito.Mockito.when(exception.getApiErrorCode()).thenReturn(10058);
        doThrow(exception).when(jsm).addStream(any(StreamConfiguration.class));

        assertThatCode(() -> initializer.run(args)).doesNotThrowAnyException();
    }

    @Test
    void run_unexpectedJetStreamException_propagates() throws Exception {
        var exception = org.mockito.Mockito.mock(JetStreamApiException.class);
        org.mockito.Mockito.when(exception.getApiErrorCode()).thenReturn(500);
        doThrow(exception).when(jsm).addStream(any(StreamConfiguration.class));

        assertThatThrownBy(() -> initializer.run(args))
            .isInstanceOf(java.io.IOException.class)
            .hasCause(exception);
    }
}
