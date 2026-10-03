package com.lavs.model;

public class Email {
    private Long emailId;
    private String customerId;
    private String emailAddress;
    private boolean isPrimary;

    public Email(Long emailId, String customerId, String emailAddress, boolean isPrimary) {
        this.emailId = emailId;
        this.customerId = customerId;
        this.emailAddress = emailAddress;
        this.isPrimary = isPrimary;
    }

    public Long getEmailId() {
        return emailId;
    }

    public void setEmailId(Long emailId) {
        this.emailId = emailId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean isPrimary) {
        this.isPrimary = isPrimary;
    }
}
