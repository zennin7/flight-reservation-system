package com.group2.flightres.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Derives two-character avatar initials from a person's name.
 */
public final class NameInitials {

    private NameInitials() {}

    public static String of(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "AD";
        }
        String[] parts = fullName.trim().split("\\s+");
        List<String> words = new ArrayList<>();
        for (String p : parts) {
            String clean = p.replace(".", "");
            if (!clean.isEmpty()
                    && !clean.equalsIgnoreCase("Mr")
                    && !clean.equalsIgnoreCase("Ms")
                    && !clean.equalsIgnoreCase("Mrs")
                    && !clean.equalsIgnoreCase("Dr")) {
                words.add(clean);
            }
        }
        if (words.isEmpty()) {
            return "AD";
        }
        if (words.size() == 1) {
            String w = words.get(0);
            return w.substring(0, Math.min(2, w.length())).toUpperCase(Locale.ROOT);
        }
        char first = Character.toUpperCase(words.get(0).charAt(0));
        if (words.size() >= 3) {
            String third = words.get(2).toUpperCase(Locale.ROOT);
            switch (third) {
                case "II": return first + "2";
                case "III": return first + "3";
                case "IV": return first + "4";
                case "V": return first + "5";
                default: break;
            }
        }
        char second = Character.toUpperCase(words.get(1).charAt(0));
        return "" + first + second;
    }
}
