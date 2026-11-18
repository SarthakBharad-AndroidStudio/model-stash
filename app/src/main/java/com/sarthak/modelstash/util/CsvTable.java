package com.sarthak.modelstash.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A CSV file as a header plus rows, read and written by column NAME, never by
 * position. That is what keeps old files readable when columns are added later
 * (price, shop, tags...): a missing column simply reads as empty, and a column
 * this version doesn't know is ignored.
 *
 * Writes standard CSV (RFC 4180): comma separated, fields quoted when they contain
 * a comma, quote or line break, quotes doubled. Opens cleanly in Excel, LibreOffice
 * and Google Sheets.
 *
 * Plain Java (no Android classes), so it can be unit-tested on your PC.
 */
public final class CsvTable {

    private final List<String> columns = new ArrayList<>();
    private final List<Map<String, String>> rows = new ArrayList<>();

    public CsvTable(String... columns) {
        for (String c : columns) {
            this.columns.add(key(c));
        }
    }

    public List<String> columns() {
        return columns;
    }

    public List<Map<String, String>> rows() {
        return rows;
    }

    /** Starts a new row; fill it with put(). */
    public Map<String, String> addRow() {
        Map<String, String> row = new LinkedHashMap<>();
        rows.add(row);
        return row;
    }

    /** Column names are compared lower-case and trimmed. */
    public static String key(String column) {
        return column == null ? "" : column.trim().toLowerCase(Locale.ROOT);
    }

    /** The value in a row, or "" when the column is missing or empty. */
    public static String get(Map<String, String> row, String column) {
        String value = row.get(key(column));
        return value == null ? "" : value;
    }

    // ---- Writing ----

    public String write() {
        StringBuilder out = new StringBuilder();
        appendLine(out, columns);
        for (Map<String, String> row : rows) {
            List<String> cells = new ArrayList<>();
            for (String c : columns) {
                String v = row.get(c);
                cells.add(v == null ? "" : v);
            }
            appendLine(out, cells);
        }
        return out.toString();
    }

    private static void appendLine(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(cells.get(i)));
        }
        out.append("\r\n");
    }

    static String quote(String value) {
        if (value == null) {
            return "";
        }
        boolean needs = value.contains(",") || value.contains("\"") || value.contains("\n")
                || value.contains("\r") || value.contains(";")
                || (!value.isEmpty() && (value.charAt(0) == ' ' || value.charAt(value.length() - 1) == ' '));
        if (!needs) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // ---- Reading ----

    /** Reads text written by write() (or by a spreadsheet). The first row is the header. */
    public static CsvTable parse(String text) {
        List<List<String>> lines = splitRows(text == null ? "" : text, ',');
        CsvTable table = new CsvTable();
        if (lines.isEmpty()) {
            return table;
        }
        for (String c : lines.get(0)) {
            table.columns.add(key(c));
        }
        for (int r = 1; r < lines.size(); r++) {
            List<String> cells = lines.get(r);
            Map<String, String> row = table.addRow();
            for (int c = 0; c < table.columns.size() && c < cells.size(); c++) {
                row.put(table.columns.get(c), cells.get(c));
            }
        }
        return table;
    }

    /** Splits CSV into rows of fields; quoted fields may contain the separator, quotes and line breaks. */
    static List<List<String>> splitRows(String csv, char separator) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int start = !csv.isEmpty() && csv.charAt(0) == '﻿' ? 1 : 0; // byte-order mark from Excel

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
            } else if (ch == separator) {
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
