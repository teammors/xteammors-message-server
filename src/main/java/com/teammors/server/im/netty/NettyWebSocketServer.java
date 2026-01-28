package com.teammors.server.im.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class NettyWebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(NettyWebSocketServer.class);

    @Value("${netty.port:8088}")
    private int port;

    @Autowired
    private WebSocketChannelInitializer webSocketChannelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;

    @PostConstruct
    public void start() {
        new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(webSocketChannelInitializer);

                channelFuture = b.bind(port).sync();
                log.info("Netty WebSocket server started on port: {}", port);
                channelFuture.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.error("Netty server interrupted", e);
                Thread.currentThread().interrupt();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }).start();
    }

    // Remove @PreDestroy to control shutdown order manually in IMApplication
    public void stop() {
        log.info("Stopping Netty WebSocket server...");
        if (bossGroup != null) {
            try {
                // Stop accepting new connections immediately
                bossGroup.shutdownGracefully(0, 5, java.util.concurrent.TimeUnit.SECONDS).sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Netty bossGroup shutdown interrupted");
            }
        }
        if (workerGroup != null) {
            try {
                // Close all existing connections and wait
                // This will trigger channelInactive -> handlerRemoved -> Redis Cleanup
                workerGroup.shutdownGracefully(0, 5, java.util.concurrent.TimeUnit.SECONDS).sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Netty workerGroup shutdown interrupted");
            }
        }
        log.info("Netty WebSocket server stopped.");
    }
}
