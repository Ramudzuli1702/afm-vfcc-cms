package com.afmvfcc.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class WebsiteEvent {
    private int id;
    private String title;
    private String description;
    private String imageFilename;
    private LocalDate eventDate;
    private LocalDateTime publishedAt;
    private int createdBy;

    public WebsiteEvent() {}

    public int getId()                              { return id; }
    public void setId(int id)                       { this.id = id; }
    public String getTitle()                        { return title; }
    public void setTitle(String t)                  { this.title = t; }
    public String getDescription()                  { return description; }
    public void setDescription(String d)            { this.description = d; }
    public String getImageFilename()                { return imageFilename; }
    public void setImageFilename(String f)          { this.imageFilename = f; }
    public LocalDate getEventDate()                 { return eventDate; }
    public void setEventDate(LocalDate d)           { this.eventDate = d; }
    public LocalDateTime getPublishedAt()           { return publishedAt; }
    public void setPublishedAt(LocalDateTime t)     { this.publishedAt = t; }
    public int getCreatedBy()                       { return createdBy; }
    public void setCreatedBy(int id)                { this.createdBy = id; }

    @Override
    public String toString()                        { return title; }
}
