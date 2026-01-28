package com.teammors.server.im;

import com.teammors.server.im.netty.NettyWebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class IMApplication implements ApplicationListener<ContextClosedEvent> {

    @Autowired
    private NettyWebSocketServer nettyWebSocketServer;

    public static void main(String[] args) {
        SpringApplication.run(IMApplication.class, args);
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        // This event is fired when the ApplicationContext is closed, 
        // but BEFORE beans are destroyed (mostly).
        // However, standard bean destruction order is not guaranteed relative to this listener
        // unless we ensure this listener runs early. 
        // Actually, ContextClosedEvent is published at the beginning of close().
        // So RedisTemplate should still be alive here.
        
        if (nettyWebSocketServer != null) {
            nettyWebSocketServer.stop();
        }
    }
}
