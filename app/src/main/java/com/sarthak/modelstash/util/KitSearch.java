package com.sarthak.modelstash.util;

import com.sarthak.modelstash.data.ModelKit;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The Catalogue's search: "do I own it?" should find the kit however it's typed.
 *
 * - Spaces, hyphens, dots and other punctuation don't matter:
 *   "F14" finds "F-14D", "bf109" finds "Bf 109", "mk ix" finds "Mk.IX".
 * - Accents and case don't matter: "fw190" finds "Focke-Wulf Fw 190", "skoda" finds "Škoda".
 * - Several words can be in any order, and each may be in the name, brand or kit
 *   number: "tamiya tomcat", "tomcat 61114".
 * - A scale written as one ("1/48", "1:72") must match the kit's scale: "tomcat 1/48".
 * - Typos: only when nothing matches as typed, words of 5 letters or more may have
 *   one letter wrong, missing, extra or swapped: "hasegwa" finds "Hasegawa".
 *   Those are "close matches", so the Catalogue can say so instead of "You own it".
 *
 * Build an {@link Index} once per list of kits; searching it is then cheap enough to
 * run on every keystroke. Plain Java (no Android classes), so it can be unit-tested.
 */
public final class KitSearch {

    /** Words shorter than this must be typed right (short words have too many neighbours). */
    public static final int TYPO_MIN_LENGTH = 5;

    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern QUERY_WORDS = Pattern.compile("[\\s,;+]+");
    /** "1/48", "1:72", "1:12.5" (not a bare "48": that could be part of a name or number). */
    private static final Pattern SCALE_WORD = Pattern.compile("1[:/]\\d{1,4}(?:[.,]\\d{1,2})?");

    private KitSearch() {
    }

    /** What a search found. */
    public static final class Result {
        public final List<ModelKit> kits;
        /** true = nothing matched as typed, these are spelling-tolerant matches. */
        public final boolean close;

        Result(List<ModelKit> kits, boolean close) {
            this.kits = kits;
            this.close = close;
        }
    }

    /** A list of kits, prepared for searching. */
    public static final class Index {
        public static final Index EMPTY = new Index(Collections.<ModelKit>emptyList());

        private final List<ModelKit> kits;
        private final Entry[] entries;

        public Index(List<ModelKit> kits) {
            this.kits = kits;
            entries = new Entry[kits.size()];
            for (int i = 0; i < entries.length; i++) {
                entries[i] = new Entry(kits.get(i));
            }
        }

        /** Kits matching `query`, in the list's order. A blank query returns every kit. */
        public Result search(String query) {
            Query q = new Query(query);
            if (q.isBlank()) {
                return new Result(kits, false);
            }
            List<ModelKit> found = collect(q, false);
            if (!found.isEmpty() || !q.allowsTypos()) {
                return new Result(found, false);
            }
            found = collect(q, true);
            return new Result(found, !found.isEmpty());
        }

        private List<ModelKit> collect(Query q, boolean tolerant) {
            List<ModelKit> out = new ArrayList<>();
            for (int i = 0; i < entries.length; i++) {
                if (entries[i].matches(q, tolerant)) {
                    out.add(kits.get(i));
                }
            }
            return out;
        }
    }

    // ---------------- Text folding ----------------

    /** Lower case without accents: "Škoda Ł" → "skoda l", "Straße" → "strasse". */
    public static String fold(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String s = text.toLowerCase(Locale.ROOT);
        // Letters that aren't "letter + accent" in Unicode, so the step below misses them.
        s = s.replace("ß", "ss").replace("æ", "ae").replace("œ", "oe").replace("ø", "o")
                .replace("ł", "l").replace("đ", "d").replace("ð", "d").replace("þ", "th")
                .replace("ı", "i");
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        return MARKS.matcher(s).replaceAll("");
    }

    /** Letters and digits only, folded: "F-14D Tomcat" → "f14dtomcat". */
    public static String compact(String text) {
        return NOT_LETTER_OR_DIGIT.matcher(fold(text)).replaceAll("");
    }

    /** Same name ignoring case, accents, spaces and punctuation. */
    public static boolean sameName(String a, String b) {
        String ka = compact(a);
        return !ka.isEmpty() && ka.equals(compact(b));
    }

    // ---------------- Matching ----------------

    /** One kit, folded once. */
    private static final class Entry {
        final String name;
        final String brand;
        final String number;
        final String scale;
        /** Separate words of the name and brand, for typo matching. */
        final List<String> words = new ArrayList<>();

        Entry(ModelKit kit) {
            name = compact(kit.name);
            brand = compact(kit.brand);
            number = compact(kit.kitNumber);
            scale = scaleKey(kit.scale);
            addWords(kit.name);
            addWords(kit.brand);
        }

        private void addWords(String text) {
            for (String w : NOT_LETTER_OR_DIGIT.split(fold(text))) {
                if (!w.isEmpty()) {
                    words.add(w);
                }
            }
        }

        boolean matches(Query q, boolean tolerant) {
            // The whole query at once, so "bf 109" finds "Bf109" and "f 14 d" finds "F-14D".
            if (!tolerant && !q.hasScale && contains(q.whole)) {
                return true;
            }
            // Otherwise every word somewhere: "tamiya tomcat 1/48".
            for (Query.Word w : q.words) {
                if (!matches(w, tolerant)) {
                    return false;
                }
            }
            return !q.words.isEmpty();
        }

        private boolean matches(Query.Word w, boolean tolerant) {
            if (w.scale != null) {
                return w.scale.equals(scale);
            }
            if (contains(w.text)) {
                return true;
            }
            if (!tolerant || !w.typoAllowed) {
                return false;
            }
            for (String word : words) {
                if (nearPrefix(w.text, word)) {
                    return true;
                }
            }
            return false;
        }

        private boolean contains(String text) {
            return !text.isEmpty()
                    && (name.contains(text) || brand.contains(text) || number.contains(text));
        }
    }

    /** What was typed, split up and folded. */
    private static final class Query {
        static final class Word {
            final String text;      // compact
            final String scale;     // "1:48" when the word is a scale, else null
            final boolean typoAllowed;

            Word(String raw) {
                scale = SCALE_WORD.matcher(raw).matches() ? scaleKey(raw) : null;
                text = compact(raw);
                typoAllowed = scale == null && text.length() >= TYPO_MIN_LENGTH && allLetters(text);
            }
        }

        final String whole;
        final List<Word> words = new ArrayList<>();
        final boolean hasScale;

        Query(String typed) {
            whole = compact(typed);
            boolean scale = false;
            if (typed != null) {
                for (String raw : QUERY_WORDS.split(fold(typed).trim())) {
                    Word w = new Word(raw);
                    if (w.scale != null || !w.text.isEmpty()) {
                        words.add(w);
                        scale |= w.scale != null;
                    }
                }
            }
            hasScale = scale;
        }

        boolean isBlank() {
            return words.isEmpty();
        }

        boolean allowsTypos() {
            for (Word w : words) {
                if (w.typoAllowed) {
                    return true;
                }
            }
            return false;
        }
    }

    /** "1/48", "1:48" and "48" stored or typed → "1:48"; anything else → "". */
    private static String scaleKey(String scale) {
        String s = ScaleFormat.normalize(scale);
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static boolean allLetters(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isLetter(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Is `typed` the start of `word` (or the whole word) with at most one letter
     * wrong, missing, extra or two letters swapped? "hasegwa" ~ "hasegawa",
     * "tomcta" ~ "tomcat", "spitfre" ~ "spitfire", "messerschmit" ~ "messerschmitt".
     */
    static boolean nearPrefix(String typed, String word) {
        int n = typed.length();
        for (int len = n - 1; len <= n + 1; len++) {
            if (len >= 1 && len <= word.length()
                    && distanceAtMostOne(typed, word.substring(0, len))) {
                return true;
            }
        }
        return false;
    }

    /** Edit distance (with swaps counted as one edit) of at most 1. */
    static boolean distanceAtMostOne(String a, String b) {
        int la = a.length();
        int lb = b.length();
        if (Math.abs(la - lb) > 1) {
            return false;
        }
        int i = 0;
        while (i < la && i < lb && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        if (i == la && i == lb) {
            return true; // equal
        }
        if (la == lb) {
            // one letter different, or two neighbours swapped
            if (a.substring(i + 1).equals(b.substring(i + 1))) {
                return true;
            }
            return i + 1 < la && a.charAt(i) == b.charAt(i + 1) && a.charAt(i + 1) == b.charAt(i)
                    && a.substring(i + 2).equals(b.substring(i + 2));
        }
        // one letter missing or extra
        return la > lb ? a.substring(i + 1).equals(b.substring(i))
                : a.substring(i).equals(b.substring(i + 1));
    }
}
