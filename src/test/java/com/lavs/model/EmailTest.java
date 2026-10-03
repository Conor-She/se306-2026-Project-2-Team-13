package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailTest {
    @Test
    void storesEmailDetails() {
        Email email = new Email(1L, "C001", "alex@example.com", true);

        assertAll(
                () -> assertEquals(1L, email.getEmailId()),
                () -> assertEquals("C001", email.getCustomerId()),
                () -> assertEquals("alex@example.com", email.getEmailAddress()),
                () -> assertTrue(email.isPrimary()));
    }

    @Test
    void updatesEmailDetails() {
        Email email = new Email(null, null, null, true);

        email.setEmailId(2L);
        email.setCustomerId("C002");
        email.setEmailAddress("jamie@example.com");
        email.setPrimary(false);

        assertAll(
                () -> assertEquals(2L, email.getEmailId()),
                () -> assertEquals("C002", email.getCustomerId()),
                () -> assertEquals("jamie@example.com", email.getEmailAddress()),
                () -> assertFalse(email.isPrimary()));
    }
}
