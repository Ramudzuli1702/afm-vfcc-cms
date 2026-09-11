package com.afmvfcc.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class BoardMeeting {
    private int           id;
    private String        title;
    private LocalDate     meetingDate;
    private String        location;
    private String        agenda;
    private String        minutesText;
    private String        status;    
    private int           createdBy;
    private LocalDateTime createdAt;

    private String agendaDocPath;
    private String minutesDocPath;

    public BoardMeeting() {}

    public int           getId()                        { return id; }
    public void          setId(int id)                  { this.id = id; }

    public String        getTitle()                     { return title; }
    public void          setTitle(String t)             { this.title = t; }

    public LocalDate     getMeetingDate()               { return meetingDate; }
    public void          setMeetingDate(LocalDate d)    { this.meetingDate = d; }

    public String        getLocation()                  { return location; }
    public void          setLocation(String l)          { this.location = l; }

    public String        getAgenda()                    { return agenda; }
    public void          setAgenda(String a)            { this.agenda = a; }

    public String        getMinutesText()               { return minutesText; }
    public void          setMinutesText(String m)       { this.minutesText = m; }

    public String        getStatus()                    { return status; }
    public void          setStatus(String s)            { this.status = s; }

    public int           getCreatedBy()                 { return createdBy; }
    public void          setCreatedBy(int id)           { this.createdBy = id; }

    public LocalDateTime getCreatedAt()                 { return createdAt; }
    public void          setCreatedAt(LocalDateTime t)  { this.createdAt = t; }

    public String        getAgendaDocPath()                     { return agendaDocPath; }
    public void          setAgendaDocPath(String agendaDocPath) { this.agendaDocPath = agendaDocPath; }

    public String        getMinutesDocPath()                      { return minutesDocPath; }
    public void          setMinutesDocPath(String minutesDocPath) { this.minutesDocPath = minutesDocPath; }

    @Override
    public String toString() { return title + " (" + meetingDate + ")"; }
}
