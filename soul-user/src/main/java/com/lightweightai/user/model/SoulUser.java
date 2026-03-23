package com.lightweightai.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A SoulHarbor user (anonymous or registered).
 */
public class SoulUser {
    private String id;
    private String username;
    @JsonIgnore
    private String passwordHash;
    private String openid;
    private String nickname;
    private String memberLevel;
    private long createdAt;
    private long lastActive;

    public SoulUser() {}

    public SoulUser(String id, String username, String passwordHash, String openid, String nickname, String memberLevel, long createdAt, long lastActive) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.openid = openid;
        this.nickname = nickname;
        this.memberLevel = memberLevel;
        this.createdAt = createdAt;
        this.lastActive = lastActive;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getMemberLevel() { return memberLevel; }
    public void setMemberLevel(String memberLevel) { this.memberLevel = memberLevel; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getLastActive() { return lastActive; }
    public void setLastActive(long lastActive) { this.lastActive = lastActive; }
}
