package com.javaee.aiservice.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextManagerTest {

    private RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private ContextManager contextManager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        contextManager = new ContextManager();
        ReflectionTestUtils.setField(contextManager, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(contextManager, "expiryHours", 24);
    }

    @Test
    void updateContextRefreshesConversationExpiry() {
        contextManager.updateContext("conversation-1", Map.of("answer", "done"));

        verify(hashOperations).putAll("ctx:conversation-1", Map.of("answer", "done"));
        verify(redisTemplate).expire("ctx:conversation-1", Duration.ofHours(24));
    }

    @Test
    void setContextValueRefreshesConversationExpiry() {
        contextManager.setContextValue("conversation-1", "documentId", "doc-1");

        verify(hashOperations).put("ctx:conversation-1", "documentId", "doc-1");
        verify(redisTemplate).expire("ctx:conversation-1", Duration.ofHours(24));
    }

    @Test
    void mergeContextRefreshesConversationExpiry() {
        when(hashOperations.entries("ctx:conversation-1"))
                .thenReturn(Map.of("existing", "value"));

        contextManager.mergeContext("conversation-1", Map.of("answer", "done"));

        verify(redisTemplate).delete("ctx:conversation-1");
        verify(hashOperations).putAll("ctx:conversation-1", Map.of(
                "existing", "value",
                "answer", "done"
        ));
        verify(redisTemplate).expire("ctx:conversation-1", Duration.ofHours(24));
    }
}
