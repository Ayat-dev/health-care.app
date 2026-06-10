package com.clinic.backend.lab;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic comparison of an entered result against a free-text reference range.
 * Lab reference ranges in the catalogue come in a few shapes:
 *   - numeric interval, e.g. "0.70 - 1.10 g/L"  → abnormal if the value falls outside [low, high]
 *   - a qualitative expected value, e.g. "Négatif" → abnormal if the value reads positive
 *   - anything else / blank → not flagged automatically (the laborantin can still tick "anormal")
 *
 * This is a best-effort default only; it never throws and falls back to "normal" when unsure.
 */
@Component
public class ResultAbnormalityChecker {

    // Matches "0.70 - 1.10", "13-17", "6 – 12 mg/L" (any trailing unit text ignored).
    private static final Pattern RANGE = Pattern.compile(
            "^\\s*(-?\\d+(?:[.,]\\d+)?)\\s*[-–]\\s*(-?\\d+(?:[.,]\\d+)?)");

    // Matches a leading number in the entered value, e.g. "12.5 g/dL" → 12.5
    private static final Pattern LEADING_NUMBER = Pattern.compile(
            "^\\s*(-?\\d+(?:[.,]\\d+)?)");

    public boolean isAbnormal(String value, String referenceRange) {
        if (value == null || value.isBlank() || referenceRange == null || referenceRange.isBlank()) {
            return false;
        }
        String range = referenceRange.trim();

        // ── Qualitative "Négatif" expected ──────────────────────────────────────
        String normRange = stripAccents(range).toLowerCase();
        if (normRange.startsWith("negatif") || normRange.startsWith("negative")) {
            String normValue = stripAccents(value).toLowerCase();
            return normValue.contains("positif") || normValue.contains("positive")
                    || normValue.trim().equals("+") || normValue.contains("anormal");
        }

        // ── Numeric interval ────────────────────────────────────────────────────
        Matcher rm = RANGE.matcher(range);
        Matcher vm = LEADING_NUMBER.matcher(value);
        if (rm.find() && vm.find()) {
            try {
                double low = parse(rm.group(1));
                double high = parse(rm.group(2));
                double v = parse(vm.group(1));
                return v < low || v > high;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private double parse(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }

    private String stripAccents(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
