package com.example.airag.model;

public class DocChunk {
    private final String id;
    private final String text;
    private final int page;

    public DocChunk(String id, String text, int page) {
        this.id = id;
        this.text = text;
        this.page = page;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public int getPage() { return page; }
}
