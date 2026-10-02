package com.afmvfcc.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A board action item — assigned to a member, optionally raised at a meeting, tracked to completion. */
public class BoardTask {
    private int           id;
    private String        title;
    private String        description;
    private int           assignedMemberId;
    private String        assignedName;
    private String        assigneePhoto;
    private int           meetingId;
    private String        meetingTitle;
    private LocalDate     dueDate;
    private String        status;      // Open | In Progress | Done
    private String        report;
    private LocalDateTime reportedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public BoardTask() {}

    public int           getId()                          { return id; }
    public void          setId(int id)                    { this.id = id; }

    public String        getTitle()                       { return title; }
    public void          setTitle(String t)               { this.title = t; }

    public String        getDescription()                 { return description; }
    public void          setDescription(String d)         { this.description = d; }

    public int           getAssignedMemberId()            { return assignedMemberId; }
    public void          setAssignedMemberId(int id)      { this.assignedMemberId = id; }

    public String        getAssignedName()                { return assignedName; }
    public void          setAssignedName(String n)        { this.assignedName = n; }

    public String        getAssigneePhoto()               { return assigneePhoto; }
    public void          setAssigneePhoto(String p)       { this.assigneePhoto = p; }

    public int           getMeetingId()                   { return meetingId; }
    public void          setMeetingId(int id)             { this.meetingId = id; }

    public String        getMeetingTitle()                { return meetingTitle; }
    public void          setMeetingTitle(String t)        { this.meetingTitle = t; }

    public LocalDate     getDueDate()                     { return dueDate; }
    public void          setDueDate(LocalDate d)          { this.dueDate = d; }

    public String        getStatus()                      { return status; }
    public void          setStatus(String s)              { this.status = s; }

    public String        getReport()                      { return report; }
    public void          setReport(String r)              { this.report = r; }

    public LocalDateTime getReportedAt()                  { return reportedAt; }
    public void          setReportedAt(LocalDateTime t)   { this.reportedAt = t; }

    public LocalDateTime getCompletedAt()                 { return completedAt; }
    public void          setCompletedAt(LocalDateTime t)  { this.completedAt = t; }

    public LocalDateTime getCreatedAt()                   { return createdAt; }
    public void          setCreatedAt(LocalDateTime t)    { this.createdAt = t; }

    public boolean isDone() { return "Done".equals(status); }

    /** Past its due date and not yet done. */
    public boolean isOverdue() {
        return !isDone() && dueDate != null && dueDate.isBefore(LocalDate.now());
    }

    /** Status as shown to the user — "Overdue" replaces Open/In Progress once the due date has passed. */
    public String getDisplayStatus() {
        return isOverdue() ? "Overdue" : status;
    }
}
