package com.openusage.app.policy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AhoCorasickMatcher - multi-pattern string search over character input.
 *
 * <p>Compiled ONCE per policy-config load (trie + BFS failure links). Matching is a
 * single O(n) pass over the input with one state transition per character and zero
 * allocation on the hot path aside from the returned match list. This is the matching
 * engine for the (remotely configurable) sensitive-keyword dictionary used by
 * {@link SensitiveContentPolicy}.
 *
 * <p>All patterns are lowercased at build time; callers must pass lowercased input to
 * {@link #findMatches(String)} for case-insensitive matching (see
 * {@code SensitiveContentPolicy} which normalizes once and shares the result).
 *
 * <p>This class is pure Java (no Android dependencies) so it is unit-testable on a
 * plain JVM.
 */
public final class AhoCorasickMatcher {

    /** A single keyword hit: the category it belongs to and its span in the input. */
    public static final class Match {
        public final String category;
        public final String keyword;
        public final int start; // inclusive
        public final int end;   // exclusive

        Match(String category, String keyword, int start, int end) {
            this.category = category;
            this.keyword = keyword;
            this.start = start;
            this.end = end;
        }
    }

    // Trie node stored in parallel arrays / maps indexed by node id.
    private final List<Map<Character, Integer>> goTo = new ArrayList<>();
    private final List<Integer> fail = new ArrayList<>();
    // Output: for a node, the list of (category, keyword) pairs ending here (incl. via suffix links).
    private final List<List<String[]>> output = new ArrayList<>();

    private boolean built = false;

    public AhoCorasickMatcher() {
        newNode(); // root = node 0
    }

    private int newNode() {
        goTo.add(new HashMap<>());
        fail.add(0);
        output.add(new ArrayList<>());
        return goTo.size() - 1;
    }

    /**
     * Adds a keyword under a category. Must be called before {@link #build()}.
     * Blank keywords are ignored. Keywords are lowercased.
     */
    public void addKeyword(String category, String keyword) {
        if (keyword == null) return;
        String kw = keyword.toLowerCase().trim();
        if (kw.isEmpty()) return;

        int node = 0;
        for (int i = 0; i < kw.length(); i++) {
            char c = kw.charAt(i);
            Integer next = goTo.get(node).get(c);
            if (next == null) {
                next = newNode();
                goTo.get(node).put(c, next);
            }
            node = next;
        }
        output.get(node).add(new String[]{category, kw});
    }

    /**
     * Constructs BFS failure links. Call once after all keywords are added.
     */
    public void build() {
        Deque<Integer> queue = new ArrayDeque<>();
        // Depth-1 nodes fail to root.
        for (Integer child : goTo.get(0).values()) {
            fail.set(child, 0);
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (Map.Entry<Character, Integer> entry : goTo.get(current).entrySet()) {
                char c = entry.getKey();
                int child = entry.getValue();
                queue.add(child);

                int f = fail.get(current);
                while (f != 0 && goTo.get(f).get(c) == null) {
                    f = fail.get(f);
                }
                Integer target = goTo.get(f).get(c);
                int failState = (target != null && target != child) ? target : 0;
                fail.set(child, failState);

                // Merge suffix outputs so a single node reports all keywords ending here.
                output.get(child).addAll(output.get(failState));
            }
        }
        built = true;
    }

    /**
     * Finds all keyword matches in {@code text}. Input should be lowercased by the caller
     * for case-insensitive behavior. Returns an empty list if nothing matches or if the
     * automaton has no keywords.
     */
    public List<Match> findMatches(String text) {
        List<Match> matches = new ArrayList<>();
        if (!built || text == null || text.isEmpty()) return matches;

        int node = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (node != 0 && goTo.get(node).get(c) == null) {
                node = fail.get(node);
            }
            Integer next = goTo.get(node).get(c);
            node = (next != null) ? next : 0;

            List<String[]> hits = output.get(node);
            if (!hits.isEmpty()) {
                for (String[] hit : hits) {
                    String category = hit[0];
                    String keyword = hit[1];
                    int start = i - keyword.length() + 1;
                    matches.add(new Match(category, keyword, start, i + 1));
                }
            }
        }
        return matches;
    }

    /** Whether the automaton contains at least one keyword. */
    public boolean isEmpty() {
        return goTo.size() <= 1;
    }
}
