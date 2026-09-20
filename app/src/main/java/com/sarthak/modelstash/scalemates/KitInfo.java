package com.sarthak.modelstash.scalemates;

/** The few kit details we can read off a Scalemates kit page. Any field may be null. */
public class KitInfo {
    public String name;      // "Supermarine Spitfire Mk.I"
    public String brand;     // "Tamiya"
    public String kitNumber; // "61119"
    public String scale;     // "1:48"
    public String year;      // "2018" (sometimes "199x" on Scalemates)

    /** One line for the description field, e.g. "Tamiya 61119 · released 2018". */
    public String summaryLine() {
        StringBuilder sb = new StringBuilder();
        if (brand != null) {
            sb.append(brand);
        }
        if (kitNumber != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(kitNumber);
        }
        if (year != null) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("released ").append(year);
        }
        return sb.toString();
    }
}