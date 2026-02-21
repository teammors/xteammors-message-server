package com.teammors.server.im.cluster;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.service.ChannelManager;
import com.teammors.server.im.service.MessageSender;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class ClusterManager implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ClusterManager.class);
    
    // Key prefix for instance heartbeat: server_heartbeat:{instanceId}
    private static final String KEY_HEARTBEAT_PREFIX = "server_heartbeat:";
    // Key prefix for instance sessions (Reverse Index): instance_sessions:{instanceId} -> Set<uid:deviceId>
    private static final String KEY_INSTANCE_SESSIONS_PREFIX = "instance_sessions:";
    
    // Stream Key prefix: im:stream:instance:{instanceId}
    private static final String KEY_STREAM_PREFIX = "im:stream:instance:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;
    
    @Autowired
    private ChannelManager channelManager;

    @Autowired
    private MessageSender messageSender;

    private final String instanceId = UUID.randomUUID().toString();
    private final String topicName = "im-cluster-topic";
    
    // Stream listener executor
    private final ExecutorService streamListenerExecutor = Executors.newSingleThreadExecutor();
    // Virtual Thread Executor for dispatching stream messages
    private final ExecutorService streamTaskExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean isRunning = true;
    
    public String getInstanceId() {
        return instanceId;
    }
    
    // Instance-specific topic for direct message forwarding
    // Format: im-instance-{instanceId}
    private String instanceTopicName;
    private String instanceStreamKey;

    @PostConstruct
    public void init() {
        this.instanceTopicName = "im-instance-" + instanceId;
        this.instanceStreamKey = KEY_STREAM_PREFIX + instanceId;
        
        redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(topicName));
        // We still keep Pub/Sub for broadcast events, but use Stream for message forwarding
        
        log.info("Cluster Manager started. Instance ID: {}", instanceId);
        log.info("Listening on private stream: {}", instanceStreamKey);
        
        // Register shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(this::gracefulShutdown));
        
        // Start Stream Listener
        streamListenerExecutor.submit(this::listenStream);
        
        publishEvent("STARTUP");
    }
    
    private void gracefulShutdown() {
        log.info("Graceful shutdown triggered for instance {}", instanceId);
        isRunning = false;
        streamListenerExecutor.shutdownNow();
        // Clean up self heartbeat immediately so other nodes can detect it faster (optional, or just let it expire)
        redisTemplate.delete(KEY_HEARTBEAT_PREFIX + instanceId);
        // Note: Actual session cleanup is handled by channel inactive events during server stop,
        // or by the dead instance cleaner if we crash hard.
    }
    
    private void listenStream() {
        while (isRunning) {
            try {
                // XREAD BLOCK 2000 STREAMS key $
                // Read new messages only ($)
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                        StreamReadOptions.empty().block(Duration.ofMillis(2000)).count(50), // Increase count for batching
                        StreamOffset.latest(instanceStreamKey)
                );
                
                if (records != null && !records.isEmpty()) {
                    for (MapRecord<String, Object, Object> record : records) {
                        // Dispatch to Virtual Thread for parallel processing
                        streamTaskExecutor.submit(() -> handleStreamMessage(record));
                    }
                }
            } catch (Exception e) {
                if (isRunning) {
                    log.error("Error reading from stream", e);
                    try {
                        Thread.sleep(1000); // Backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
    
    private void handleStreamMessage(MapRecord<String, Object, Object> record) {
        try {
            Map<Object, Object> value = record.getValue();
            String jsonBody = (String) value.get("body");
            
            if (jsonBody != null) {
                handleForwardedMessage(jsonBody);
            }
            
            // Acknowledge/Delete processed message
            // Since we are not using consumer groups, we can just delete it to keep stream small
            redisTemplate.opsForStream().delete(instanceStreamKey, record.getId());
            
        } catch (Exception e) {
            log.error("Error processing stream message {}", record.getId(), e);
        }
    }

    @Override
    public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
        String topic = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        if (topicName.equals(topic)) {
            handleClusterEvent(body);
        }
        // Deprecated: instanceTopicName listener logic moved to Stream
    }
    
    private void handleClusterEvent(String body) {
        // Format: INSTANCE_ID:EVENT
        if (!body.startsWith(instanceId)) { // Ignore self
            log.debug("Received cluster event: {}", body);
        }
    }
    
    private void handleForwardedMessage(String body) {
        try {
            // The body is the JSON string of the Message object
            Message msg = JSON.parseObject(body, Message.class);
            if (msg == null) return;
            
            String toUid = msg.getToUid();
            // Check if this user is connected to this instance
            Map<String, Channel> userChannels = channelManager.getUserChannels(toUid);
            
            if (userChannels != null && !userChannels.isEmpty()) {
                log.debug("Received forwarded message for user {}, sending to local channels", toUid);
                for (Channel channel : userChannels.values()) {
                    if (channel.isActive()) {
                        messageSender.send(channel, msg); // Use MessageSender
                    }
                }
            } else {
                log.warn("Received forwarded message for user {}, but user not found locally.", toUid);
            }
        } catch (Exception e) {
            log.error("Error handling forwarded message", e);
        }
    }

    @Scheduled(fixedRate = 5000)
    public void sendHeartbeat() {
        // 1. Send Pub/Sub Heartbeat (Legacy/Optional)
        publishEvent("HEARTBEAT");
        
        // 2. Set/Update Heartbeat Key in Redis (For Dead Instance Detection)
        // TTL = 10 seconds (if we crash, key expires in 10s)
        redisTemplate.opsForValue().set(KEY_HEARTBEAT_PREFIX + instanceId, String.valueOf(System.currentTimeMillis()), 10, TimeUnit.SECONDS);
    }
    
    /**
     * Periodically check for dead instances and clean up their sessions.
     * Running every 10 seconds.
     * In a real production environment, you might want to use a distributed lock 
     * so only one node performs this cleanup, or have a dedicated monitoring service.
     * For simplicity here, all nodes will check (idempotent operations are preferred).
     */
    @Scheduled(fixedRate = 10000)
    public void checkDeadInstances() {
        try {
            // Scan for all instance session keys: instance_sessions:*
            Set<String> instanceKeys = redisTemplate.keys(KEY_INSTANCE_SESSIONS_PREFIX + "*");
            if (instanceKeys == null || instanceKeys.isEmpty()) return;
            
            for (String key : instanceKeys) {
                // Extract instanceId from key "instance_sessions:{instanceId}"
                String deadInstanceId = key.substring(KEY_INSTANCE_SESSIONS_PREFIX.length());
                
                // Check if this instance is still alive (has heartbeat key)
                Boolean isAlive = redisTemplate.hasKey(KEY_HEARTBEAT_PREFIX + deadInstanceId);
                
                if (!isAlive) {
                    log.warn("Detected DEAD instance: {}. Starting session cleanup...", deadInstanceId);
                    cleanUpDeadInstance(deadInstanceId, key);
                }
            }
        } catch (Exception e) {
            log.error("Error checking dead instances", e);
        }
    }
    
    private void cleanUpDeadInstance(String deadInstanceId, String sessionSetKey) {
        // 1. Get all users connected to that dead instance
        // Set members format: "uid:deviceId"
        Set<String> userDevicePairs = redisTemplate.opsForSet().members(sessionSetKey);
        
        if (userDevicePairs != null && !userDevicePairs.isEmpty()) {
            for (String pair : userDevicePairs) {
                try {
                    String[] parts = pair.split(":");
                    if (parts.length == 2) {
                        String uid = parts[0];
                        String deviceId = parts[1];
                        
                        // 2. Remove from global session map: session:{uid} -> deviceId
                        redisTemplate.opsForHash().delete("session:" + uid, deviceId);
                        log.info("Cleaned up dead session for user {} device {} on dead instance {}", uid, deviceId, deadInstanceId);
                    }
                } catch (Exception e) {
                    log.error("Error cleaning up user session {}", pair, e);
                }
            }
        }
        
        // 3. Remove the instance_sessions key itself
        redisTemplate.delete(sessionSetKey);
        
        // 4. Cleanup Stream Key
        redisTemplate.delete(KEY_STREAM_PREFIX + deadInstanceId);
        
        log.info("Completed cleanup for dead instance {}", deadInstanceId);
    }

    private void publishEvent(String event) {
        String msg = instanceId + ":" + event;
        redisTemplate.convertAndSend(topicName, msg);
    }
    
    /**
     * Forward message to a specific instance via Redis Stream
     * @param targetInstanceId The target instance ID
     * @param message The message object to forward
     */
    public void forwardToInstance(String targetInstanceId, Message message) {
        if (targetInstanceId == null || targetInstanceId.isEmpty()) return;
        
        String targetStreamKey = KEY_STREAM_PREFIX + targetInstanceId;
        String jsonBody = JSON.toJSONString(message);
        
        // XADD key * body {json}
        redisTemplate.opsForStream().add(targetStreamKey, Collections.singletonMap("body", jsonBody));
    }
    
    /**
     * Register a user session to this instance (Reverse Index)
     * @param uid User ID
     * @param deviceId Device ID
     */
    public void registerSession(String uid, String deviceId) {
        redisTemplate.opsForSet().add(KEY_INSTANCE_SESSIONS_PREFIX + instanceId, uid + ":" + deviceId);
    }
    
    /**
     * Unregister a user session from this instance
     * @param uid User ID
     * @param deviceId Device ID
     */
    public void unregisterSession(String uid, String deviceId) {
        redisTemplate.opsForSet().remove(KEY_INSTANCE_SESSIONS_PREFIX + instanceId, uid + ":" + deviceId);
    }
}
