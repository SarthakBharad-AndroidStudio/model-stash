package com.sarthak.modelstash.scalemates;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads kit details out of one Scalemates kit page.
 *
 * It only uses the page's <title> / og:title and its meta description, which
 * currently look like this:
 *   title:       "Supermarine Spitfire Mk.I, Tamiya 61119 (2018)"
 *   description: "Tamiya model kit in scale 1:48, 61119 is a NEW tool released in 2018 | ..."
 *
 * Plain Java (no Android classes), so it can be unit-tested on your PC.
 * If Scalemates changes its page format, this is the only file to update.
 */
public final class ScalematesParser {

    private static final Pattern KIT_URL = Pattern.compile(
            "https?://(?:www\\.)?scalemates\\.com/(?:[a-z]{2}/)?kits/[^\\s\"'<>]+",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern META_TAG =
            Pattern.compile("<meta\\s[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE =
            Pattern.compile("([a-zA-Z:_-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    private static final Pattern TITLE_TAG =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // "<name>, <brand> <number> (<year>)" - the name may itself contain commas,
    // so the greedy (.+) splits on the LAST comma. The year is optional.
    private static final Pattern TITLE =
            Pattern.compile("^(.+),\\s+(.+?)(?:\\s*\\(([0-9]{3}[0-9x]|[0-9]{2}xx)\\))?$");
    private static final Pattern SCALE =
            Pattern.compile("\\bscale\\s+(1:\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_AFTER_SCALE = Pattern.compile(
            "\\bscale\\s+1:[\\d.,]+,\\s+(.+?)\\s+is\\s+(?:a|an|the)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SITE_SUFFIX =
            Pattern.compile("\\s*[|\\-–]\\s*Scalemates\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    private ScalematesParser() {
    }

    /**
     * Finds a Scalemates kit link inside any text (e.g. what Chrome shares:
     * "Supermarine Spitfire ... https://www.scalemates.com/kits/...").
     *
     * @return the link, or null if the text contains none.
     */
    public static String extractKitUrl(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = KIT_URL.matcher(text);
        if (!m.find()) {
            return null;
        }
        String url = m.group();
        int hash = url.indexOf('#');
        if (hash >= 0) {
            url = url.substring(0, hash);
        }
        // Drop punctuation that belongs to the surrounding sentence, not the link.
        while (!url.isEmpty() && ".,;:!?)]".indexOf(url.charAt(url.length() - 1)) >= 0) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.regionMatches(true, 0, "http://", 0, 7)) {
            url = "https://" + url.substring(7);
        }
        return url;
    }

    /** @return the kit details, or null if the page doesn't look like a kit page. */
    public static KitInfo parse(String html) {
        if (html == null) {
            return null;
        }
        Map<String, String> meta = readMetaTags(html);
        String title = firstNonBlank(meta.get("og:title"), readTitleTag(html));
        if (title == null) {
            return null;
        }
        title = SITE_SUFFIX.matcher(decodeEntities(title).trim()).replaceFirst("");
        String description = decodeEntities(firstNonBlank(meta.get("og:description"), meta.get("description")));

        KitInfo info = new KitInfo();
        String brandAndNumber = null;
        Matcher t = TITLE.matcher(title);
        if (t.matches()) {
            info.name = t.group(1).trim();
            brandAndNumber = t.group(2).trim();
            info.year = t.group(3);
        } else {
            info.name = title;
        }

        if (description != null) {
            Matcher s = SCALE.matcher(description);
            if (s.find()) {
                info.scale = s.group(1).replace(',', '.');
            }
            Matcher n = NUMBER_AFTER_SCALE.matcher(description);
            if (n.find()) {
                info.kitNumber = n.group(1).trim();
            }
        }

        if (brandAndNumber != null) {
            splitBrandAndNumber(info, brandAndNumber);
        }
        return info.name.isEmpty() ? null : info;
    }

    /** "Tamiya 61119" -> brand "Tamiya"; uses the kit number from the description when known. */
    private static void splitBrandAndNumber(KitInfo info, String text) {
        if (info.kitNumber != null && text.endsWith(" " + info.kitNumber)) {
            info.brand = text.substring(0, text.length() - info.kitNumber.length()).trim();
            return;
        }
        // Fallback: the brand is every word before the first word containing a digit.
        String[] words = text.split("\\s+");
        StringBuilder brand = new StringBuilder();
        int i = 0;
        while (i < words.length && !words[i].matches(".*\\d.*")) {
            if (brand.length() > 0) {
                brand.append(' ');
            }
            brand.append(words[i]);
            i++;
        }
        info.brand = brand.length() > 0 ? brand.toString() : null;
        if (info.kitNumber == null && i < words.length) {
            StringBuilder number = new StringBuilder();
            for (int j = i; j < words.length; j++) {
                if (number.length() > 0) {
                    number.append(' ');
                }
                number.append(words[j]);
            }
            info.kitNumber = number.toString();
        }
    }

    private static Map<String, String> readMetaTags(String html) {
        Map<String, String> result = new HashMap<>();
        Matcher tag = META_TAG.matcher(html);
        while (tag.find()) {
            Map<String, String> attrs = new HashMap<>();
            Matcher a = ATTRIBUTE.matcher(tag.group());
            while (a.find()) {
                String value = a.group(2) != null ? a.group(2) : a.group(3);
                attrs.put(a.group(1).toLowerCase(Locale.ROOT), value);
            }
            String key = attrs.containsKey("property") ? attrs.get("property") : attrs.get("name");
            String content = attrs.get("content");
            if (key != null && content != null) {
                String k = key.toLowerCase(Locale.ROOT);
                if (!result.containsKey(k)) {
                    result.put(k, content);
                }
            }
        }
        return result;
    }

    private static String readTitleTag(String html) {
        Matcher m = TITLE_TAG.matcher(html);
        return m.find() ? m.group(1).replaceAll("\\s+", " ").trim() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        if (b != null && !b.trim().isEmpty()) {
            return b;
        }
        return null;
    }

    static String decodeEntities(String s) {
        if (s == null || s.indexOf('&') < 0) {
            return s;
        }
        Matcher m = NUMERIC_ENTITY.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int code = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        m.appendTail(sb);
        return sb.toString()
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&"); // last, so "&amp;lt;" becomes "&lt;" and not "<"
    }
}