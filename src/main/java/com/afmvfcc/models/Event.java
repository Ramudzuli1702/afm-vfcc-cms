package com.afmvfcc.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Event {
    private int id;
    private String title;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private String location;
    private String description;
    private String category;
    private int createdBy;
    private LocalDateTime createdAt;

    public Event() {}

    public int getId()                          { return id; }
    public void setId(int id)                   { this.id = id; }
    public String getTitle()                    { return title; }
    public void setTitle(String t)              { this.title = t; }
    public LocalDate getEventDate()             { return eventDate; }
    public void setEventDate(LocalDate d)       { this.eventDate = d; }
    public LocalTime getEventTime()             { return eventTime; }
    public void setEventTime(LocalTime t)       { this.eventTime = t; }
    public String getLocation()                 { return location; }
    public void setLocation(String l)           { this.location = l; }
    public String getDescription()              { return description; }
    public void setDescription(String d)        { this.description = d; }
    public String getCategory()                 { return category; }
    public void setCategory(String c)           { this.category = c; }
    public int getCreatedBy()                   { return createdBy; }
    public void setCreatedBy(int id)            { this.createdBy = id; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void setCreatedAt(LocalDateTime t)   { this.createdAt = t; }

    @Override
    public String toString()                    { return title + " (" + eventDate + ")"; }
}
