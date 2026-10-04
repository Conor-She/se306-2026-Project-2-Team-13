package com.lavs.model;

public class Employee {

    private final String employeeId;
    private final String userName;
    private final String passwordHash;
    private final String name;

    public Employee(String employeeId, String userName, String passwordHash, String name) {
        // Basic structural checks only
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException("employeeId must not be null or blank");
        }
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("userName must not be null or blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be null or blank");
        }

        // Assign all fields once the employee record is confirmed valid
        this.employeeId = employeeId;
        this.userName = userName;
        this.passwordHash = passwordHash;
        this.name = name;
    }

    // Getters
    public String getEmployeeId() {
        return employeeId;
    }

    public String getUserName() {
        return userName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }
}