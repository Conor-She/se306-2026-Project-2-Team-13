package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GuarantorTest {

    // Confirms a fully valid Guarantor builds and its fields are retrievable
    @Test
    void constructsWithValidFields() {
        Guarantor guarantor = new Guarantor("G001", "C001", "C002", "Parent");

        assertEquals("G001", guarantor.getGuarantorId());
        assertEquals("Parent", guarantor.getRelationshipType());
    }

    // The mainframe assigns the ID when a new relationship is saved
    @Test
    void allowsMissingIdBeforeCreation() {
        Guarantor guarantor = new Guarantor(null, "C001", "C002", "Parent");

        assertNull(guarantor.getGuarantorId());
    }

    // An empty guarantorCustomerId means no one is actually guaranteeing the loan, so it must be rejected
    @Test
    void rejectsBlankGuarantorCustomerId() {
        assertThrows(IllegalArgumentException.class, () ->
                new Guarantor("G001", "C001", "", "Parent"));
    }

    // A customer cannot guarantee their own loan, so matching IDs must be rejected
    @Test
    void rejectsCustomerBeingTheirOwnGuarantor() {
        assertThrows(IllegalArgumentException.class, () ->
                new Guarantor("G001", "C001", "C001", "Self"));
    }

    @Test
    void rejectsBlankRelationshipType() {
        assertThrows(IllegalArgumentException.class, () ->
                new Guarantor("G001", "C001", "C002", " "));
    }
}
