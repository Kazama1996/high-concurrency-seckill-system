package com.kazama.redis_cache_demo.notification.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kazama.redis_cache_demo.infra.idempotency.NotificationIdempotencyService;
import com.kazama.redis_cache_demo.seckill.event.SeckillOrderEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class SeckillOrderNotificationConsumerTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private NotificationIdempotencyService idempotencyService;
    @Mock private Acknowledgment acknowledgment;

    @InjectMocks private SeckillOrderNotificationConsumer consumer;

    private static final String PAYLOAD = "{\"id\":1}";

    private SeckillOrderEvent sampleEvent() {
        return new SeckillOrderEvent(1L, 10L, 100L, 42L, 1,
                BigDecimal.valueOf(100), BigDecimal.valueOf(9.9));
    }

    @Test
    void sendMockEmail_deserializeFails_acknowledgesAndSkipsIdempotencyCheck() throws JsonProcessingException {
        when(objectMapper.readValue(PAYLOAD, SeckillOrderEvent.class))
                .thenThrow(JsonMappingException.class);

        assertThrows(JsonMappingException.class , ()->consumer.sendMockEmail(PAYLOAD, acknowledgment));

        verifyNoInteractions(idempotencyService);
        verifyNoInteractions(acknowledgment);
    }

    @Test
    void sendMockEmail_duplicateNotification_acknowledgesAndSkipsMarkProcessed() throws JsonProcessingException {
        SeckillOrderEvent event = sampleEvent();
        when(objectMapper.readValue(PAYLOAD, SeckillOrderEvent.class)).thenReturn(event);
        when(idempotencyService.isAlreadyProcessed(event.id())).thenReturn(true);

        consumer.sendMockEmail(PAYLOAD, acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(idempotencyService, never()).markProcessed(any());
    }

    @Test
    void sendMockEmail_happyPath_marksProcessedThenAcknowledges() throws JsonProcessingException {
        SeckillOrderEvent event = sampleEvent();
        when(objectMapper.readValue(PAYLOAD, SeckillOrderEvent.class)).thenReturn(event);
        when(idempotencyService.isAlreadyProcessed(event.id())).thenReturn(false);

        consumer.sendMockEmail(PAYLOAD, acknowledgment);

        InOrder inOrder = inOrder(idempotencyService, acknowledgment);
        inOrder.verify(idempotencyService).markProcessed(event.id());
        inOrder.verify(acknowledgment).acknowledge();
    }
}
