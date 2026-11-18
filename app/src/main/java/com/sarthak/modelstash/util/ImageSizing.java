package com.sarthak.modelstash.util;

/**
 * The arithmetic behind shrinking photos (PhotoCompressor does the pixels).
 * Plain Java, so it can be unit-tested on your PC.
 */
public final class ImageSizing {

    /** Photos are stored at most this many pixels on their long side: sharp on any phone screen, ~0.5 MB. */
    public static final int MAX_SIDE = 2560;
    /** JPEG quality for stored photos: no visible loss, a fraction of the camera's file size. */
    public static final int JPEG_QUALITY = 85;

    private ImageSizing() {
    }

    /** true when a photo of this size is already small enough to keep exactly as it is. */
    public static boolean fits(int width, int height, int maxSide) {
        return Math.max(width, height) <= maxSide;
    }

    /**
     * The largest power-of-two step down that still leaves the image at least
     * maxSide on its long side. Decoding with it keeps memory low (a 50 MP photo is
     * decoded at a quarter of its pixels) without losing detail we'd keep.
     */
    public static int sampleSize(int width, int height, int maxSide) {
        int longSide = Math.max(width, height);
        int sample = 1;
        while (longSide / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        return sample;
    }

    /** Final width and height: scaled so the long side is maxSide, aspect ratio kept, never enlarged. */
    public static int[] targetSize(int width, int height, int maxSide) {
        int longSide = Math.max(width, height);
        if (longSide <= maxSide || longSide <= 0) {
            return new int[]{width, height};
        }
        double scale = (double) maxSide / longSide;
        return new int[]{
                Math.max(1, (int) Math.round(width * scale)),
                Math.max(1, (int) Math.round(height * scale))};
    }
}
