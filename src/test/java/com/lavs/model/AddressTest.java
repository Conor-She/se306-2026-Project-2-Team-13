package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AddressTest {
    @Test
    void storesAddressDetails() {
        Address address = new Address(
                1L, "C001", "10 Queen Street", "Central", "Auckland", "1010",
                "New Zealand", true);

        assertAll(
                () -> assertEquals(1L, address.getAddressId()),
                () -> assertEquals("C001", address.getCustomerId()),
                () -> assertEquals("10 Queen Street", address.getStreet()),
                () -> assertEquals("Central", address.getSuburb()),
                () -> assertEquals("Auckland", address.getCity()),
                () -> assertEquals("1010", address.getPostCode()),
                () -> assertEquals("New Zealand", address.getCountry()),
                () -> assertTrue(address.isMailing()));
    }

    @Test
    void updatesAddressDetails() {
        Address address = new Address(null, null, null, null, null, null, null, true);

        address.setAddressId(2L);
        address.setCustomerId("C002");
        address.setStreet("20 George Street");
        address.setSuburb("Dunedin Central");
        address.setCity("Dunedin");
        address.setPostCode("9016");
        address.setCountry("New Zealand");
        address.setMailing(false);

        assertAll(
                () -> assertEquals(2L, address.getAddressId()),
                () -> assertEquals("C002", address.getCustomerId()),
                () -> assertEquals("20 George Street", address.getStreet()),
                () -> assertEquals("Dunedin Central", address.getSuburb()),
                () -> assertEquals("Dunedin", address.getCity()),
                () -> assertEquals("9016", address.getPostCode()),
                () -> assertEquals("New Zealand", address.getCountry()),
                () -> assertFalse(address.isMailing()));
    }
}
