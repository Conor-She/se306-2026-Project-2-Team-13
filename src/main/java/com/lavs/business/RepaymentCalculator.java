package com.lavs.business;

import java.util.List;

import com.lavs.model.Loan;
import com.lavs.model.Repayment;

public interface RepaymentCalculator {
    List<Repayment> calculateSchedule(Loan loan);
}
