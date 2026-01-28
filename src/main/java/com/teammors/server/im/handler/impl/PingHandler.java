package com.teammors.server.im.handler.impl;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.handler.EventHandler;
import com.teammors.server.im.service.IMService;
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

    @Override
    public String getEventId() {
        return "9000000";
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Message msg) {
        // 1. Respond PONG
        Message pong = new Message();
        pong.setEventId("9000000");
        pong.setDataBody("PONG");
        ctx.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(pong)));
        
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
            // Using entries() to get both timestamp (key) and message (value)
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
                                log.debug("Resending timed-out message to user {}, timest: {}", uid, sTimest);
                                ctx.writeAndFlush(new TextWebSocketFrame(msgJson));
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
