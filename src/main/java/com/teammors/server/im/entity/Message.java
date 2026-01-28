package com.teammors.server.im.entity;

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

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getFromUid() {
        return fromUid;
    }

    public void setFromUid(String fromUid) {
        this.fromUid = fromUid;
    }

    public String getToUid() {
        return toUid;
    }

    public void setToUid(String toUid) {
        this.toUid = toUid;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getcTimest() {
        return cTimest;
    }

    public void setcTimest(String cTimest) {
        this.cTimest = cTimest;
    }

    public String getsTimest() {
        return sTimest;
    }

    public void setsTimest(String sTimest) {
        this.sTimest = sTimest;
    }

    public String getDataBody() {
        return dataBody;
    }

    public void setDataBody(String dataBody) {
        this.dataBody = dataBody;
    }

    public String getIsGroup() {
        return isGroup;
    }

    public void setIsGroup(String isGroup) {
        this.isGroup = isGroup;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getIsCache() {
        return isCache;
    }

    public void setIsCache(String isCache) {
        this.isCache = isCache;
    }
}
