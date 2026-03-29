package com.lightweightai.web.skillcreator;

import java.time.Instant;

/**
 * 不可变 Skill 版本快照
 */
public class SkillVersion {

    private String id;
    private String skillId;
    private String versionTag;    // semantic: 1.0.0, 1.0.1
    private String snapshotJson;  // 完整 SkillDraft JSON
    private String testRunId;     // 通过该版本的测试运行 ID
    private String status;        // staged | active | archived
    private Instant createdAt;
    private Instant activatedAt;

    public SkillVersion() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }

    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String versionTag) { this.versionTag = versionTag; }

    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }

    public String getTestRunId() { return testRunId; }
    public void setTestRunId(String testRunId) { this.testRunId = testRunId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
}
