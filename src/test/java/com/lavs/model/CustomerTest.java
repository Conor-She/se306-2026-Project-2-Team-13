package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CustomerTest {
    @Test
    void storesCustomerDetails() {
        LocalDate dateOfBirth = LocalDate.of(1990, 5, 12);
        Customer customer = new Customer(
                "C001", "Ms", "Alex", "Taylor", dateOfBirth, "New Zealand");

        assertAll(
                () -> assertEquals("C001", customer.getCustomerId()),
                () -> assertEquals("Ms", customer.getTitle()),
                () -> assertEquals("Alex", customer.getFirstName()),
                () -> assertEquals("Taylor", customer.getLastName()),
                () -> assertEquals(dateOfBirth, customer.getDateOfBirth()),
                () -> assertEquals("New Zealand", customer.getNationality()));
    }

    @Test
    void updatesCustomerDetails() {
        Customer customer = new Customer(null, null, null, null, null, null);
        LocalDate dateOfBirth = LocalDate.of(1985, 8, 20);

        customer.setCustomerId("C002");
        customer.setTitle("Dr");
        customer.setFirstName("Jamie");
        customer.setLastName("Morgan");
        customer.setDateOfBirth(dateOfBirth);
        customer.setNationality("Australia");

        assertAll(
                () -> assertEquals("C002", customer.getCustomerId()),
                () -> assertEquals("Dr", customer.getTitle()),
                () -> assertEquals("Jamie", customer.getFirstName()),
                () -> assertEquals("Morgan", customer.getLastName()),
                () -> assertEquals(dateOfBirth, customer.getDateOfBirth()),
                () -> assertEquals("Australia", customer.getNationality()));
    }
}
