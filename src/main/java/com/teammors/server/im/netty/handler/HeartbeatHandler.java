package com.teammors.server.im.netty.handler;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.model.UserSessionInfo;
import com.teammors.server.im.service.IMService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatHandler.class);

    @Autowired
    @Lazy
    private IMService imService;
    
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.info("Channel idle (no ping/message), closing connection: {}", ctx.channel().id());
                
                // Clean up Redis session before closing
                // Although WebSocketHandler.handlerRemoved() calls imService.removeChannel(),
                // which calls channelManager.unbind(), we also need to clean up the Redis "session:{uid}" entry.
                // The current channelManager.unbind() only removes from local memory.
                
                removeRedisSession(ctx);
                
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    private void removeRedisSession(ChannelHandlerContext ctx) {
        if (ctx.channel().hasAttr(AttributeKey.valueOf("userId")) && 
            ctx.channel().hasAttr(AttributeKey.valueOf("deviceId"))) {
            
            String uid = (String) ctx.channel().attr(AttributeKey.valueOf("userId")).get();
            String deviceId = (String) ctx.channel().attr(AttributeKey.valueOf("deviceId")).get();
            
            if (uid != null && deviceId != null) {
                // Remove from Redis
                redisTemplate.opsForHash().delete("session:" + uid, deviceId);
                log.info("Removed session from Redis for user {} device {}", uid, deviceId);
            }
        }
    }
}
