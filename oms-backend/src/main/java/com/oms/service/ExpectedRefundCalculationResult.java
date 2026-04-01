package com.oms.service;

import java.time.LocalDate;

public record ExpectedRefundCalculationResult(
        LocalDate expectedRefundDate,
        String ruleDescription,
        String pendingReason,
        boolean matchedRule
) {
    public static ExpectedRefundCalculationResult unmatched() {
        return new ExpectedRefundCalculationResult(null, null, "未匹配甲方回款规则", false);
    }

    public static ExpectedRefundCalculationResult pending(String ruleDescription, String pendingReason) {
        return new ExpectedRefundCalculationResult(null, ruleDescription, pendingReason, true);
    }

    public static ExpectedRefundCalculationResult resolved(LocalDate expectedRefundDate, String ruleDescription) {
        return new ExpectedRefundCalculationResult(expectedRefundDate, ruleDescription, null, true);
    }
}
