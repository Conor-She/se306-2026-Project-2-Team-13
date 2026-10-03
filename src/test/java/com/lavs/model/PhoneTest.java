package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhoneTest {
    @Test
    void storesPhoneDetails() {
        Phone phone = new Phone(1L, "C001", "0211234567", true, true);

        assertAll(
                () -> assertEquals(1L, phone.getPhoneId()),
                () -> assertEquals("C001", phone.getCustomerId()),
                () -> assertEquals("0211234567", phone.getNumber()),
                () -> assertTrue(phone.isPrimary()),
                () -> assertTrue(phone.isSendTXT()));
    }

    @Test
    void updatesPhoneDetails() {
        Phone phone = new Phone(null, null, null, true, true);

        phone.setPhoneId(2L);
        phone.setCustomerId("C002");
        phone.setNumber("0227654321");
        phone.setPrimary(false);
        phone.setSendTXT(false);

        assertAll(
                () -> assertEquals(2L, phone.getPhoneId()),
                () -> assertEquals("C002", phone.getCustomerId()),
                () -> assertEquals("0227654321", phone.getNumber()),
                () -> assertFalse(phone.isPrimary()),
                () -> assertFalse(phone.isSendTXT()));
    }
}
