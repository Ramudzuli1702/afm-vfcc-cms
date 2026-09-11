package com.afmvfcc.models;

import java.time.LocalDateTime;

public class WelfareCase {
    private int id;
    private int memberId;
    private String memberName;
    private String reason;
    private int assignedWorkerId;
    private String assignedWorkerName;
    private String report;
    private String status;
    private LocalDateTime openedAt;
    private LocalDateTime completedAt;

    public WelfareCase() {}

    public int getId()                              { return id; }
    public void setId(int id)                       { this.id = id; }
    public int getMemberId()                        { return memberId; }
    public void setMemberId(int id)                 { this.memberId = id; }
    public String getMemberName()                   { return memberName; }
    public void setMemberName(String name)          { this.memberName = name; }
    public String getReason()                       { return reason; }
    public void setReason(String reason)            { this.reason = reason; }
    public int getAssignedWorkerId()                { return assignedWorkerId; }
    public void setAssignedWorkerId(int id)         { this.assignedWorkerId = id; }
    public String getAssignedWorkerName()           { return assignedWorkerName; }
    public void setAssignedWorkerName(String name)  { this.assignedWorkerName = name; }
    public String getReport()                       { return report; }
    public void setReport(String report)            { this.report = report; }
    public String getStatus()                       { return status; }
    public void setStatus(String status)            { this.status = status; }
    public LocalDateTime getOpenedAt()              { return openedAt; }
    public void setOpenedAt(LocalDateTime t)        { this.openedAt = t; }
    public LocalDateTime getCompletedAt()           { return completedAt; }
    public void setCompletedAt(LocalDateTime t)     { this.completedAt = t; }
}
