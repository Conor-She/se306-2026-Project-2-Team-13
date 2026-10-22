package com.lavs.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lavs.business.CustomerRecordValidator.FieldSpec;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomerRecordValidatorTest {
    private final CustomerRecordValidator validator = new CustomerRecordValidator();

    private static FieldSpec field(String key, String... options) {
        return new FieldSpec(key, List.of(options));
    }

    private Map<String, String> check(List<FieldSpec> fields, Map<String, String> values) {
        return validator.validate(fields, values);
    }

    @Test void everyFieldOnTheFormIsRequired() {
        Map<String, String> errors = check(List.of(field("firstName"), field("lastName")),
                Map.of("firstName", "Sarah", "lastName", "  "));
        assertEquals(Map.of("lastName", CustomerRecordValidator.REQUIRED_MESSAGE), errors);
    }

    @Test void emailsAndPhonesMustHaveAValidShape() {
        List<FieldSpec> fields = List.of(field("email"), field("phone"));
        assertEquals(Map.of("email", CustomerRecordValidator.EMAIL_MESSAGE,
                "phone", CustomerRecordValidator.PHONE_MESSAGE),
                check(fields, Map.of("email", "not-an-email", "phone", "abc")));
        assertTrue(check(fields, Map.of("email", "sarah@example.com", "phone", "+64 21 135 6767")).isEmpty());
    }

    @Test void amountsMustBeNonNegativeWithAtMostTwoDecimals() {
        List<FieldSpec> fields = List.of(field("income"));
        for (String bad : new String[] {"-1", "10.123", "1e3"}) {
            assertEquals(Map.of("income", CustomerRecordValidator.AMOUNT_MESSAGE),
                    check(fields, Map.of("income", bad)), bad);
        }
        for (String notANumber : new String[] {"abc", "1,000"}) {
            assertEquals(Map.of("income", CustomerRecordValidator.NUMBER_MESSAGE),
                    check(fields, Map.of("income", notANumber)), notANumber);
        }
        assertTrue(check(fields, Map.of("income", "85000.50")).isEmpty());
        assertTrue(check(fields, Map.of("income", "0")).isEmpty());
    }

    @Test void theRateMustBeBetweenZeroAndOneHundred() {
        List<FieldSpec> fields = List.of(field("rate"));
        assertEquals(Map.of("rate", CustomerRecordValidator.RATE_MESSAGE),
                check(fields, Map.of("rate", "100.01")));
        assertEquals(Map.of("rate", CustomerRecordValidator.RATE_MESSAGE),
                check(fields, Map.of("rate", "-1")));
        assertTrue(check(fields, Map.of("rate", "100")).isEmpty());
        assertTrue(check(fields, Map.of("rate", "0")).isEmpty());
    }

    @Test void theBirthDateMustBeARealPastDateInTheExpectedFormat() {
        List<FieldSpec> fields = List.of(field("birthDate"));
        assertEquals(Map.of("birthDate", CustomerRecordValidator.BIRTH_DATE_FORMAT_MESSAGE),
                check(fields, Map.of("birthDate", "1990-01-01")));
        assertEquals(Map.of("birthDate", CustomerRecordValidator.BIRTH_DATE_FORMAT_MESSAGE),
                check(fields, Map.of("birthDate", "31-02-1990")));
        String tomorrow = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("dd-MM-uuuu"));
        assertEquals(Map.of("birthDate", CustomerRecordValidator.BIRTH_DATE_PAST_MESSAGE),
                check(fields, Map.of("birthDate", tomorrow)));
        assertTrue(check(fields, Map.of("birthDate", "15-06-1990")).isEmpty());
    }

    @Test void theUploadDateMustBeAnIsoDate() {
        List<FieldSpec> fields = List.of(field("date"));
        assertEquals(Map.of("date", CustomerRecordValidator.UPLOAD_DATE_MESSAGE),
                check(fields, Map.of("date", "14/10/2026")));
        assertTrue(check(fields, Map.of("date", "2026-10-14")).isEmpty());
    }

    @Test void aDropDownValueMustBeOneOfTheOptions() {
        List<FieldSpec> fields = List.of(field("employmentType", "Full-time", "Part-time"));
        assertEquals(Map.of("employmentType", CustomerRecordValidator.PICK_FROM_LIST_MESSAGE),
                check(fields, Map.of("employmentType", "Hobby")));
        assertTrue(check(fields, Map.of("employmentType", "Part-time")).isEmpty());
    }

    @Test void aFreeTextFieldWithNoOptionsAcceptsAnyValue() {
        assertTrue(check(List.of(field("employer")), Map.of("employer", "Anything Ltd")).isEmpty());
    }

    @Test void contactDetailsAreCheckedAccordingToTheContactType() {
        List<FieldSpec> fields = List.of(field("contactType", "Email", "Phone", "Address"), field("value"));
        assertEquals(Map.of("value", CustomerRecordValidator.EMAIL_MESSAGE),
                check(fields, Map.of("contactType", "Email", "value", "nope")));
        assertEquals(Map.of("value", CustomerRecordValidator.PHONE_MESSAGE),
                check(fields, Map.of("contactType", "Phone", "value", "abc")));
        assertTrue(check(fields, Map.of("contactType", "Email", "value", "a@b.co")).isEmpty());
    }

    @Test void anAddressNeedsAStreetCityAndCountryInsteadOfContactDetails() {
        List<FieldSpec> fields = List.of(field("contactType", "Email", "Phone", "Address"), field("value"));
        Map<String, String> errors = check(fields, Map.of("contactType", "Address", "value", "", "city", "Auckland"));
        assertEquals(Map.of("street", CustomerRecordValidator.REQUIRED_MESSAGE,
                "country", CustomerRecordValidator.REQUIRED_MESSAGE), errors);
        assertTrue(check(fields, Map.of("contactType", "Address", "value", "",
                "street", "1 Queen St", "city", "Auckland", "country", "NZ")).isEmpty());
    }
}
