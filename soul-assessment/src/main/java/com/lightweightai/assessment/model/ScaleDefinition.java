package com.lightweightai.assessment.model;

import java.util.List;

/**
 * A scale definition: questions and their answer options.
 */
public record ScaleDefinition(
    ScaleType type,
    String title,
    String description,
    List<Question> questions,
    List<String> options,
    List<Integer> optionScores
) {
    public record Question(int index, String text) {}
}
