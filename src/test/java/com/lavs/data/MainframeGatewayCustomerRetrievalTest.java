package com.lavs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import mainframe.client.Connection;
import mainframe.client.MessageRequest;
import mainframe.client.MessageResponse;
import mainframe.client.Status;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MainframeGatewayCustomerRetrievalTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "1990-05-01"})
    void retrievesPersonalDetailsIncludingOptionalDateOfBirth(String birthDate) {
        Connection connection = mock(Connection.class);
        HashMap<String, String> values = new HashMap<>();
        values.put("number", "C1");
        values.put("title", "Ms");
        values.put("firstName", "Jane");
        values.put("familyName", "Smith");
        values.put("nationality", "NZ");
        values.put("dateOfBirth", birthDate);
        when(connection.send(any(MessageRequest.class)))
                .thenReturn(new MessageResponse(new Status(1), values));

        var customer = new MainframeGatewayImpl(connection, "Test").getCustomer("C1");

        assertEquals("C1", customer.getCustomerId());
        assertEquals("Ms", customer.getTitle());
        assertEquals("Jane", customer.getFirstName());
        assertEquals("Smith", customer.getLastName());
        assertEquals("NZ", customer.getNationality());
        if (birthDate == null || birthDate.isBlank()) {
            assertNull(customer.getDateOfBirth());
        } else {
            assertEquals(LocalDate.of(1990, 5, 1), customer.getDateOfBirth());
        }
    }
}
