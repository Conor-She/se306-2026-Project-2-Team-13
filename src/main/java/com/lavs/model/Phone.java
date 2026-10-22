package com.lavs.model;

// Stores one phone number linked to a customer.
public class Phone {
    private Long phoneId;
    private String customerId;
    private String number;
    private boolean isPrimary;
    private boolean sendTXT;

    public Phone(Long phoneId, String customerId, String number, boolean isPrimary, boolean sendTXT) {
        this.phoneId = phoneId;
        this.customerId = customerId;
        this.number = number;
        this.isPrimary = isPrimary;
        this.sendTXT = sendTXT;
    }

    public Long getPhoneId() {
        return phoneId;
    }

    public void setPhoneId(Long phoneId) {
        this.phoneId = phoneId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public boolean isSendTXT() {
        return sendTXT;
    }

    public void setSendTXT(boolean sendTXT) {
        this.sendTXT = sendTXT;
    }
}
