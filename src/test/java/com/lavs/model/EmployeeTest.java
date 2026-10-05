package com.lavs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class EmployeeTest {

    // Confirms a fully valid Employee builds and its fields are retrievable
    @Test
    void constructsWithValidFields() {
        Employee employee = new Employee("E001", "jsmith", "hashedpw123", "Jane Smith");

        assertEquals("E001", employee.getEmployeeId());
        assertEquals("jsmith", employee.getUserName());
    }

    // An empty username should be rejected
    @Test
    void rejectsBlankUserName() {
        assertThrows(IllegalArgumentException.class, () ->
                new Employee("E001", "", "hashedpw123", "Jane Smith"));
    }

    // Employee must have zero fields referencing other domain classes
    @Test
    void hasNoReferencesToOtherDomainClasses() {
        Field[] fields = Employee.class.getDeclaredFields();

        for (Field field : fields) {
            Class<?> fieldType = field.getType();
            assertTrue(fieldType == String.class,
                    "Employee field '" + field.getName() + "' is of type "
                            + fieldType.getSimpleName()
                            + ", but Employee must only contain String fields"
                            + " (no references to other domain classes)");
        }
    }
}