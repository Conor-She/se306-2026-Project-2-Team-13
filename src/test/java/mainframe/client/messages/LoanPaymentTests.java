package mainframe.client.messages;

import mainframe.client.*;
import mainframe.client.messages.loan.AddPayment;
import mainframe.client.messages.loan.Create;
import mainframe.client.messages.loan.DeletePayment;
import mainframe.client.messages.loan.List;
import mainframe.client.messages.loanPayment.Retrieve;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

public class LoanPaymentTests {
    @Test
    void sendsToServer() throws IOException, InterruptedException {
        // login
        Connection connection = new HttpConnection(TestConstants.BASE_URL);
        connection.Login("test", "test");

        // create a fresh customer and loan so earlier payments can't have paid it off
        String name = "test_" + Instant.now().toEpochMilli();
        mainframe.client.messages.customer.Create customerMessage = sendMessage(
                connection,
                new mainframe.client.messages.customer.Create("2001-02-03", "Smith", name, "New Zealand", 1, "Mr"),
                "createCustomer");
        String customerNumber = customerMessage.getNumber();

        Create createLoanMessage = sendMessage(
                connection,
                new Create(customerNumber, 100.00, Create.PAYMENTFREQUENCY_WEEKLY, 2000.00, 2.00, Create.RATEFREQUENCY_YEARLY, Create.RATETYPE_FIXED, "2030-01-02", "2025-01-02"),
                "createLoan");
        String loanNumber = createLoanMessage.getLoanNumber();

        // search for a loan
        List listLoansMessage = sendMessage(
                connection,
                new List(customerNumber, 1, 50),
                "list");

        // add a payment
        AddPayment addMessage = sendMessage(
                connection,
                new AddPayment(100.00, "2025-12-24", loanNumber),
                "add");
        long id = addMessage.getId();
        Assertions.assertAll(
                () -> Assertions.assertNotEquals(0, id, "id should not be zero"),
                () -> Assertions.assertEquals(100, addMessage.getAmountPaid(), "amount paid does not match"),
                () -> Assertions.assertEquals("2025-12-24", addMessage.getDate(), "date does not match"),
                () -> Assertions.assertTrue( addMessage.getAmountRemaining() > 0, "amount remaining should be greater than zero"),
                () -> Assertions.assertTrue( addMessage.getInterestAmount() > 0, "interest amount should be greater than zero"),
                () -> Assertions.assertTrue( addMessage.getPrincipalAmount() > 0, "principal amount should be greater than zero")
        );

        // list the payments
        mainframe.client.messages.loanPayment.List listMessage = sendMessage(
                connection,
                new mainframe.client.messages.loanPayment.List(loanNumber, 1, 50),
                "list");
        Assertions.assertTrue(listMessage.getItems().length > 0, "No items returned");

        // retrieve the payment
        Retrieve retrieveMessage = sendMessage(
                connection,
                new Retrieve(id, loanNumber),
                "retrieve");
        Assertions.assertAll(
                () -> Assertions.assertNotEquals(0, id, "id should not be zero"),
                () -> Assertions.assertEquals(100, retrieveMessage.getAmountPaid(), "amount paid does not match"),
                () -> Assertions.assertEquals("2025-12-24", retrieveMessage.getDate(), "date does not match"),
                () -> Assertions.assertTrue( retrieveMessage.getAmountRemaining() > 0, "amount remaining should be greater than zero"),
                () -> Assertions.assertTrue( retrieveMessage.getInterestAmount() > 0, "interest amount should be greater than zero"),
                () -> Assertions.assertTrue( retrieveMessage.getPrincipalAmount() > 0, "principal amount should be greater than zero")
        );

        // delete the payment
        DeletePayment deleteMessage = sendMessage(
                connection,
                new DeletePayment(id, loanNumber),
                "delete");
    }

    private <T extends Message> T sendMessage(Connection connection, T message, String name) throws IOException, InterruptedException {
        Status status = message.send(connection);
        Thread.sleep(500);     // give the server time to apply the updates

        Assertions.assertAll(
                () -> Assertions.assertTrue(status.getWasSuccessful(), name + " message failed"),
                () -> Assertions.assertNull(status.getErrorMessage(), name + " message failed")
        );

        return message;
    }
}
