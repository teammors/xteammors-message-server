package com.teammors.server.im.entity;

public class Message {

    private String eventId;//事件ID，参考事件ID文件

    private String fromUid;//发送者ID
    private String toUid;//接收者ID

    private String token;//发送者token
    private String deviceId = "";//发送者token唯一设备id


    private String type;//消息类型
    private String cTimest;//客户端发送时间搓
    private String sTimest;//服务端接收时间搓
    private String dataBody;//消息体，可以自由定义，以字符串格式传入{}

    private String isGroup = "0";//是否群组 1-群组，0-个人
    private String groupId = "";//群组ID ，对于客户端发送过来的消息，不能和toUid并存，两者只能同时出现一个

    private String isCache = "1";//是否需要存离线 1-需要，0-不需要

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
