package com.openusage.app.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Component tests for {@link AhoCorasickMatcher}: overlapping matches, case-insensitivity
 * (caller-normalized), unicode safety, and empty behavior.
 */
public class AhoCorasickMatcherTest {

    private AhoCorasickMatcher build(String... kws) {
        AhoCorasickMatcher m = new AhoCorasickMatcher();
        for (String kw : kws) m.addKeyword("financial", kw);
        m.build();
        return m;
    }

    @Test
    public void findsSingleKeyword() {
        AhoCorasickMatcher m = build("balance");
        List<AhoCorasickMatcher.Match> matches = m.findMatches("account balance available");
        assertEquals(1, matches.size());
        assertEquals("balance", matches.get(0).keyword);
    }

    @Test
    public void findsOverlappingKeywords() {
        // "he", "she", "his", "hers" classic Aho-Corasick overlap case.
        AhoCorasickMatcher m = new AhoCorasickMatcher();
        m.addKeyword("c", "he");
        m.addKeyword("c", "she");
        m.addKeyword("c", "his");
        m.addKeyword("c", "hers");
        m.build();
        List<AhoCorasickMatcher.Match> matches = m.findMatches("ushers");
        // "she", "he", "hers" all end within "ushers".
        assertTrue(containsKeyword(matches, "she"));
        assertTrue(containsKeyword(matches, "he"));
        assertTrue(containsKeyword(matches, "hers"));
    }

    @Test
    public void caseInsensitiveWhenCallerLowercases() {
        AhoCorasickMatcher m = build("routing number");
        List<AhoCorasickMatcher.Match> matches = m.findMatches("your ROUTING NUMBER is".toLowerCase());
        assertEquals(1, matches.size());
    }

    @Test
    public void unicodeDoesNotCrash() {
        AhoCorasickMatcher m = build("balance");
        List<AhoCorasickMatcher.Match> matches = m.findMatches("account bàlance 余额 balance");
        assertEquals(1, matches.size()); // only the ASCII "balance" matches
    }

    @Test
    public void emptyMatcherReturnsNothing() {
        AhoCorasickMatcher m = new AhoCorasickMatcher();
        m.build();
        assertTrue(m.isEmpty());
        assertTrue(m.findMatches("anything").isEmpty());
    }

    @Test
    public void spanIndicesAreCorrect() {
        AhoCorasickMatcher m = build("card");
        List<AhoCorasickMatcher.Match> matches = m.findMatches("my card here");
        assertEquals(1, matches.size());
        assertEquals(3, matches.get(0).start);
        assertEquals(7, matches.get(0).end);
    }

    private static boolean containsKeyword(List<AhoCorasickMatcher.Match> matches, String kw) {
        for (AhoCorasickMatcher.Match m : matches) if (m.keyword.equals(kw)) return true;
        return false;
    }
}
