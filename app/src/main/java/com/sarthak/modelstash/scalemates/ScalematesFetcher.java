package com.sarthak.modelstash.scalemates;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads ONE Scalemates kit page, only when you tap "Fetch details", and
 * hands it to ScalematesParser. No searching, crawling or image downloads.
 */
public final class ScalematesFetcher {

    public interface Callback {
        void onSuccess(KitInfo info);

        void onError(String message);
    }

    private static final int MAX_PAGE_BYTES = 3 * 1024 * 1024;
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ScalematesFetcher() {
    }

    /** callback runs on the main thread. */
    public static void fetch(String kitUrl, Callback callback) {
        NETWORK.execute(() -> {
            try {
                KitInfo info = ScalematesParser.parse(download(kitUrl));
                if (info == null) {
                    MAIN.post(() -> callback.onError(
                            "Couldn't find kit details on that page. Enter them manually."));
                } else {
                    MAIN.post(() -> callback.onSuccess(info));
                }
            } catch (IOException e) {
                String message = "Couldn't load the page (" + e.getMessage() + "). Enter the details manually.";
                MAIN.post(() -> callback.onError(message));
            }
        });
    }

    private static String download(String kitUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(kitUrl).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        // An honest user agent: Scalemates can see what is asking and block it if they want to.
        connection.setRequestProperty("User-Agent", "ModelStash/1.0 (personal Android app; single kit page on user request)");
        connection.setRequestProperty("Accept", "text/html");
        connection.setRequestProperty("Accept-Language", "en");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1 && out.size() < MAX_PAGE_BYTES) {
                    out.write(buffer, 0, read);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            connection.disconnect();
        }
    }
}