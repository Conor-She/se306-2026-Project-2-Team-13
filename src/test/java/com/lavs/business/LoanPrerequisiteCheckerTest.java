package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lavs.model.Address;
import com.lavs.model.Email;
import com.lavs.model.Phone;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanPrerequisiteCheckerTest {

    private final LoanPrerequisiteChecker checker = new LoanPrerequisiteChecker();

    // Helpers, so each test only states what it is actually testing
    private Address address(boolean isMailing) {
        return new Address(1L, "C001", "5 Queen St", "CBD", "Auckland", "1010", "NZ", isMailing);
    }

    private Phone phone() {
        return new Phone(1L, "C001", "0211234567", true, true);
    }

    private Email email() {
        return new Email(1L, "C001", "jane@example.com", true);
    }

    // --- Mailing address check, in isolation ---

    @Test
    void passesWhenCustomerHasMailingAddress() {
        assertDoesNotThrow(() -> checker.checkHasMailingAddress(List.of(address(true))));
    }

    // Only one of several addresses needs to be the mailing one
    @Test
    void passesWhenOnlyOneOfSeveralAddressesIsMailing() {
        assertDoesNotThrow(() ->
                checker.checkHasMailingAddress(List.of(address(false), address(true))));
    }

    @Test
    void blocksWhenCustomerHasNoAddresses() {
        LoanPrerequisiteException exception = assertThrows(LoanPrerequisiteException.class,
                () -> checker.checkHasMailingAddress(Collections.emptyList()));

        assertEquals(LoanPrerequisiteChecker.MAILING_ADDRESS_MESSAGE, exception.getMessage());
    }

    @Test
    void blocksWhenNoAddressIsMarkedMailing() {
        assertThrows(LoanPrerequisiteException.class,
                () -> checker.checkHasMailingAddress(List.of(address(false))));
    }

    @Test
    void blocksWhenAddressListIsNull() {
        assertThrows(LoanPrerequisiteException.class,
                () -> checker.checkHasMailingAddress(null));
    }

    // --- Alternate contact check, in isolation ---

    @Test
    void passesWithPhoneOnly() {
        assertDoesNotThrow(() ->
                checker.checkHasAlternateContact(List.of(phone()), Collections.emptyList()));
    }

    @Test
    void passesWithEmailOnly() {
        assertDoesNotThrow(() ->
                checker.checkHasAlternateContact(Collections.emptyList(), List.of(email())));
    }

    @Test
    void passesWithBothPhoneAndEmail() {
        assertDoesNotThrow(() ->
                checker.checkHasAlternateContact(List.of(phone()), List.of(email())));
    }

    @Test
    void passesWhenPhoneListIsNullButEmailExists() {
        assertDoesNotThrow(() -> checker.checkHasAlternateContact(null, List.of(email())));
    }

    @Test
    void passesWhenEmailListIsNullButPhoneExists() {
        assertDoesNotThrow(() -> checker.checkHasAlternateContact(List.of(phone()), null));
    }

    @Test
    void blocksWhenNoPhoneAndNoEmail() {
        LoanPrerequisiteException exception = assertThrows(LoanPrerequisiteException.class,
                () -> checker.checkHasAlternateContact(Collections.emptyList(),
                        Collections.emptyList()));

        assertEquals(LoanPrerequisiteChecker.CONTACT_MESSAGE, exception.getMessage());
    }

    @Test
    void blocksWhenBothContactListsAreNull() {
        assertThrows(LoanPrerequisiteException.class,
                () -> checker.checkHasAlternateContact(null, null));
    }

    // --- Combined check, in isolation ---
    @Test
    void checkAllPassesWhenBothPrerequisitesAreMet() {
        assertDoesNotThrow(() -> checker.checkAll(
                List.of(address(true)), List.of(phone()), Collections.emptyList()));
    }

    @Test
    void checkAllBlocksWhenOnlyMailingAddressIsMissing() {
        LoanPrerequisiteException exception = assertThrows(LoanPrerequisiteException.class,
                () -> checker.checkAll(
                        Collections.emptyList(), List.of(phone()), Collections.emptyList()));

        assertEquals(LoanPrerequisiteChecker.MAILING_ADDRESS_MESSAGE, exception.getMessage());
    }

    @Test
    void checkAllBlocksWhenOnlyAlternateContactIsMissing() {
        LoanPrerequisiteException exception = assertThrows(LoanPrerequisiteException.class,
                () -> checker.checkAll(
                        List.of(address(true)), Collections.emptyList(), Collections.emptyList()));

        assertEquals(LoanPrerequisiteChecker.CONTACT_MESSAGE, exception.getMessage());
    }

    // When both are missing, the mailing address check runs first
    @Test
    void checkAllReportsMailingAddressFirstWhenBothAreMissing() {
        LoanPrerequisiteException exception = assertThrows(LoanPrerequisiteException.class,
                () -> checker.checkAll(Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList()));

        assertEquals(LoanPrerequisiteChecker.MAILING_ADDRESS_MESSAGE, exception.getMessage());
    }
}