package com.afmvfcc.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Member {
    private int id;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String address;
    private int subBranchId;
    private String subBranchName;
    private String phone;
    private String email;
    private LocalDate baptismDate;
    private String maritalStatus = "Single";
    private boolean spouseIsMember = false;
    private Integer spouseMemberId;
    private String employmentStatus = "Employed";
    private String nextOfKinName;
    private String nextOfKinPhone;
    private Integer nextOfKinMemberId;
    private Integer familyId;
    private String familyName;
    private String photoPath;
    private boolean fullTime;
    private boolean active;
    private boolean deleted;
    private LocalDate dateJoined;
    private boolean deceased;
    private LocalDateTime createdAt;

    public Member() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dob) {
        this.dateOfBirth = dob;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getSubBranchId() {
        return subBranchId;
    }

    public void setSubBranchId(int id) {
        this.subBranchId = id;
    }

    public String getSubBranchName() {
        return subBranchName;
    }

    public void setSubBranchName(String name) {
        this.subBranchName = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getBaptismDate() {
        return baptismDate;
    }

    public void setBaptismDate(LocalDate d) {
        this.baptismDate = d;
    }

    public String getMaritalStatus() {
        return maritalStatus != null ? maritalStatus : "Single";
    }

    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public boolean isSpouseMember() {
        return spouseIsMember;
    }

    public void setSpouseMember(boolean spouseIsMember) {
        this.spouseIsMember = spouseIsMember;
    }

    public Integer getSpouseMemberId() {
        return spouseMemberId;
    }

    public void setSpouseMemberId(Integer spouseMemberId) {
        this.spouseMemberId = spouseMemberId;
    }

    public String getEmploymentStatus() {
        return employmentStatus != null ? employmentStatus : "Employed";
    }

    public void setEmploymentStatus(String employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public boolean isDeceased() {
        return deceased;
    }

    public void setDeceased(boolean deceased) {
        this.deceased = deceased;
    }

    public String getNextOfKinName() {
        return nextOfKinName;
    }

    public void setNextOfKinName(String name) {
        this.nextOfKinName = name;
    }

    public String getNextOfKinPhone() {
        return nextOfKinPhone;
    }

    public void setNextOfKinPhone(String phone) {
        this.nextOfKinPhone = phone;
    }

    public Integer getNextOfKinMemberId() {
        return nextOfKinMemberId;
    }

    public void setNextOfKinMemberId(Integer id) {
        this.nextOfKinMemberId = id;
    }

    public Integer getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Integer familyId) {
        this.familyId = familyId;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public boolean isFullTime() {
        return fullTime;
    }

    public void setFullTime(boolean fullTime) {
        this.fullTime = fullTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDate getDateJoined() {
        return dateJoined;
    }

    public void setDateJoined(LocalDate d) {
        this.dateJoined = d;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime t) {
        this.createdAt = t;
    }

    // Availability label for display
    public String getAvailabilityLabel() {
        return fullTime ? "Full Time" : "Part Time";
    }

    // Status label for display
    public String getStatusLabel() {
        return active ? "Active" : "Inactive";
    }

    @Override
    public String toString() {
        return fullName;
    }
}
