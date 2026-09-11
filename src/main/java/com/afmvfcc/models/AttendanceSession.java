package com.afmvfcc.models;

import java.time.LocalDate;

public class AttendanceSession {
    private int       id;
    private String    sessionName;
    private LocalDate sessionDate;
    private int       ministryId;
    private String    ministryName;
    private boolean   closed;   

    public int getId()                       { return id; }
    public void setId(int id)                { this.id = id; }

    public String getSessionName()           { return sessionName; }
    public void setSessionName(String n)     { this.sessionName = n; }

    public LocalDate getSessionDate()        { return sessionDate; }
    public void setSessionDate(LocalDate d)  { this.sessionDate = d; }

    public int getMinistryId()               { return ministryId; }
    public void setMinistryId(int id)        { this.ministryId = id; }

    public String getMinistryName()          { return ministryName; }
    public void setMinistryName(String n)    { this.ministryName = n; }

    public boolean isClosed()               { return closed; }
    public void setClosed(boolean closed)   { this.closed = closed; }
}
