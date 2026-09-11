package com.afmvfcc.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class WebsiteBlog {
    private int id;
    private String title;
    private String author;
    private String content;
    private LocalDateTime publishedAt;
    private int createdBy;

    public WebsiteBlog() {}

    public int getId()                          { return id; }
    public void setId(int id)                   { this.id = id; }
    public String getTitle()                    { return title; }
    public void setTitle(String t)              { this.title = t; }
    public String getAuthor()                   { return author; }
    public void setAuthor(String a)             { this.author = a; }
    public String getContent()                  { return content; }
    public void setContent(String c)            { this.content = c; }
    public LocalDateTime getPublishedAt()       { return publishedAt; }
    public void setPublishedAt(LocalDateTime t) { this.publishedAt = t; }
    public int getCreatedBy()                   { return createdBy; }
    public void setCreatedBy(int id)            { this.createdBy = id; }

    @Override
    public String toString()                    { return title; }
}
