package com.teammors.server.im.handler.impl;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.handler.EventHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AckHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(AckHandler.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public String getEventId() {
        return "1000002";
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Message msg) {
        String fromUid = msg.getFromUid();
        String dataBody = msg.getDataBody(); // ["sTimest1", "sTimest2"]

        if (dataBody == null || dataBody.isEmpty()) {
            return;
        }

        try {
            List<String> ackIds = JSON.parseArray(dataBody, String.class);
            if (ackIds != null && !ackIds.isEmpty()) {
                String ackKey = "ack:msg:" + fromUid;
                
                // Batch delete acked messages from Redis
                // opsForHash().delete accepts varargs
                redisTemplate.opsForHash().delete(ackKey, ackIds.toArray());
                
                log.debug("User {} acked messages: {}", fromUid, ackIds);
            }
        } catch (Exception e) {
            log.error("Error processing ACK from user {}", fromUid, e);
        }
    }
}
