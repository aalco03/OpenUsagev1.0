package com.openusage.app.TextBasedEventData;

import java.util.HashMap;
import java.util.Map;

/**
 * TextSimilarityCalculator - Fast cosine similarity using character n-grams.
 *
 * Used by ScreenomicsAccessService to detect duplicate UI text extractions.
 * When consecutive extractions score above a configurable threshold (e.g. 0.85)
 * the newer extraction is skipped, reducing redundant database writes.
 *
 * Algorithm: cosine similarity on character tri-gram frequency vectors.
 * Time complexity:  O(n) where n = text length
 * Space complexity: O(n) for the n-gram maps
 */
public class TextSimilarityCalculator {

    private static final int NGRAM_SIZE = 3;

    /**
     * Calculate cosine similarity between two texts using character n-grams.
     *
     * @param text1 first text
     * @param text2 second text
     * @return similarity in [0.0, 1.0]; 1.0 = identical
     */
    public static double calculateSimilarity(String text1, String text2) {
        // Null / empty guards
        if (text1 == null || text2 == null) return 0.0;
        if (text1.isEmpty() && text2.isEmpty()) return 1.0;
        if (text1.isEmpty() || text2.isEmpty()) return 0.0;
        if (text1.equals(text2)) return 1.0;

        // Normalise: lowercase + collapse whitespace
        String norm1 = normalise(text1);
        String norm2 = normalise(text2);

        // Build n-gram frequency maps
        Map<String, Integer> ngrams1 = getNGrams(norm1, NGRAM_SIZE);
        Map<String, Integer> ngrams2 = getNGrams(norm2, NGRAM_SIZE);

        if (ngrams1.isEmpty() || ngrams2.isEmpty()) return 0.0;

        // Cosine similarity = dot(v1, v2) / (|v1| * |v2|)
        double dot = dotProduct(ngrams1, ngrams2);
        double mag1 = magnitude(ngrams1);
        double mag2 = magnitude(ngrams2);

        if (mag1 == 0.0 || mag2 == 0.0) return 0.0;

        return dot / (mag1 * mag2);
    }

    // ── helpers ──────────────────────────────────────────────

    private static String normalise(String text) {
        return text.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    /**
     * Extract character n-grams and count their frequencies.
     */
    private static Map<String, Integer> getNGrams(String text, int n) {
        Map<String, Integer> map = new HashMap<>();
        int len = text.length();
        if (len < n) {
            // Text shorter than n-gram size: use the whole string as a single gram
            map.put(text, 1);
            return map;
        }
        for (int i = 0; i <= len - n; i++) {
            String gram = text.substring(i, i + n);
            Integer count = map.get(gram);
            map.put(gram, count == null ? 1 : count + 1);
        }
        return map;
    }

    /**
     * Dot product of two sparse frequency vectors.
     */
    private static double dotProduct(Map<String, Integer> map1, Map<String, Integer> map2) {
        double sum = 0.0;
        // Iterate over the smaller map for efficiency
        Map<String, Integer> smaller = map1.size() <= map2.size() ? map1 : map2;
        Map<String, Integer> larger  = smaller == map1 ? map2 : map1;
        for (Map.Entry<String, Integer> entry : smaller.entrySet()) {
            Integer other = larger.get(entry.getKey());
            if (other != null) {
                sum += (double) entry.getValue() * other;
            }
        }
        return sum;
    }

    /**
     * Euclidean magnitude (L2 norm) of a frequency vector.
     */
    private static double magnitude(Map<String, Integer> map) {
        double sum = 0.0;
        for (int v : map.values()) {
            sum += (double) v * v;
        }
        return Math.sqrt(sum);
    }
}
