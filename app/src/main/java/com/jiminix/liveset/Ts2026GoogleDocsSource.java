package com.jiminix.liveset;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Read-only transport and offline cache for the TS 2026 Google Docs source.
 *
 * This component intentionally NEVER updates Song, SetListModel or songIds:
 * document order must not replace the user's Manager ordering.
 * The document must permit unauthenticated export for this transport to work.
 * Private documents require a separate OAuth-based transport.
 */
public final class Ts2026GoogleDocsSource {
    public static final String DOCUMENT_ID =
        "1kfLp1alEdnLunA-es-s13V58WcS-uV4HYVP8hl45zhE";
    private static final String PREFS = "ts2026_google_docs";
    private static final String CACHE_KEY = "last_successful_html";
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Ts2026GoogleDocsSource() {}

    public interface Callback {
        void onSuccess(String html, boolean fromCache);
        void onError(String message, String cachedHtml);
    }

    public static boolean isTs2026(String playlistName) {
        return playlistName != null &&
            playlistName.trim().replaceAll("\\s+", " ").equalsIgnoreCase("TS 2026");
    }

    public static String cachedHtml(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CACHE_KEY, "");
    }

    public static void refresh(Context context, Callback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://docs.google.com/document/d/" +
                    DOCUMENT_ID + "/export?format=html");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept", "text/html");
                int status = connection.getResponseCode();
                if (status != 200) {
                    throw new java.io.IOException("HTTP " + status +
                        " : vérifier les autorisations de lecture du document");
                }
                String html;
                try (InputStream in = connection.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (out.size() + count > MAX_BYTES)
                            throw new java.io.IOException("Document trop volumineux");
                        out.write(buffer, 0, count);
                    }
                    html = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
                if (!html.toLowerCase(java.util.Locale.ROOT).contains("<html"))
                    throw new java.io.IOException("Réponse Google Docs non HTML");
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(CACHE_KEY, html).apply();
                final String result = html;
                MAIN.post(() -> callback.onSuccess(result, false));
            } catch (Exception e) {
                final String message = e.getMessage() == null ?
                    "Impossible de lire Google Docs" : e.getMessage();
                final String cached = cachedHtml(app);
                MAIN.post(() -> callback.onError(message, cached));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "TS2026-GoogleDocs").start();
    }
}
