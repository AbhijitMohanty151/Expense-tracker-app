package com.example.budget;

/**
 * Represents a single, stackable filter that can be applied to transaction queries.
 *
 * Types:
 *   DATE_RANGE   – keep rows whose ISO date is between fromIso and toIso (inclusive)
 *   DESCRIPTION  – keep/exclude rows whose description contains keyword (case-insensitive)
 *   TYPE         – keep only "credit" or "debit" rows
 *   AMOUNT_RANGE – keep rows where amount is between minAmount and maxAmount
 */
public class TransactionFilter {

    public enum FilterType {
        DATE_RANGE,
        DESCRIPTION,
        TXN_TYPE,
        AMOUNT_RANGE
    }

    // ---- common ----
    public final FilterType type;

    // ---- DATE_RANGE ----
    public String fromIso;   // "yyyy-MM-dd"
    public String toIso;     // "yyyy-MM-dd"

    // ---- DESCRIPTION ----
    public String keyword;
    public boolean excludeKeyword; // true → exclude rows that match, false → include only matches

    // ---- TXN_TYPE ----
    public String txnType; // "credit" | "debit"

    // ---- AMOUNT_RANGE ----
    public double minAmount;
    public double maxAmount;

    /* ---- Factories ---- */

    public static TransactionFilter dateRange(String fromIso, String toIso) {
        TransactionFilter f = new TransactionFilter(FilterType.DATE_RANGE);
        f.fromIso = fromIso;
        f.toIso   = toIso;
        return f;
    }

    public static TransactionFilter description(String keyword, boolean exclude) {
        TransactionFilter f = new TransactionFilter(FilterType.DESCRIPTION);
        f.keyword        = keyword;
        f.excludeKeyword = exclude;
        return f;
    }

    public static TransactionFilter txnType(String type) {
        TransactionFilter f = new TransactionFilter(FilterType.TXN_TYPE);
        f.txnType = type;
        return f;
    }

    public static TransactionFilter amountRange(double min, double max) {
        TransactionFilter f = new TransactionFilter(FilterType.AMOUNT_RANGE);
        f.minAmount = min;
        f.maxAmount = max;
        return f;
    }

    private TransactionFilter(FilterType type) { this.type = type; }

    /** Human-readable chip label shown in the UI. */
    public String label() {
        switch (type) {
            case DATE_RANGE:
                return fromIso + " → " + toIso;
            case DESCRIPTION:
                return (excludeKeyword ? "Excl: \"" : "Incl: \"") + keyword + "\"";
            case TXN_TYPE:
                return "credit".equals(txnType) ? "In only" : "Spent only";
            case AMOUNT_RANGE:
                return "₹" + fmt(minAmount) + " – ₹" + fmt(maxAmount);
            default:
                return "Filter";
        }
    }

    private static String fmt(double v) {
        return v == (long) v ? String.valueOf((long) v) : String.format("%.2f", v);
    }
}
