package com.lavs.business;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.model.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerServiceRelatedRecordsTest {
    @Test void retrievesOnlyTheSelectedCustomersDocumentsAndGuarantors() {
        var gateway = new FakeMainframeGateway();
        gateway.createCustomer(new Customer("C1", "Ms", "Jane", "Smith", null, "NZ"));
        gateway.createCustomer(new Customer("C2", "Mr", "John", "Smith", null, "NZ"));
        var document = gateway.addDocument(new CustomerDocument("D1", "C1", "passport.pdf", "Identity", LocalDate.now()));
        var guarantor = gateway.addGuarantor(new Guarantor("G1", "C1", "C3", "Parent"));
        gateway.addDocument(new CustomerDocument("D2", "C2", "other.pdf", "Address", LocalDate.now()));
        gateway.addGuarantor(new Guarantor("G2", "C2", "C4", "Sibling"));
        var details = new CustomerService(gateway).getById(new CustomerId("C1"));
        assertEquals(List.of(document), details.documents());
        assertEquals(List.of(guarantor), details.guarantors());
        assertThrows(UnsupportedOperationException.class, () -> details.documents().clear());
        assertThrows(UnsupportedOperationException.class, () -> details.guarantors().clear());
    }

    @Test void customerWithoutSupportingRecordsHasEmptyLists() {
        var gateway = new FakeMainframeGateway();
        gateway.createCustomer(new Customer("C1", "Ms", "Jane", "Smith", null, "NZ"));
        var details = new CustomerService(gateway).getById(new CustomerId("C1"));
        assertTrue(details.documents().isEmpty());
        assertTrue(details.guarantors().isEmpty());
    }
}
