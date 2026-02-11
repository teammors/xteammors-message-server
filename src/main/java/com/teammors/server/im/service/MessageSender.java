package com.teammors.server.im.service;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.utils.SecurityUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageSender {

    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    ChannelManager channelManager;

    /**
     * Send message to channel without caching (e.g. Pong, System Response)
     */
    public void send(Channel channel, Message msg) {
        if (channel != null && channel.isActive()) {

            //encrypt data
            String text = JSON.toJSONString(msg);
            String uid = channelManager.getUserIdByChannelId(channel.id().asLongText());
            if(uid != null){
                text = SecurityUtil.encrypt(SecurityUtil.getUidKey(uid), text);
            }

            channel.writeAndFlush(new TextWebSocketFrame(text));
        }
    }
    
    /**
     * Send message via ChannelHandlerContext (convenience method)
     */
    public void send(ChannelHandlerContext ctx, Message msg) {
        if (ctx != null) {
            send(ctx.channel(), msg);
        }
    }

    /**
     * Send message and cache it for ACK mechanism (QoS 1)
     * Should be used for Private/Group messages that require reliability.
     */
    public void sendAndCache(Channel channel, Message msg) {
        if (channel != null && channel.isActive()) {
            // Cache for ACK
            String toUid = msg.getToUid();
            String sTimest = msg.getSTimest();
            
            if (toUid != null && sTimest != null && !sTimest.isEmpty()) {
                // Key: ack:msg:{userId}  HashKey: sTimest  Value: MessageJSON
                redisTemplate.opsForHash().put("ack:msg:" + toUid, sTimest, JSON.toJSONString(msg));
            }

            //encrypt data
            String text = JSON.toJSONString(msg);
            String uid = channelManager.getUserIdByChannelId(channel.id().asLongText());
            if(uid != null){
                text = SecurityUtil.encrypt(SecurityUtil.getUidKey(uid), text);
            }
            // Send
            channel.writeAndFlush(new TextWebSocketFrame(text));
        }
    }
    
    public void sendAndCache(ChannelHandlerContext ctx, Message msg) {
        if (ctx != null) {
            sendAndCache(ctx.channel(), msg);
        }
    }

    /**
     * Send a simple system response (e.g. "Success", "Fail")
     */
    public void sendResponse(ChannelHandlerContext ctx, String eventId, String fromUid, String toUid, String body) {
        Message resp = new Message();
        resp.setEventId(eventId);
        resp.setFromUid(fromUid != null ? fromUid : "SYSTEM");
        resp.setToUid(toUid);
        resp.setDataBody(body);
        resp.setSTimest(String.valueOf(System.currentTimeMillis()));
        resp.setIsCache("0"); // System responses usually don't need caching
        
        send(ctx, resp);
    }

    /**
     * Send a simple system response using original message context
     */
    public void sendResponse(ChannelHandlerContext ctx, Message originalMsg, String eventId, String body) {
        sendResponse(ctx, eventId, "SYSTEM", originalMsg.getFromUid(), body);
    }
}
