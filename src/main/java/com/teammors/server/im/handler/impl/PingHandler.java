package com.teammors.server.im.handler.impl;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.handler.EventHandler;
import com.teammors.server.im.service.IMService;
import com.teammors.server.im.service.MessageSender;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PingHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(PingHandler.class);
    private static final long RETRY_TIMEOUT_MS = 5000; // 5 seconds

    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    @org.springframework.context.annotation.Lazy
    private IMService imService;
    
    @Autowired
    private MessageSender messageSender;

    @Override
    public String getEventId() {
        return "9000000";
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Message msg) {
        // 1. Respond PONG
        messageSender.sendResponse(ctx, "9000000", null, null, "PONG");
        
        // 2. Check unacked messages (Async)
        String fromUid = msg.getFromUid();
        if (fromUid != null && !fromUid.isEmpty()) {
            imService.executeAsync(() -> checkAndResendUnacked(ctx, fromUid));
        }
    }
    
    private void checkAndResendUnacked(ChannelHandlerContext ctx, String uid) {
        String ackKey = "ack:msg:" + uid;
        try {
            // Get all unacked messages
            Map<Object, Object> unackedMsgs = redisTemplate.opsForHash().entries(ackKey);
            
            if (unackedMsgs != null && !unackedMsgs.isEmpty()) {
                long now = System.currentTimeMillis();
                
                for (Map.Entry<Object, Object> entry : unackedMsgs.entrySet()) {
                    String sTimestStr = (String) entry.getKey();
                    String msgJson = (String) entry.getValue();
                    
                    try {
                        long sTimest = Long.parseLong(sTimestStr);
                        
                        // Check if timeout exceeded
                        if (now - sTimest > RETRY_TIMEOUT_MS) {
                            if (ctx.channel().isActive()) {
                                Message resendMsg = JSON.parseObject(msgJson, Message.class);
                                log.debug("Resending timed-out message to user {}, timest: {}", uid, sTimest);
                                messageSender.send(ctx, resendMsg); // Already cached, just send
                            } else {
                                return;
                            }
                        }
                    } catch (NumberFormatException e) {
                        // Ignore invalid timestamp keys
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking unacked messages for user {}", uid, e);
        }
    }
}
