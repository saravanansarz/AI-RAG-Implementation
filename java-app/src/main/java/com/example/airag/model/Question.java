package com.example.airag.model;

import java.util.List;

public class Question {
    private String question;
    private String type;
    private List<String> expectedValues;

    public Question() {}

    public Question(String question, String type, List<String> expectedValues) {
        this.question = question;
        this.type = type;
        this.expectedValues = expectedValues;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getExpectedValues() {
        return expectedValues;
    }

    public void setExpectedValues(List<String> expectedValues) {
        this.expectedValues = expectedValues;
    }
}
