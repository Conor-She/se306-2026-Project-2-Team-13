package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lavs.data.FakeMainframeGateway;
import com.lavs.data.MainframeException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CategoryServiceTest {
    private FakeMainframeGateway gateway;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        gateway = new FakeMainframeGateway();
        service = new CategoryService(gateway);
    }

    @Test
    void returnsCategoryValuesFromGateway() {
        gateway.seedCategoryValues(
                "EmploymentType", List.of("Full-time", "Part-time", "Self-employed"));

        assertEquals(
                List.of("Full-time", "Part-time", "Self-employed"),
                service.get("EmploymentType"));
    }

    @Test
    void requestsFreshValuesEachTime() {
        gateway.seedCategoryValues("LoanType", List.of("Table loan"));
        assertEquals(List.of("Table loan"), service.get("LoanType"));

        gateway.seedCategoryValues(
                "LoanType", List.of("Table loan", "Reducing balance"));

        assertEquals(
                List.of("Table loan", "Reducing balance"),
                service.get("LoanType"));
    }

    @Test
    void returnsEmptyListForUnknownCategory() {
        assertTrue(service.get("Unknown").isEmpty());
    }

    @Test
    void validatesDependenciesAndCategoryType() {
        assertThrows(IllegalArgumentException.class, () -> new CategoryService(null));
        assertThrows(IllegalArgumentException.class, () -> service.get(null));
        assertThrows(IllegalArgumentException.class, () -> service.get("  "));
    }

    @Test
    void passesMainframeUnavailableThrough() {
        gateway.simulateUnavailable(true);

        MainframeException exception = assertThrows(
                MainframeException.class,
                () -> service.get("EmploymentType"));

        assertEquals(MainframeException.Type.UNAVAILABLE, exception.getType());
    }
}
