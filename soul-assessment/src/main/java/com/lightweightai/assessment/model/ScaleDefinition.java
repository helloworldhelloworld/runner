package com.lightweightai.assessment.model;

import java.util.List;

/**
 * A scale definition: questions and their answer options.
 */
public class ScaleDefinition {

    private final ScaleType type;
    private final String title;
    private final String description;
    private final List<Question> questions;
    private final List<String> options;
    private final List<Integer> optionScores;

    public ScaleDefinition(ScaleType type, String title, String description,
                           List<Question> questions, List<String> options, List<Integer> optionScores) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.questions = questions;
        this.options = options;
        this.optionScores = optionScores;
    }

    public ScaleType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<Question> getQuestions() { return questions; }
    public List<String> getOptions() { return options; }
    public List<Integer> getOptionScores() { return optionScores; }

    public static class Question {
        private final int index;
        private final String text;

        public Question(int index, String text) {
            this.index = index;
            this.text = text;
        }

        public int getIndex() { return index; }
        public String getText() { return text; }
    }
}
