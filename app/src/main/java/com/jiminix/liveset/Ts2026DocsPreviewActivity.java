package com.jiminix.liveset;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;
import android.net.Uri;

/**
 * Read-only full-document preview, to validate formatting and access before
 * implementing per-song extraction. Does not mutate playlists or lyrics.
 */
public class Ts2026DocsPreviewActivity extends Activity {
    private WebView browser;
    private TextView status;
    private String targetTitle = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        targetTitle = getIntent().getStringExtra("song_title");
        if (targetTitle == null) targetTitle = "";
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.BLACK);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button close = new Button(this);
        close.setText("‹ Retour");
        close.setOnClickListener(v -> finish());
        header.addView(close);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setText("TS 2026 · chargement Google Docs…");
        header.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        layout.addView(header);
        LinearLayout actions = new LinearLayout(this);
        Button retry = new Button(this);
        retry.setText("Actualiser");
        retry.setOnClickListener(v -> refreshDocument());
        actions.addView(retry, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button open = new Button(this);
        open.setText("Ouvrir Google Docs");
        open.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://docs.google.com/document/d/" +
                Ts2026GoogleDocsSource.DOCUMENT_ID + "/edit"))));
        actions.addView(open, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        layout.addView(actions);
        Button copyTest = new Button(this);
        copyTest.setText("Connexion Google · test sur copie");
        copyTest.setOnClickListener(v -> startActivity(
            new Intent(this, Ts2026DocsWriteTestActivity.class)));
        layout.addView(copyTest);
        browser = new WebView(this);
        browser.setBackgroundColor(Color.BLACK);
        browser.getSettings().setJavaScriptEnabled(false);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        browser.setWebViewClient(new android.webkit.WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (!targetTitle.isEmpty()) view.findAllAsync(targetTitle);
            }
        });
        browser.getSettings().setBuiltInZoomControls(true);
        browser.getSettings().setDisplayZoomControls(false);
        browser.getSettings().setUseWideViewPort(true);
        browser.getSettings().setLoadWithOverviewMode(true);
        layout.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        String cached = Ts2026GoogleDocsSource.cachedHtml(this);
        if (!cached.isEmpty()) {
            showHtml(cached);
            status.setText("TS 2026 · copie locale · actualisation…");
        }
        refreshDocument();
    }

    private void refreshDocument() {
        status.setText("TS 2026 · vérification Google Docs…");
        Ts2026GoogleDocsSource.refresh(this, new Ts2026GoogleDocsSource.Callback() {
            @Override public void onSuccess(String html, boolean fromCache) {
                if (isFinishing() || isDestroyed()) return;
                showHtml(html);
                status.setText(fromCache ? "TS 2026 · copie locale" : "TS 2026 · Google Docs à jour");
            }
            @Override public void onError(String message, String cachedHtml) {
                if (isFinishing() || isDestroyed()) return;
                status.setText(cachedHtml.isEmpty() ?
                    "Accès Google Docs indisponible : " + message :
                    "Copie locale affichée · accès Google Docs indisponible : " + message);
            }
        });
    }

    private void showHtml(String html) {
        // Google Docs HTML may contain external resources. JavaScript stays off.
        browser.loadDataWithBaseURL("https://docs.google.com/", html,
            "text/html", "UTF-8", null);
    }

    @Override protected void onDestroy() {
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
