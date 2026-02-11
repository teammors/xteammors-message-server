package com.teammors.server.im.entity;

import lombok.Data;

@Data
public class Message {

    private String eventId; // Event ID, refer to the Event ID document

    private String fromUid; // Sender ID
    private String toUid; // Receiver ID

    private String token; // Sender token
    private String deviceId = ""; // Unique device ID for the sender token

    private String type; // Message type
    private String cTimest; // Client-side timestamp (when sent)
    private String sTimest; // Server-side timestamp (when received)
    private String dataBody; // Message body, can be freely defined, passed as a string format {}

    private String isGroup = "0"; // Whether it is a group message: 1 - group, 0 - personal
    private String groupId = ""; // Group ID. For messages sent from the client, this cannot coexist with toUid; only one of them can be present at a time

    private String isCache = "1"; // Whether offline storage is required: 1 - required, 0 - not required

}
