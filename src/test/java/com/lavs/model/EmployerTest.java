package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EmployerTest {
    @Test
    void storesEmployerDetails() {
        Employer employer = new Employer(
                1L, "C001", "Example Limited", "Engineer", 85000.00, "Full-time");

        assertAll(
                () -> assertEquals(1L, employer.getEmployerId()),
                () -> assertEquals("C001", employer.getCustomerId()),
                () -> assertEquals("Example Limited", employer.getCompanyName()),
                () -> assertEquals("Engineer", employer.getJobTitle()),
                () -> assertEquals(85000.00, employer.getIncome()),
                () -> assertEquals("Full-time", employer.getEmploymentType()));
    }

    @Test
    void updatesEmployerDetails() {
        Employer employer = new Employer(null, null, null, null, 0.0, null);

        employer.setEmployerId(2L);
        employer.setCustomerId("C002");
        employer.setCompanyName("Another Limited");
        employer.setJobTitle("Analyst");
        employer.setIncome(72000.00);
        employer.setEmploymentType("Part-time");

        assertAll(
                () -> assertEquals(2L, employer.getEmployerId()),
                () -> assertEquals("C002", employer.getCustomerId()),
                () -> assertEquals("Another Limited", employer.getCompanyName()),
                () -> assertEquals("Analyst", employer.getJobTitle()),
                () -> assertEquals(72000.00, employer.getIncome()),
                () -> assertEquals("Part-time", employer.getEmploymentType()));
    }
}
