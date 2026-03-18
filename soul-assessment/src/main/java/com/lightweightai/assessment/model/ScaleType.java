package com.lightweightai.assessment.model;

/** Supported psychological assessment scales. */
public enum ScaleType {
    PHQ9("患者健康问卷-9", 9),
    GAD7("广泛性焦虑量表-7", 7),
    PSS10("压力知觉量表-10", 10);

    private final String displayName;
    private final int questionCount;

    ScaleType(String displayName, int questionCount) {
        this.displayName = displayName;
        this.questionCount = questionCount;
    }

    public String getDisplayName() {
        return displayName;
    }
    public int getQuestionCount() {
        return questionCount;
    }
}
