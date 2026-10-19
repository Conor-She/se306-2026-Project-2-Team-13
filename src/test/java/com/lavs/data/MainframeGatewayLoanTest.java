package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lavs.model.Loan;
import java.time.LocalDate;
import java.util.HashMap;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MainframeGatewayLoanTest {

    // A loan.retrieve reply, with the status and rate type given
    private static MessageResponse loanReply(String status, String rateType) {
        return response("customerNumber", "C001", "loanNumber", "L001", "rateType", rateType,
                "rateAmount", "12.0", "rateFrequency", "Monthly", "paymentAmount", "1066.19",
                "paymentFrequency", "Monthly", "principal", "12000.0",
                "startDate", "2026-01-01", "renewalDate", "2028-01-01", "status", status);
    }

    @Test
    void readsALoanInFull() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                loanReply("New", mainframe.client.messages.loan.Create.RATETYPE_FIXED),
                noLoanTypeNote());
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Loan loan = gateway.getLoan("L001");

        assertEquals("L001", loan.getLoanId());
        assertEquals("C001", loan.getCustomerId());
        assertEquals(Loan.Status.NEW, loan.getStatus());
        assertEquals(12000.0, loan.getPrincipal());
        assertEquals(12.0, loan.getInterestRate());
        assertFalse(loan.isFloating());
        assertEquals(LocalDate.of(2026, 1, 1), loan.getStartDate());
        assertEquals(LocalDate.of(2028, 1, 1), loan.getRenewDate());
        assertEquals(1066.19, loan.getPaymentAmount());
        assertEquals(Loan.PaymentFrequency.MONTHLY, loan.getPaymentFrequency());
        assertEquals(Loan.RateFrequency.MONTHLY, loan.getRateFrequency());
        // The mainframe doesn't keep the type (apart from interest-only) or the term
        assertNull(loan.getType());
        assertNull(loan.getTermMonths());
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(2)).send(request.capture());
        assertEquals("loan.retrieve", request.getAllValues().get(0).getRequestType());
        assertEquals("L001", request.getAllValues().get(0).getValue("number"));
        assertEquals("loanNote.list", request.getAllValues().get(1).getRequestType());
    }

    // Interest-only is the one loan type the mainframe reports back, and
    // floating is read from its rate type
    @Test
    void readsInterestOnlyAndFloatingRateTypes() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                loanReply("New", mainframe.client.messages.loan.Create.RATETYPE_INTERESTONLY),
                loanReply("New", mainframe.client.messages.loan.Create.RATETYPE_FLOATING),
                noLoanTypeNote());
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Loan interestOnly = gateway.getLoan("L001");
        Loan floating = gateway.getLoan("L001");

        assertEquals(Loan.Type.INTEREST_ONLY, interestOnly.getType());
        assertFalse(interestOnly.isFloating());
        assertNull(floating.getType());
        assertTrue(floating.isFloating());
    }

    @Test
    void restoresTableAndReducingBalanceTypesFromLoanMetadata() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                loanReply("New", mainframe.client.messages.loan.Create.RATETYPE_FIXED),
                loanTypeNote(41),
                response("lines", "TABLE_LOAN"),
                loanReply("New", mainframe.client.messages.loan.Create.RATETYPE_FIXED),
                loanTypeNote(42),
                response("lines", "REDUCING_BALANCE"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Loan tableLoan = gateway.getLoan("L001");
        Loan reducingBalance = gateway.getLoan("L001");

        assertEquals(Loan.Type.TABLE_LOAN, tableLoan.getType());
        assertEquals(Loan.Type.REDUCING_BALANCE, reducingBalance.getType());
    }

    // The status update's reply can leave out even the loan number, so the loan
    // is read back using the number that was sent. This is the case that failed
    // with "Loan number is required".
    @Test
    void statusUpdateReadsTheLoanBackUsingTheNumberItSent() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                response(),
                loanReply("Pending", mainframe.client.messages.loan.Create.RATETYPE_FIXED),
                noLoanTypeNote());
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);

        Loan loan = gateway.updateLoanStatus("L001", Loan.Status.PENDING);

        assertEquals("L001", loan.getLoanId());
        assertEquals(Loan.Status.PENDING, loan.getStatus());
        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(3)).send(requests.capture());
        MessageRequest retrieve = requests.getAllValues().get(1);
        assertEquals("loan.retrieve", retrieve.getRequestType());
        assertEquals("L001", retrieve.getValue("number"));
    }

    // The reply to a loan update can leave out the customer, so the loan is read
    // back using the number that was sent, and the type and term are carried over
    // from the loan that was sent
    @Test
    void updateReadsTheLoanBackAndCarriesOverTheTypeAndTerm() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                response("loanNumber", "L001", "rateAmount", "12.0", "paymentAmount", "1066.19",
                        "principal", "12000.0"),
                loanReply("New", mainframe.client.messages.loan.Create.RATETYPE_FIXED),
                loanTypeNote(41),
                response("id", "41"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);
        Loan sent = new Loan("L001", "C001", Loan.Status.NEW, 12000, 12.0,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1),
                1066.19, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 12);

        Loan result = gateway.updateLoan(sent);

        assertEquals("C001", result.getCustomerId());
        assertEquals(Loan.Type.TABLE_LOAN, result.getType());
        assertEquals(Integer.valueOf(12), result.getTermMonths());
        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(4)).send(requests.capture());
        assertEquals("loan.update", requests.getAllValues().get(0).getRequestType());
        MessageRequest retrieve = requests.getAllValues().get(1);
        assertEquals("loan.retrieve", retrieve.getRequestType());
        assertEquals("L001", retrieve.getValue("number"));
        MessageRequest metadata = requests.getAllValues().get(3);
        assertEquals("loan.updateNote", metadata.getRequestType());
        assertEquals("TABLE_LOAN", metadata.getValue("lines"));
    }

    // The mainframe only builds the repayment schedule on an update, so creating a
    // loan resends its terms as an update to the new loan number
    @Test
    void createSendsTheSameTermsAsAnUpdateSoTheScheduleIsBuilt() {
        Connection connection = mock(Connection.class);
        when(connection.send(any(MessageRequest.class))).thenReturn(
                response("customerNumber", "C001", "loanNumber", "L002", "status", "New",
                        "rateType", mainframe.client.messages.loan.Create.RATETYPE_FIXED,
                        "rateAmount", "12.0", "rateFrequency", "Monthly", "paymentAmount", "1066.19",
                        "paymentFrequency", "Monthly", "principal", "12000.0",
                        "startDate", "2026-01-01", "renewalDate", "2028-01-01"),
                response("loanNumber", "L002", "rateAmount", "12.0", "paymentAmount", "1066.19",
                        "principal", "12000.0"),
                response("id", "42"));
        MainframeGateway gateway = new MainframeGatewayImpl(connection, 13);
        Loan sent = new Loan(null, "C001", Loan.Status.NEW, 12000, 12.0,
                Loan.Type.TABLE_LOAN, false, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1),
                1066.19, Loan.PaymentFrequency.MONTHLY, Loan.RateFrequency.MONTHLY, 24);

        Loan created = gateway.createLoan(sent);

        assertEquals("L002", created.getLoanId());
        assertEquals(Integer.valueOf(24), created.getTermMonths());
        ArgumentCaptor<MessageRequest> requests = ArgumentCaptor.forClass(MessageRequest.class);
        verify(connection, times(3)).send(requests.capture());
        assertEquals("loan.create", requests.getAllValues().get(0).getRequestType());
        MessageRequest update = requests.getAllValues().get(1);
        assertEquals("loan.update", update.getRequestType());
        assertEquals("L002", update.getValue("loanNumber"));
        assertEquals("C001", update.getValue("customerNumber"));
        MessageRequest create = requests.getAllValues().get(0);
        for (String field : new String[] {"principal", "rateAmount", "paymentAmount",
                "startDate", "renewalDate", "rateType", "paymentFrequency", "rateFrequency"}) {
            assertEquals(create.getValue(field), update.getValue(field), field);
        }
        MessageRequest metadata = requests.getAllValues().get(2);
        assertEquals("loan.addNote", metadata.getRequestType());
        assertEquals("LAVS Loan Type", metadata.getValue("type"));
        assertEquals("TABLE_LOAN", metadata.getValue("lines"));
    }

    private static MessageResponse noLoanTypeNote() {
        return response("items", "0", "total", "0");
    }

    private static MessageResponse loanTypeNote(long id) {
        return response("items", "1", "total", "1", "1:id", Long.toString(id),
                "1:type", "LAVS Loan Type");
    }

    private static MessageResponse response(String... entries) {
        HashMap<String, String> values = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put(entries[index], entries[index + 1]);
        }
        return new MessageResponse(new Status(1), values);
    }
}
