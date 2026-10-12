package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.data.MainframeException;
import com.lavs.model.Customer;
import com.lavs.model.Guarantor;

class GuarantorServiceTest {
    private FakeMainframeGateway gateway;
    private GuarantorService service;

    @BeforeEach
    void setUp() {
        gateway = new FakeMainframeGateway();
        service = new GuarantorService(gateway);
        gateway.createCustomer(customer("C001", "Primary"));
        gateway.createCustomer(customer("C002", "First Guarantor"));
        gateway.createCustomer(customer("C003", "Second Guarantor"));
    }

    @Test
    void addsGuarantorAndRecordsRelationshipType() {
        Guarantor added = service.addGuarantor("C001", "C002", "Parent");

        assertAll(
                () -> assertNotNull(added.getGuarantorId()),
                () -> assertEquals("C001", added.getCustomerId()),
                () -> assertEquals("C002", added.getGuarantorCustomerId()),
                () -> assertEquals("Parent", added.getRelationshipType()),
                () -> assertEquals(1, gateway.getGuarantors("C001").size()));
    }

    @Test
    void updatesExistingGuarantor() {
        Guarantor added = service.addGuarantor("C001", "C002", "Parent");

        Guarantor updated = service.updateGuarantor(
                added.getGuarantorId(), "C001", "C003", "Spouse");

        assertAll(
                () -> assertEquals(added.getGuarantorId(), updated.getGuarantorId()),
                () -> assertEquals("C003", updated.getGuarantorCustomerId()),
                () -> assertEquals("Spouse", updated.getRelationshipType()),
                () -> assertEquals(1, gateway.getGuarantors("C001").size()),
                () -> assertEquals(
                        "Spouse",
                        gateway.getGuarantors("C001").get(0).getRelationshipType()));
    }

    @Test
    void deletesExistingGuarantor() {
        Guarantor added = service.addGuarantor("C001", "C002", "Parent");

        service.deleteGuarantor(added.getGuarantorId());

        assertTrue(gateway.getGuarantors("C001").isEmpty());
    }

    @Test
    void rejectsMissingPrimaryCustomer() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addGuarantor("MISSING", "C002", "Parent"));

        assertEquals("Customer 'MISSING' does not exist", exception.getMessage());
        assertTrue(gateway.getGuarantors("MISSING").isEmpty());
    }

    @Test
    void rejectsMissingGuarantorCustomer() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addGuarantor("C001", "MISSING", "Parent"));

        assertEquals("Customer 'MISSING' does not exist", exception.getMessage());
        assertTrue(gateway.getGuarantors("C001").isEmpty());
    }

    @Test
    void validatesRequiredFieldsAndDifferentCustomers() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new GuarantorService(null)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.addGuarantor(null, "C002", "Parent")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.addGuarantor("C001", " ", "Parent")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.addGuarantor("C001", "C002", " ")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.addGuarantor("C001", "C001", "Self")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.updateGuarantor(
                                " ", "C001", "C002", "Parent")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.deleteGuarantor(null)));
    }

    @Test
    void updateAndDeleteRejectUnknownGuarantors() {
        assertAll(
                () -> assertThrows(
                        MainframeException.class,
                        () -> service.updateGuarantor(
                                "MISSING", "C001", "C002", "Parent")),
                () -> assertThrows(
                        MainframeException.class,
                        () -> service.deleteGuarantor("MISSING")));
    }

    @Test
    void passesMainframeUnavailableThrough() {
        gateway.simulateUnavailable(true);

        MainframeException exception = assertThrows(
                MainframeException.class,
                () -> service.addGuarantor("C001", "C002", "Parent"));

        assertEquals(MainframeException.Type.UNAVAILABLE, exception.getType());
    }

    private Customer customer(String customerId, String firstName) {
        return new Customer(
                customerId,
                "Mx",
                firstName,
                "Customer",
                LocalDate.of(1990, 1, 1),
                "New Zealand");
    }
}
