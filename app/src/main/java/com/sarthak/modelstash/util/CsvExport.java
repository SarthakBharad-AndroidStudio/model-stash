package com.sarthak.modelstash.util;

import com.sarthak.modelstash.data.ModelKit;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Your stash as a spreadsheet. The first four columns (name, number, brand, scale)
 * are the ones CsvImport reads, so an exported file can be imported again; the
 * rest are there for you to read and sort in Excel / Sheets.
 *
 * Plain Java (no Android classes), so it can be unit-tested on your PC.
 */
public final class CsvExport {

    public static final String[] COLUMNS = {
            "name", "number", "brand", "scale", "status", "list", "description", "link", "added"};

    private CsvExport() {
    }

    /**
     * @param statusLabels the build stage names in order (Unbuilt, In build, Completed), from BuildStatus
     * @param ownedLabel   what to write in "list" for owned kits ("Stash")
     * @param wishLabel    what to write for wishlist kits ("Wishlist")
     */
    public static String write(List<ModelKit> kits, String[] statusLabels, String ownedLabel, String wishLabel) {
        CsvTable table = new CsvTable(COLUMNS);
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        for (ModelKit k : kits) {
            Map<String, String> row = table.addRow();
            row.put("name", k.name);
            row.put("number", orEmpty(k.kitNumber));
            row.put("brand", orEmpty(k.brand));
            row.put("scale", orEmpty(k.scale));
            int status = Math.max(0, Math.min(k.buildStatus, statusLabels.length - 1));
            row.put("status", k.wishlist || statusLabels.length == 0 ? "" : statusLabels[status]);
            row.put("list", k.wishlist ? wishLabel : ownedLabel);
            row.put("description", orEmpty(k.description));
            row.put("link", orEmpty(k.sourceUrl));
            row.put("added", k.createdAt > 0 ? date.format(new Date(k.createdAt)) : "");
        }
        // A byte-order mark makes Excel open the file as UTF-8 (umlauts, Cyrillic...).
        return "﻿" + table.write();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
