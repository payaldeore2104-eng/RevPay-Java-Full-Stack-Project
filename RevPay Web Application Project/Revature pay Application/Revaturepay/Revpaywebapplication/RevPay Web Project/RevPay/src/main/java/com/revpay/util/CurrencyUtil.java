package com.revpay.util;

import java.text.NumberFormat;
import java.util.Locale;

public class CurrencyUtil {
    private static final Locale INDIA = new Locale("en", "IN");

    public static String format(double amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(INDIA);
        return formatter.format(amount);
    }

    public static String format(java.math.BigDecimal amount) {
        if (amount == null)
            return format(0.0);
        return format(amount.doubleValue());
    }
}
