package com.lavs.business;

import com.lavs.model.Address;
import com.lavs.model.Email;
import com.lavs.model.Phone;
import java.util.List;

public class LoanPrerequisiteChecker {

    public static final String MAILING_ADDRESS_MESSAGE =
            "Customer must have at least one mailing address before a loan can proceed";
    public static final String CONTACT_MESSAGE =
            "Customer must have at least one phone number or email before a loan can proceed";

    // At least one address must be marked IsMailing.
    // A missing (null) list counts as no addresses.
    public void checkHasMailingAddress(List<Address> addresses) {
        boolean hasMailingAddress = addresses != null
                && addresses.stream().anyMatch(Address::isMailing);

        if (!hasMailingAddress) {
            throw new LoanPrerequisiteException(MAILING_ADDRESS_MESSAGE);
        }
    }

    // Alternate contact check: at least one phone number or email
    // is required. Missing (null) lists count as empty.
    public void checkHasAlternateContact(List<Phone> phones, List<Email> emails) {
        boolean hasPhone = phones != null && !phones.isEmpty();
        boolean hasEmail = emails != null && !emails.isEmpty();

        if (!hasPhone && !hasEmail) {
            throw new LoanPrerequisiteException(CONTACT_MESSAGE);
        }
    }

    // Runs both checks (mailing address first), stopping at the first failure
    public void checkAll(List<Address> addresses, List<Phone> phones, List<Email> emails) {
        checkHasMailingAddress(addresses);
        checkHasAlternateContact(phones, emails);
    }
}