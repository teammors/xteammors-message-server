package com.teammors.server.im.service;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChannelManager {
    private static final Logger log = LoggerFactory.getLogger(ChannelManager.class);
    
    // UserId -> (DeviceId -> Channel)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Channel>> userChannels = new ConcurrentHashMap<>();

    public static final AttributeKey<String> ATTR_USER_ID = AttributeKey.valueOf("userId");
    public static final AttributeKey<String> ATTR_DEVICE_ID = AttributeKey.valueOf("deviceId");

    private final ConcurrentHashMap<String, String> userIdMaps = new ConcurrentHashMap<>();

    /**
     * Bind User-Device-Channel
     * @param uid User ID
     * @param deviceId Device ID
     * @param channel Netty Channel
     */
    public void bind(String uid, String deviceId, Channel channel) {
        if (uid == null || deviceId == null || channel == null) {
            log.error("Bind failed: args cannot be null. uid={}, deviceId={}", uid, deviceId);
            return;
        }
        
        // 1. Store in memory
        userChannels.computeIfAbsent(uid, k -> new ConcurrentHashMap<>()).put(deviceId, channel);
        
        // 2. Attach attributes to channel for quick access during disconnect
        channel.attr(ATTR_USER_ID).set(uid);
        channel.attr(ATTR_DEVICE_ID).set(deviceId);

        //3.channelId map userId
        userIdMaps.put(channel.id().asLongText(), uid);
        
        log.info("Bound user: {}, device: {}, channel: {}", uid, deviceId, channel.id());
    }

    /**
     * Unbind when channel is disconnected
     * @param channel Netty Channel
     */
    public void unbind(Channel channel) {
        if (channel == null) return;

        userIdMaps.remove(channel.id().asLongText());

        String uid = channel.attr(ATTR_USER_ID).get();
        String deviceId = channel.attr(ATTR_DEVICE_ID).get();
        
        if (uid != null && deviceId != null) {
            Map<String, Channel> deviceMap = userChannels.get(uid);
            if (deviceMap != null) {
                Channel storedChannel = deviceMap.get(deviceId);
                // Only remove if it's the same channel (handling reconnections)
                if (storedChannel == channel) {
                    deviceMap.remove(deviceId);
                    log.info("Unbound user: {}, device: {}, channel: {}", uid, deviceId, channel.id());
                    
                    if (deviceMap.isEmpty()) {
                        userChannels.remove(uid);
                    }
                }
            }
        }
    }

    /**
     * Get Channel by User ID and Device ID
     * @param uid User ID
     * @param deviceId Device ID
     * @return Channel or null
     */
    public Channel getChannel(String uid, String deviceId) {
        Map<String, Channel> deviceMap = userChannels.get(uid);
        if (deviceMap != null) {
            return deviceMap.get(deviceId);
        }
        return null;
    }

    /**
     * Get all channels for a user (multi-device support)
     * @param uid User ID
     * @return Map of DeviceId -> Channel
     */
    public Map<String, Channel> getUserChannels(String uid) {
        return userChannels.get(uid);
    }

    /**
     * get uid from channelId
     * @return String <>uid</>
     */
    public String getUserIdByChannelId(String channelId) {
        return userIdMaps.get(channelId);
    }
}
