package com.teammors.server.im.netty.handler;

import com.alibaba.fastjson.JSON;
import com.teammors.server.im.entity.Message;
import com.teammors.server.im.service.ChannelManager;
import com.teammors.server.im.service.IMService;
import com.teammors.server.im.utils.SecurityUtil;
import com.teammors.server.im.utils.XJSONUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ChannelHandler.Sharable
public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);

    @Autowired
    private IMService imService;

    @Autowired
    ChannelManager channelManager;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String text = frame.text();

        //If it is encrypted, it needs to be decrypted first.
        if(!XJSONUtils.isJsonFast(text)){
            String uid = channelManager.getUserIdByChannelId(ctx.channel().id().asLongText());
            if(uid != null){
                text = SecurityUtil.decrypt(SecurityUtil.getUidKey(uid), text);
            }
        }

        try {

            Message msg = JSON.parseObject(text, Message.class);
            if(!Objects.equals(msg.getEventId(), "9000000")){
                log.info("Received message: {}", text);
            }
            msg.setSTimest(String.valueOf(System.currentTimeMillis()));
            imService.handleEvent(ctx, msg);

        } catch (Exception e) {
            log.error("Failed to parse message", e);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        imService.removeChannel(ctx.channel());
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("WebSocket error", cause);
        imService.removeChannel(ctx.channel());
        ctx.close();
    }
}
