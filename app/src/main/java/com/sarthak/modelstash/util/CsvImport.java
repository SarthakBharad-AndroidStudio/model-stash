package com.sarthak.modelstash.util;

import com.sarthak.modelstash.data.ModelKit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a CSV file into models. Only four columns are used: name, number,
 * brand and scale. Anything else in the file is ignored, so a spreadsheet
 * with prices or notes can be imported as it is.
 *
 * A header row decides which column is which; without one, the order
 * name, number, brand, scale is assumed. Only the name is required.
 *
 * Plain Java (no Android classes), so it can be unit-tested on your PC.
 */
public final class CsvImport {

    private static final String[] DEFAULT_COLUMNS = {"name", "number", "brand", "scale"};

    private CsvImport() {
    }

    /**
     * @param wishlist true puts every imported row on the wishlist, false in the owned catalogue
     * @return one ModelKit per usable row; rows without a name are skipped
     */
    public static List<ModelKit> parse(String csv, boolean wishlist) {
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
                    default:
                        break; // price, notes, anything else: ignored on purpose
                }
            }
            if (hasName) {
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
            default:
                return h; // unknown column, ignored below
        }
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