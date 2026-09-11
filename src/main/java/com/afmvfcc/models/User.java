package com.afmvfcc.models;

import java.time.LocalDateTime;

public class User {
    private int id;
    private String fullName;
    private String username;
    private String passwordHash;
    private String email;
    private String phone;
    private String roleTitle;
    private String photoPath;
    private boolean superAdmin;
    private boolean active;
    private int failedAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;

    public User() {}

    public User(int id, String fullName, String username, String email,
                String phone, String roleTitle, String photoPath,
                boolean superAdmin, boolean active) {
        this.id         = id;
        this.fullName   = fullName;
        this.username   = username;
        this.email      = email;
        this.phone      = phone;
        this.roleTitle  = roleTitle;
        this.photoPath  = photoPath;
        this.superAdmin = superAdmin;
        this.active     = active;
    }

    // ── Getters & Setters ──────────────────────────────────────

    public int getId()                          { return id; }
    public void setId(int id)                   { this.id = id; }

    public String getFullName()                 { return fullName; }
    public void setFullName(String fullName)    { this.fullName = fullName; }

    public String getUsername()                 { return username; }
    public void setUsername(String username)    { this.username = username; }

    public String getPasswordHash()             { return passwordHash; }
    public void setPasswordHash(String hash)    { this.passwordHash = hash; }

    public String getEmail()                    { return email; }
    public void setEmail(String email)          { this.email = email; }

    public String getPhone()                    { return phone; }
    public void setPhone(String phone)          { this.phone = phone; }

    public String getRoleTitle()                { return roleTitle; }
    public void setRoleTitle(String roleTitle)  { this.roleTitle = roleTitle; }

    public String getPhotoPath()                { return photoPath; }
    public void setPhotoPath(String photoPath)  { this.photoPath = photoPath; }

    public boolean isSuperAdmin()               { return superAdmin; }
    public void setSuperAdmin(boolean superAdmin){ this.superAdmin = superAdmin; }

    public boolean isActive()                   { return active; }
    public void setActive(boolean active)       { this.active = active; }

    public int getFailedAttempts()              { return failedAttempts; }
    public void setFailedAttempts(int attempts) { this.failedAttempts = attempts; }

    public LocalDateTime getLockedUntil()       { return lockedUntil; }
    public void setLockedUntil(LocalDateTime t) { this.lockedUntil = t; }

    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void setCreatedAt(LocalDateTime t)   { this.createdAt = t; }

    @Override
    public String toString() { return fullName + " (" + username + ")"; }
}
