package com.lavs.model;

// Stores a customer's employment details.
public class Employer {
    private Long employerId;
    private String customerId;
    private String companyName;
    private String jobTitle;
    private double income;
    private String employmentType;

    public Employer(Long employerId, String customerId, String companyName, String jobTitle,
            double income, String employmentType) {
        this.employerId = employerId;
        this.customerId = customerId;
        this.companyName = companyName;
        this.jobTitle = jobTitle;
        this.income = income;
        this.employmentType = employmentType;
    }

    public Long getEmployerId() {
        return employerId;
    }

    public void setEmployerId(Long employerId) {
        this.employerId = employerId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public double getIncome() {
        return income;
    }

    public void setIncome(double income) {
        this.income = income;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }
}
