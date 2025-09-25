package com.example.cache.core;

import com.example.cache.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static com.example.cache.core.CacheKeys.invalidateChannel;

@Configuration
public class CacheInvalidationListener {
    
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationListener.class);

    @Bean
    public ChannelTopic invalidateTopic() {
        return new ChannelTopic(invalidateChannel());
    }

    @Bean
    public RedisMessageListenerContainer listenerContainer(RedisConnectionFactory factory,
                                                           CacheManager cacheManager,
                                                           ChannelTopic topic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        Cache l1 = cacheManager.getCache(CacheConfig.L1_CACHE);
        
        container.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                String key = new String(message.getBody());
                log.debug("Received L1 invalidation message for key: {}", key);
                if (l1 != null) {
                    l1.evict(key);
                    log.debug("Evicted key from L1 cache: {}", key);
                }
            }
        }, topic);
        
        return container;
    }
}