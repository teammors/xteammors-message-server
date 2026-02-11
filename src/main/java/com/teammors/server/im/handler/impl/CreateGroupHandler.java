package com.teammors.server.im.handler.impl;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.handler.EventHandler;
import com.teammors.server.im.model.GroupMember;
import com.teammors.server.im.service.MessageSender;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CreateGroupHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(CreateGroupHandler.class);

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    MessageSender messageSender;

    @Autowired
    private GroupMessageHandler groupMessageHandler;

    @Override
    public String getEventId() {
        return "5000001";
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Message msg) {
        String fromUid = msg.getFromUid();
        String dataBody = msg.getDataBody();

        try {
            // 1. Parse member list
            List<GroupMember> members = JSON.parseArray(dataBody, GroupMember.class);
            if (members == null || members.isEmpty()) {
                log.warn("Create group failed: Empty member list from user {}", fromUid);
                sendResponse(ctx, msg, "5000001", "Fail: Empty members");
                return;
            }

            // 2. Generate Group ID
            String groupId = msg.getGroupId();

            // 3. Store Group Info to Redis
            String groupKey = "group:info:" + groupId;
            
            for (GroupMember member : members) {
                redisTemplate.opsForHash().put(groupKey, member.getUserId(), member.getIsAdmin());
                redisTemplate.opsForSet().add("user:groups:" + member.getUserId(), groupId);
            }

            log.info("Group created successfully. GroupId: {}, Creator: {}, Members: {}", groupId, fromUid, members.size());

            // 4. Respond with Group ID
            String responseBody = JSON.toJSONString(java.util.Map.of("groupId", groupId));
            sendResponse(ctx, msg, "5000001", responseBody);
            
            // 5. Notify all initial members
            Message notifyMsg = new Message();
            notifyMsg.setEventId("5000004"); // Use Group Message Event ID
            notifyMsg.setFromUid("SYSTEM");
            notifyMsg.setGroupId(groupId);
            notifyMsg.setIsGroup("1");
            notifyMsg.setDataBody(JSON.toJSONString(java.util.Map.of(
                "type", "GROUP_CREATED",
                "groupId", groupId,
                "creator", fromUid,
                "timestamp", System.currentTimeMillis()
            )));
            notifyMsg.setSTimest(String.valueOf(System.currentTimeMillis()));
            notifyMsg.setIsCache("0"); // System notification usually not cached or handle separately
            
            groupMessageHandler.handle(ctx, notifyMsg);

        } catch (Exception e) {
            log.error("Error creating group for user {}", fromUid, e);
            sendResponse(ctx, msg, "5000001", "Fail: System Error");
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, Message originalMsg, String eventId, String body) {
        messageSender.sendResponse(ctx,originalMsg,eventId,body);
    }
}
