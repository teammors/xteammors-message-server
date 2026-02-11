package com.teammors.server.im.handler.impl;

import com.teammors.server.im.entity.Message;
import com.teammors.server.im.handler.EventHandler;
import com.teammors.server.im.service.IMService;
import com.teammors.server.im.service.MessageSender;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class GroupMessageHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(GroupMessageHandler.class);
    
    // Batch size for parallel processing
    private static final int BATCH_SIZE = 500;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PrivateMessageHandler privateMessageHandler;
    
    @Autowired
    @org.springframework.context.annotation.Lazy
    private IMService imService;

    @Override
    public String getEventId() {
        return "5000004";
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Message msg) {
        String fromUid = msg.getFromUid();
        String groupId = msg.getGroupId();
        
        if (groupId == null || groupId.isEmpty()) {
            log.warn("Group message failed: Missing groupId from user {}", fromUid);
            return;
        }

        // 1. Get all group members
        // Key: "group:info:{groupId}"
        String groupKey = "group:info:" + groupId;
        Set<Object> memberIdsSet = redisTemplate.opsForHash().keys(groupKey);
        
        if (memberIdsSet == null || memberIdsSet.isEmpty()) {
            log.warn("Group message failed: Group {} not found or empty", groupId);
            return;
        }
        
        List<String> memberIds = memberIdsSet.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        
        int totalMembers = memberIds.size();
        log.info("Sending group message to {} members in group {}", totalMembers, groupId);
        
        // 2. Parallel processing with Virtual Threads
        // Split list into batches
        List<List<String>> batches = partition(memberIds, BATCH_SIZE);
        
        for (List<String> batch : batches) {
            imService.executeAsync(() -> processBatch(ctx, msg, batch, fromUid));
        }
    }
    
    private void processBatch(ChannelHandlerContext ctx, Message originalMsg, List<String> batch, String fromUid) {
        for (String memberId : batch) {
            try {
                // Create a copy for each recipient to avoid race conditions
                Message copyMsg = new Message();
                copyMsg.setEventId("1000001"); // Convert to Private Message Event ID
                copyMsg.setFromUid(fromUid);
                copyMsg.setToUid(memberId);
                copyMsg.setDataBody(originalMsg.getDataBody());
                copyMsg.setSTimest(String.valueOf(System.currentTimeMillis()));
                copyMsg.setIsCache(originalMsg.getIsCache());
                copyMsg.setToken(originalMsg.getToken());
                copyMsg.setGroupId(originalMsg.getGroupId());
                copyMsg.setIsGroup(originalMsg.getIsGroup());
                
                privateMessageHandler.handle(ctx, copyMsg);
                
            } catch (Exception e) {
                log.error("Failed to send group message to member {}", memberId, e);
            }
        }
    }
    
    // Simple list partition utility
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
