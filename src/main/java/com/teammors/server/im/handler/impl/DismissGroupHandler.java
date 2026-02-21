package com.teammors.server.im.handler.impl;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.handler.EventHandler;
import com.teammors.server.im.service.MessageSender;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DismissGroupHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(DismissGroupHandler.class);

    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private GroupMessageHandler groupMessageHandler;

    @Autowired
    MessageSender messageSender;

    @Override
    public String getEventId() {
        return "5000003";
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Message msg) {
        String fromUid = msg.getFromUid();
        String groupId = msg.getGroupId();
        
        if (groupId == null || groupId.isEmpty()) {
            log.warn("Dismiss group failed: Missing groupId from user {}", fromUid);
            sendResponse(ctx, msg, "5000003", "Fail: Missing GroupId");
            return;
        }

        try {
            // 1. Check permission (Is Admin/Owner?)
            //String groupKey = groupId;
            Object roleObj = redisTemplate.opsForHash().get(groupId, fromUid);
            
            if (roleObj == null) {
                sendResponse(ctx, msg, "5000003", "Fail: Not a member");
                return;
            }
            
            String role = (String) roleObj;
            if (!"1".equals(role)) { // "1" means admin/owner
                sendResponse(ctx, msg, "5000003", "Fail: Permission Denied");
                return;
            }

            // 2. Broadcast dismissal notification to all members
            Message notifyMsg = new Message();
            notifyMsg.setEventId("5000004"); // Use Group Message Event ID
            notifyMsg.setFromUid(fromUid);
            notifyMsg.setToUid(groupId);
            notifyMsg.setDataBody("Group has been dismissed by admin.");
            notifyMsg.setSTimest(String.valueOf(System.currentTimeMillis()));
            notifyMsg.setIsCache("0"); // Notification doesn't need to be cached if group is gone
            
            // We use GroupMessageHandler to broadcast.
            // Note: Since we are about to delete the group, we must send notification BEFORE deleting.
            // But GroupMessageHandler is async... this is a race condition risk.
            // If we delete immediately, the async sender might find empty group.
            // So we should: 
            // A. Get member list first, then send notification manually (reuse logic), then delete.
            // B. Or just use GroupMessageHandler and wait? (Can't easily wait on void async).
            
            // Let's do A: Get members, Clean up, then Send (using the list we just fetched).
            // Actually, if we delete data first, we can't send.
            // If we send first (async), we might delete data before sending finishes.
            
            // Refined Approach:
            // 1. Fetch all members.
            // 2. Clean up Redis data (Group Info & User's Group List).
            // 3. Send notification to the fetched members (using a modified batch sender that takes a list, not querying redis).
            
            // Since GroupMessageHandler reads from Redis, we can't use it directly if we delete Redis key first.
            // Let's call GroupMessageHandler FIRST, let it read members.
            // BUT we need to ensure it finishes reading before we delete.
            // GroupMessageHandler reads members synchronously at the beginning of handle().
            // The async part is the sending loop.
            // So it IS safe to call groupMessageHandler.handle(), it will fetch members immediately.
            // Then we can delete.
            
            groupMessageHandler.handle(ctx, notifyMsg);
            
            // Small delay to ensure GroupMessageHandler fetched the keys? 
            // No need, it's synchronous up to the point of fetching keys.
            // However, let's be safer and fetch members here for cleanup anyway.
            
            Set<Object> memberIds = redisTemplate.opsForHash().keys(groupId);
            for (Object memberIdObj : memberIds) {
                String memberId = (String) memberIdObj;
                // Remove from reverse index
                redisTemplate.opsForSet().remove("user:groups:" + memberId, groupId);
            }

            // Delete Group Key
            redisTemplate.delete(groupId);
            
            log.info("Group {} dismissed by {}", groupId, fromUid);
            sendResponse(ctx, msg, "5000003", "Success");

        } catch (Exception e) {
            log.error("Error dismissing group {} for user {}", groupId, fromUid, e);
            sendResponse(ctx, msg, "5000003", "Fail: System Error");
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, Message originalMsg, String eventId, String body) {
        messageSender.sendResponse(ctx,originalMsg,eventId,body);
    }
}
