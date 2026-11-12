package com.group2.flightres.util;

import com.group2.flightres.config.BaggagePolicy;

import java.util.List;

/**
 * Company fare and ticket policies (baggage allowances, cancellation and refund terms).
 * Reusable across desktop, web, and reporting.
 */
public final class FarePolicy {

    public record PolicyRule(String label, String value) {}

    public static final List<PolicyRule> BAGGAGE_RULES = List.of(
            new PolicyRule("Cabin Baggage", "1 carry-on bag (up to 7 kg) + 1 personal item included"),
            new PolicyRule("Checked Baggage", "1 checked bag (up to " + BaggagePolicy.CHECKED_BAGGAGE_KG + " kg) included free of charge"),
            new PolicyRule("Oversized Bags", "Available for purchase at check-in counter"),
            new PolicyRule("Sports Equipment", "Not provided")
    );

    public static final List<PolicyRule> CANCELLATION_RULES = List.of(
            new PolicyRule("Free Cancellation", "Within 24 hours of booking for full refund"),
            new PolicyRule("Flight Changes", "Permitted up to 2 hours before departure (fare difference applies)"),
            new PolicyRule("No-Show Policy", "Ticket non-refundable if not cancelled prior to departure"),
            new PolicyRule("Refund Method", "Original payment method within 5-7 business days")
    );

    private FarePolicy() {}

    public static List<PolicyRule> getBaggageRules() {
        return BAGGAGE_RULES;
    }

    public static List<PolicyRule> getCancellationRules() {
        return CANCELLATION_RULES;
    }
}
