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

@Component
public class JoinGroupHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(JoinGroupHandler.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    MessageSender messageSender;

    @Autowired
    private GroupMessageHandler groupMessageHandler;

    @Override
    public String getEventId() {
        return "5000002";
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Message msg) {
        String fromUid = msg.getFromUid();
        String groupId = msg.getGroupId();
        String dataBody = msg.getDataBody();

        if (groupId == null || groupId.isEmpty()) {
            log.warn("Join group failed: Missing groupId from user {}", fromUid);
            sendResponse(ctx, msg, "5000002", "Fail: Missing GroupId");
            return;
        }

        try {
            // 1. Check if group exists
            //String groupKey = groupId;
            Boolean hasKey = redisTemplate.hasKey(groupId);
            if (!hasKey) {
                log.warn("Join group failed: Group {} not found", groupId);
                sendResponse(ctx, msg, "5000002", "Fail: Group Not Found");
                return;
            }

            // 2. Parse new member list
            List<GroupMember> newMembers = JSON.parseArray(dataBody, GroupMember.class);
            if (newMembers == null || newMembers.isEmpty()) {
                log.warn("Join group failed: Empty member list from user {}", fromUid);
                sendResponse(ctx, msg, "5000002", "Fail: Empty members");
                return;
            }

            // 3. Add members to Group
            for (GroupMember member : newMembers) {
                redisTemplate.opsForHash().put(groupId, member.getUserId(), member.getIsAdmin());
                redisTemplate.opsForSet().add("user:groups:" + member.getUserId(), groupId);
            }

            log.info("Users joined group {} successfully. Count: {}", groupId, newMembers.size());

            sendResponse(ctx, msg, "5000002", "Success");
            
            // 4. Notify all group members (including new ones)
            Message notifyMsg = new Message();
            notifyMsg.setEventId("5000004");
            notifyMsg.setFromUid("SYSTEM");
            notifyMsg.setGroupId(groupId);
            notifyMsg.setIsGroup("1");
            notifyMsg.setDataBody(JSON.toJSONString(java.util.Map.of(
                "type", "MEMBER_JOINED",
                "groupId", groupId,
                "inviter", fromUid,
                "newMembers", newMembers,
                "timestamp", System.currentTimeMillis()
            )));
            notifyMsg.setSTimest(String.valueOf(System.currentTimeMillis()));
            notifyMsg.setIsCache("0");
            
            groupMessageHandler.handle(ctx, notifyMsg);

        } catch (Exception e) {
            log.error("Error joining group {} for user {}", groupId, fromUid, e);
            sendResponse(ctx, msg, "5000002", "Fail: System Error");
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, Message originalMsg, String eventId, String body) {
        messageSender.sendResponse(ctx,originalMsg,eventId,body);
    }
}
