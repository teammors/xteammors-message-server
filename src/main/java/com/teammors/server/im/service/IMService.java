package com.teammors.server.im.service;

import com.teammors.server.im.cluster.ClusterManager;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.handler.EventHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IMService {

    private static final Logger log = LoggerFactory.getLogger(IMService.class);

    private final Map<String, EventHandler> eventHandlerMap;
    private final ChannelManager channelManager;
    private final StringRedisTemplate redisTemplate;
    private final ClusterManager clusterManager;
    
    // Virtual Thread Executor for dispatching events
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public IMService(List<EventHandler> eventHandlers, ChannelManager channelManager, StringRedisTemplate redisTemplate, ClusterManager clusterManager) {
        this.eventHandlerMap = eventHandlers.stream()
                .collect(Collectors.toMap(EventHandler::getEventId, Function.identity()));
        this.channelManager = channelManager;
        this.redisTemplate = redisTemplate;
        this.clusterManager = clusterManager;
    }

    public void handleEvent(ChannelHandlerContext ctx, Message msg) {
        String eventId = msg.getEventId();
        if (eventId == null) return;

        EventHandler handler = eventHandlerMap.get(eventId);
        if (handler != null) {
            // Dispatch to Virtual Thread
            virtualThreadExecutor.submit(() -> {
                try {
                    handler.handle(ctx, msg);
                } catch (Exception e) {
                    log.error("Error handling event {}", eventId, e);
                }
            });
        } else {
            log.warn("Unknown event: {}", eventId);
        }
    }
    
    public void executeAsync(Runnable task) {
        virtualThreadExecutor.submit(task);
    }

    public void removeChannel(Channel channel) {
        // 1. Remove from ChannelManager (Local memory)
        channelManager.unbind(channel);
        
        // 2. Remove from Redis Session & Reverse Index
        if (channel.hasAttr(ChannelManager.ATTR_USER_ID) && channel.hasAttr(ChannelManager.ATTR_DEVICE_ID)) {
            String uid = channel.attr(ChannelManager.ATTR_USER_ID).get();
            String deviceId = channel.attr(ChannelManager.ATTR_DEVICE_ID).get();
            
            if (uid != null && deviceId != null) {
                try {
                    // Remove from Global Session
                    redisTemplate.opsForHash().delete("session:" + uid, deviceId);
                    
                    // Remove from Cluster Reverse Index (important for consistency)
                    clusterManager.unregisterSession(uid, deviceId);
                    
                    log.info("Cleaned up Redis session for user {} device {}", uid, deviceId);
                } catch (Exception e) {
                    log.error("Failed to clean up Redis session for user {}", uid, e);
                }
            }
        }
    }
}
