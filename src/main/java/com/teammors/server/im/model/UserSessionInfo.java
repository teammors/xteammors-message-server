package com.teammors.server.im.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionInfo {
    private String userId;
    private String channelId;
    private String deviceId;
    private long loginTime;
    private String instanceId;

}
