package com.sarthak.modelstash.util;

import com.sarthak.modelstash.data.ModelKit;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a CSV file into models.
 *
 * Columns it understands (by header name; common spellings work too):
 *   name (required), number, brand, scale,
 *   status (Unbuilt / On the bench / Completed), list (Stash / Wishlist),
 *   description (or notes), link (Scalemates URL), added (yyyy-MM-dd).
 * These are exactly the columns CsvExport writes, so an exported file is a
 * complete copy of your list (everything except the photos) and imports back
 * as it was. Any other column (price...) is ignored, so a spreadsheet you
 * already keep can be imported as it is.
 *
 * Without a header row, the order name, number, brand, scale is assumed.
 *
 * Plain Java (no Android classes), so it can be unit-tested on your PC.
 */
public final class CsvImport {

    private static final String[] DEFAULT_COLUMNS = {"name", "number", "brand", "scale"};

    private CsvImport() {
    }

    /**
     * @param wishlist the list for rows that don't say (no "list" column): true = wishlist
     * @return one ModelKit per usable row; rows without a name are skipped
     */
    public static List<ModelKit> parse(String csv, boolean wishlist) {
        return parse(csv, wishlist, new String[0]);
    }

    /**
     * Same, also understanding the app's own (possibly translated) status names,
     * e.g. BuildStatus.labels(context), as written by CsvExport.
     */
    public static List<ModelKit> parse(String csv, boolean wishlist, String[] statusLabels) {
        List<ModelKit> models = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) {
            return models;
        }
        List<List<String>> rows = splitRows(csv);
        if (rows.isEmpty()) {
            return models;
        }

        String[] columns = DEFAULT_COLUMNS;
        int firstDataRow = 0;
        if (looksLikeHeader(rows.get(0))) {
            List<String> header = rows.get(0);
            columns = new String[header.size()];
            for (int i = 0; i < header.size(); i++) {
                columns[i] = canonicalColumn(header.get(i));
            }
            firstDataRow = 1;
        }

        for (int r = firstDataRow; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            ModelKit model = new ModelKit();
            model.wishlist = wishlist;
            boolean hasName = false;
            for (int c = 0; c < row.size() && c < columns.length; c++) {
                String value = row.get(c).trim();
                if (value.isEmpty()) {
                    continue;
                }
                switch (columns[c]) {
                    case "name":
                        model.name = value;
                        hasName = true;
                        break;
                    case "number":
                        model.kitNumber = value;
                        break;
                    case "brand":
                        model.brand = value;
                        break;
                    case "scale":
                        model.scale = ScaleFormat.normalize(value); // null when it isn't a scale
                        break;
                    case "status":
                        model.buildStatus = statusOf(value, statusLabels);
                        break;
                    case "list":
                        Boolean onWishlist = listOf(value);
                        if (onWishlist != null) {
                            model.wishlist = onWishlist;
                        }
                        break;
                    case "description":
                        model.description = value;
                        break;
                    case "link":
                        model.sourceUrl = value;
                        break;
                    case "added":
                        model.createdAt = dateOf(value);
                        break;
                    default:
                        break; // price and anything else: ignored on purpose
                }
            }
            if (hasName) {
                if (model.wishlist) {
                    model.buildStatus = 0; // a kit you don't own yet has no build stage
                }
                models.add(model);
            }
        }
        return models;
    }

    /** Accepts the names a spreadsheet is likely to use for each of the four columns. */
    private static String canonicalColumn(String header) {
        String h = header.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        switch (h) {
            case "name":
            case "model":
            case "kit":
            case "title":
                return "name";
            case "number":
            case "no":
            case "no.":
            case "kit number":
            case "kit no":
            case "kit no.":
            case "model number":
            case "item number":
            case "item no":
            case "code":
                return "number";
            case "brand":
            case "manufacturer":
            case "maker":
            case "make":
                return "brand";
            case "scale":
            case "ratio":
            case "size":
                return "scale";
            case "status":
            case "build status":
            case "stage":
            case "build stage":
                return "status";
            case "list":
            case "owned":
            case "collection":
                return "list";
            case "description":
            case "notes":
            case "note":
            case "comment":
            case "comments":
                return "description";
            case "link":
            case "url":
            case "scalemates":
            case "source":
                return "link";
            case "added":
            case "date added":
            case "created":
                return "added";
            default:
                return h; // unknown column, ignored below
        }
    }

    /** "On the bench" / "in build" / "1" -> 1 and so on; anything unknown is Unbuilt (0). */
    static int statusOf(String value, String[] labels) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] != null && labels[i].trim().toLowerCase(Locale.ROOT).equals(v)) {
                return Math.min(i, 2);
            }
        }
        switch (v) {
            case "1":
            case "on the bench":
            case "in build":
            case "in progress":
            case "started":
            case "building":
                return 1;
            case "2":
            case "completed":
            case "complete":
            case "built":
            case "finished":
            case "done":
                return 2;
            default:
                return 0;
        }
    }

    /** "Wishlist" -> true, "Stash" -> false, anything else -> null (use the default). */
    static Boolean listOf(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("wish") || v.equals("wanted") || v.equals("want")) {
            return true;
        }
        if (v.equals("stash") || v.equals("owned") || v.equals("own") || v.equals("catalogue")
                || v.equals("collection") || v.equals("yes")) {
            return false;
        }
        return null;
    }

    /** "2026-11-18" -> that day (midnight, phone's time zone, like the export); 0 when it isn't a date. */
    static long dateOf(String value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false);
        try {
            return format.parse(value.trim()).getTime();
        } catch (ParseException | RuntimeException e) {
            return 0;
        }
    }

    /**
     * Drops rows that are kits you already have (DuplicateFinder's rules), and rows
     * repeated within the file, so importing the same file twice adds nothing new.
     */
    public static List<ModelKit> withoutDuplicates(List<ModelKit> imported, List<ModelKit> existing) {
        List<ModelKit> seen = new ArrayList<>(existing);
        List<ModelKit> kept = new ArrayList<>();
        for (ModelKit k : imported) {
            if (DuplicateFinder.find(k, seen).isEmpty()) {
                kept.add(k);
                seen.add(k);
            }
        }
        return kept;
    }

    private static boolean looksLikeHeader(List<String> row) {
        for (String cell : row) {
            if ("name".equals(canonicalColumn(cell))) {
                return true;
            }
        }
        return false;
    }

    /** Splits CSV text into rows of fields, honouring "quoted, fields" and "" inside them. */
    private static List<List<String>> splitRows(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        // A file saved by Excel may start with a byte-order mark; drop it.
        int start = !csv.isEmpty() && csv.charAt(0) == '﻿' ? 1 : 0;

        for (int i = start; i < csv.length(); i++) {
            char ch = csv.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',' || ch == ';') { // Excel in German locales writes semicolons
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                if (!isBlank(row)) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }
        row.add(field.toString());
        if (!isBlank(row)) {
            rows.add(row);
        }
        return rows;
    }

    private static boolean isBlank(List<String> row) {
        for (String cell : row) {
            if (!cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}