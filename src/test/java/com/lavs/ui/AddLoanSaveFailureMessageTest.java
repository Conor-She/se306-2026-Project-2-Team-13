package com.lavs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lavs.business.LoanPrerequisiteChecker;
import com.lavs.business.LoanPrerequisiteException;
import com.lavs.business.LoanSubmissionException;
import com.lavs.data.MainframeException;
import org.junit.jupiter.api.Test;

class AddLoanSaveFailureMessageTest {

    private static final String BORROWER = "Amelia Adams";

    private static String describe(RuntimeException failure) {
        return AddLoanController.describeSaveFailure(failure, BORROWER);
    }

    @Test
    void explainsAMissingMailingAddress() {
        String message = describe(
                new LoanPrerequisiteException(LoanPrerequisiteChecker.MAILING_ADDRESS_MESSAGE));

        assertTrue(message.startsWith("Amelia Adams has no mailing address recorded."));
        assertTrue(message.endsWith("No loan has been saved."));
    }

    @Test
    void explainsMissingPhoneAndEmail() {
        String message = describe(
                new LoanPrerequisiteException(LoanPrerequisiteChecker.CONTACT_MESSAGE));

        assertTrue(message.startsWith("Amelia Adams has no phone number or email recorded."));
        assertTrue(message.endsWith("No loan has been saved."));
    }

    @Test
    void explainsAMissingGuarantor() {
        String message = describe(new LoanPrerequisiteException("no employer, no guarantor"));

        assertTrue(message.contains("needs a guarantor"));
        assertTrue(message.endsWith("No loan has been saved."));
    }

    @Test
    void explainsAnUnreachableMainframe() {
        String message = describe(
                new MainframeException(MainframeException.Type.UNAVAILABLE, 10, "down"));

        assertTrue(message.contains("could not be reached"));
        assertTrue(message.contains("no loan has been saved"));
    }

    @Test
    void passesOnWhatTheMainframeRejected() {
        String message = describe(
                new MainframeException(MainframeException.Type.BUSINESS_ERROR, 3, "Invalid rate"));

        assertTrue(message.contains("Invalid rate"));
        assertTrue(message.contains("no loan has been saved"));
    }

    @Test
    void keepsTheWizardsOwnMessagesAsTheyWere() {
        assertEquals("A new loan must start with status New.",
                describe(new IllegalArgumentException("A new loan must start with status New.")));
    }

    @Test
    void passesOnAPartlyCompletedLoanMessage() {
        String text = "The loan could not be completed and has been cancelled (loan 1): down";

        assertEquals(text, describe(new LoanSubmissionException(text, new RuntimeException())));
    }

    @Test
    void namesAnUnexpectedErrorEvenWithoutAMessage() {
        assertTrue(describe(new IllegalStateException()).contains("IllegalStateException"));
    }
}