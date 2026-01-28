package com.teammors.server.im.handler.impl;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.handler.EventHandler;
import com.teammors.server.im.model.GroupMember;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LeaveGroupHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(LeaveGroupHandler.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public String getEventId() {
        return "5000005";
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Message msg) {
        String fromUid = msg.getFromUid();
        String groupId = msg.getGroupId();
        String dataBody = msg.getDataBody();

        if (groupId == null || groupId.isEmpty()) {
            log.warn("Leave group failed: Missing groupId from user {}", fromUid);
            sendResponse(ctx, msg, "5000005", "Fail: Missing GroupId");
            return;
        }

        try {
            // 1. Check if group exists
            String groupKey = "group:info:" + groupId;
            Boolean hasKey = redisTemplate.hasKey(groupKey);
            if (!hasKey) {
                log.warn("Leave group failed: Group {} not found", groupId);
                sendResponse(ctx, msg, "5000005", "Fail: Group Not Found");
                return;
            }

            // 2. Parse leaving member list
            List<GroupMember> leavingMembers = JSON.parseArray(dataBody, GroupMember.class);
            if (leavingMembers == null || leavingMembers.isEmpty()) {
                log.warn("Leave group failed: Empty member list from user {}", fromUid);
                sendResponse(ctx, msg, "5000005", "Fail: Empty members");
                return;
            }

            // 3. Remove members from Group
            for (GroupMember member : leavingMembers) {
                // Remove from group info
                redisTemplate.opsForHash().delete(groupKey, member.getUserId());
                
                // Remove from reverse index: "user:groups:{userId}" -> Set<groupId>
                redisTemplate.opsForSet().remove("user:groups:" + member.getUserId(), groupId);
            }

            log.info("Users left group {} successfully. Count: {}", groupId, leavingMembers.size());

            sendResponse(ctx, msg, "5000005", "Success");

        } catch (Exception e) {
            log.error("Error leaving group {} for user {}", groupId, fromUid, e);
            sendResponse(ctx, msg, "5000005", "Fail: System Error");
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, Message originalMsg, String eventId, String body) {
        Message resp = new Message();
        resp.setEventId(eventId);
        resp.setFromUid("SYSTEM");
        resp.setToUid(originalMsg.getFromUid());
        resp.setDataBody(body);
        resp.setsTimest(String.valueOf(System.currentTimeMillis()));
        resp.setIsCache("0");
        
        ctx.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(resp)));
    }
}
