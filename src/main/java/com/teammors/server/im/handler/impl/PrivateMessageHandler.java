package com.teammors.server.im.handler.impl;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.cluster.ClusterManager;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.handler.EventHandler;
import com.teammors.server.im.model.UserSessionInfo;
import com.teammors.server.im.service.ChannelManager;
import com.teammors.server.im.service.MessageSender;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class PrivateMessageHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(PrivateMessageHandler.class);

    @Autowired
    private ChannelManager channelManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ClusterManager clusterManager;
    
    @Autowired
    private MessageSender messageSender;

    @Override
    public String getEventId() {
        return "1000001";
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Message msg) {
        String toUid = msg.getToUid();
        
        // 1. Try to send locally
        Map<String, Channel> userChannels = channelManager.getUserChannels(toUid);
        
        boolean sentLocally = false;
        if (userChannels != null && !userChannels.isEmpty()) {
            for (Channel channel : userChannels.values()) {
                if (channel.isActive()) {
                    // Send and cache for ACK
                    messageSender.sendAndCache(channel, msg);
                    sentLocally = true;
                }
            }
        }
        
        // 2. If not sent locally (or to support multi-device on other instances), check Redis session
        // We always check session to support multi-device across different instances
        try {
            Map<Object, Object> sessions = redisTemplate.opsForHash().entries("session:" + toUid);
            if (sessions != null && !sessions.isEmpty()) {
                Set<String> forwardedInstances = new HashSet<>();
                
                for (Object value : sessions.values()) {
                    String json = (String) value;
                    UserSessionInfo sessionInfo = JSON.parseObject(json, UserSessionInfo.class);
                    
                    // If the user is on another instance, forward the message
                    if (sessionInfo != null && !clusterManager.getInstanceId().equals(sessionInfo.getInstanceId())) {
                        String targetInstanceId = sessionInfo.getInstanceId();
                        
                        // Avoid sending to the same instance multiple times if user has multiple devices there
                        if (!forwardedInstances.contains(targetInstanceId)) {
                            log.debug("User {} found on another instance {}, forwarding message", toUid, targetInstanceId);
                            clusterManager.forwardToInstance(targetInstanceId, msg);
                            forwardedInstances.add(targetInstanceId);
                        }
                    }
                }
            } else if (!sentLocally) {
                // User is offline (no session in Redis and no local channel)
                handleOffline(msg, toUid);
            }
        } catch (Exception e) {
            log.error("Error checking remote session", e);
            if (!sentLocally) handleOffline(msg, toUid);
        }
    }
    
    private void handleOffline(Message msg, String toUid) {
        if ("1".equals(msg.getIsCache())) {
            redisTemplate.opsForList().rightPush("offline:msg:" + toUid, JSON.toJSONString(msg));
        }
    }
}
