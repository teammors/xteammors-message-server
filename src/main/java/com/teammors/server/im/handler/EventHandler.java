package com.teammors.server.im.handler;

import com.teammors.server.im.entity.Message;
import io.netty.channel.ChannelHandlerContext;

public interface EventHandler {
    /**
     * Get the event ID this handler is responsible for
     * @return Event ID
     */
    String getEventId();

    /**
     * Handle the message
     * @param ctx Channel Context
     * @param msg Message
     */
    void handle(ChannelHandlerContext ctx, Message msg);
}
