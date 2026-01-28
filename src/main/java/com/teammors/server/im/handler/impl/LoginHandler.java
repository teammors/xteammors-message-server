package com.teammors.server.im.handler.impl;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.handler.EventHandler;
import com.teammors.server.im.cluster.ClusterManager;
import com.teammors.server.im.model.UserSessionInfo;
import com.teammors.server.im.service.ChannelManager;
import com.teammors.server.im.service.IMService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class LoginHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginHandler.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ChannelManager channelManager;

    @Autowired
    private ClusterManager clusterManager;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private IMService imService;

    @Override
    public String getEventId() {
        return "1000000";
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Message msg) {
        String uid = msg.getFromUid();
        String token = msg.getToken();
        String deviceId = msg.getDeviceId();

        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = "default"; // Fallback or reject
        }

        // Validate token: GET "token_list:用户Id"
        String storedToken = redisTemplate.opsForValue().get("token_list:" + uid);

        if (token != null && token.equals(storedToken)) {
            channelManager.bind(uid, deviceId, ctx.channel());

            // 1. Store UserSessionInfo to Redis
            UserSessionInfo sessionInfo = new UserSessionInfo();
            sessionInfo.setUserId(uid);
            sessionInfo.setChannelId(ctx.channel().id().asLongText());
            sessionInfo.setDeviceId(deviceId);
            sessionInfo.setLoginTime(System.currentTimeMillis());
            sessionInfo.setInstanceId(clusterManager.getInstanceId());
            
            // Key: "session:用户Id" -> HashKey: "设备Id" -> Value: JSON(UserSessionInfo)
            // This supports multi-device login info
            redisTemplate.opsForHash().put("session:" + uid, deviceId, JSON.toJSONString(sessionInfo));
            
            // 2. Register to ClusterManager (Reverse Index for Dead Instance Cleanup)
            clusterManager.registerSession(uid, deviceId);

            log.info("User {} logged in successfully on device {}", uid, deviceId);

            sendResponse(ctx, "1000000", "Success");
            
            // 2. Async offline & unacked message retrieval and push
            String finalDeviceId = deviceId;
            imService.executeAsync(() -> {
                pushOfflineMessages(ctx, uid);
                pushUnackedMessages(ctx, uid);
            });
        } else {
            log.warn("Login failed for user {}. Invalid token.", uid);
            sendResponse(ctx, "1000000", "Fail");
            ctx.close();
        }
    }
    
    // Push messages that were sent but not ACKed (QoS 1 Re-delivery)
    private void pushUnackedMessages(ChannelHandlerContext ctx, String uid) {
        String ackKey = "ack:msg:" + uid;
        try {
            // Get all unacked messages
            Map<Object, Object> unackedMsgs = redisTemplate.opsForHash().entries(ackKey);
            if (unackedMsgs != null && !unackedMsgs.isEmpty()) {
                log.info("Resending {} unacked messages for user {}", unackedMsgs.size(), uid);
                for (Object msgJsonObj : unackedMsgs.values()) {
                    String msgJson = (String) msgJsonObj;
                    if (ctx.channel().isActive()) {
                        ctx.writeAndFlush(new TextWebSocketFrame(msgJson));
                    } else {
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error pushing unacked messages for user {}", uid, e);
        }
    }

    private void pushOfflineMessages(ChannelHandlerContext ctx, String uid) {
        String key = "offline:msg:" + uid;
        long total = 0;
        try {
            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > 0) {
                total = size;
                log.info("Start pushing {} offline messages for user {}", size, uid);
                
                int batchSize = 200;
                long count = 0;
                
                while (true) {
                    // Pop messages from right (assuming RPUSH was used to add)
                    // We use LPOP to get the oldest message first if we want FIFO
                    // Assuming RPUSH was used: Head is oldest, Tail is newest. 
                    // To get oldest first: LPOP
                    List<String> messages = redisTemplate.opsForList().range(key, 0, batchSize - 1);
                    if (messages == null || messages.isEmpty()) {
                        break;
                    }
                    
                    // Trim the list after reading
                    redisTemplate.opsForList().trim(key, messages.size(), -1);
                    
                    for (String jsonMsg : messages) {
                        if (ctx.channel().isActive()) {
                            ctx.writeAndFlush(new TextWebSocketFrame(jsonMsg));
                        } else {
                            // Channel closed, stop pushing and maybe push back to redis? 
                            // For simplicity, we abort. In prod, we might need to handle data loss or requeue.
                            log.warn("Channel closed during offline message push for user {}", uid);
                            return;
                        }
                    }
                    
                    count += messages.size();
                    
                    if (messages.size() < batchSize) {
                        break; // No more messages
                    }
                    
                    // Pause if needed
                    try {
                        TimeUnit.MILLISECONDS.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                log.info("Finished pushing {} offline messages for user {}", count, uid);
            }
        } catch (Exception e) {
            log.error("Error pushing offline messages for user {}", uid, e);
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, String eventId, String body) {
        Message resp = new Message();
        resp.setEventId(eventId);
        resp.setDataBody(body);
        resp.setsTimest(String.valueOf(System.currentTimeMillis()));
        ctx.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(resp)));
    }
}
