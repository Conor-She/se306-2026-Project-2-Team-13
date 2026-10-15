package com.lavs.business;

// Whether the money paid towards a scheduled repayment meets what was due
public enum RepaymentStatus {

    // At least the amount due was paid
    ON_TIME,

    // Something was paid, but less than the amount due
    PARTIAL,

    // Nothing was paid, and the due date has passed
    MISSED,

    // Nothing was paid yet and the due date hasn't passed
    UPCOMING
}