package com.afmvfcc.models;

import java.time.LocalDateTime;

public class AuditEntry {
    private int id;
    private int userId;
    private String adminName;
    private String action;
    private LocalDateTime performedAt;

    public AuditEntry() {}

    public int getId()                          { return id; }
    public void setId(int id)                   { this.id = id; }
    public int getUserId()                      { return userId; }
    public void setUserId(int id)               { this.userId = id; }
    public String getAdminName()                { return adminName; }
    public void setAdminName(String name)       { this.adminName = name; }
    public String getAction()                   { return action; }
    public void setAction(String action)        { this.action = action; }
    public LocalDateTime getPerformedAt()       { return performedAt; }
    public void setPerformedAt(LocalDateTime t) { this.performedAt = t; }
}
