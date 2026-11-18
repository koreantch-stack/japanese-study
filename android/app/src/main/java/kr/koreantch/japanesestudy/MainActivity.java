package kr.koreantch.japanesestudy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String HOME = "https://koreantch-stack.github.io/japanese-study/";
    private static final String HOST = "koreantch-stack.github.io";
    private static final String RELEASE_API = "https://api.github.com/repos/koreantch-stack/japanese-study/releases/tags/apk";
    private static final String APK_URL = "https://github.com/koreantch-stack/japanese-study/releases/download/apk/japanese-study.apk";
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF477657);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) {
                    return false;
                }
                startActivity(new Intent(Intent.ACTION_VIEW, u));
                return true;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(HOME);
            checkUpdate();
        }
    }

    private void checkUpdate() {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() != 200) return;
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                String body = new JSONObject(sb.toString()).optString("body", "");
                Matcher m = Pattern.compile("versionCode=(\\d+)").matcher(body);
                if (!m.find()) return;
                int latest = Integer.parseInt(m.group(1));
                int current = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                if (latest > current) runOnUiThread(this::showUpdateDialog);
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void showUpdateDialog() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("새 버전이 있어요")
                .setMessage("앱을 최신 버전으로 업데이트할까요?\n내려받은 파일을 눌러 설치하면 됩니다.")
                .setPositiveButton("업데이트", (d, w) ->
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))))
                .setNegativeButton("나중에", null)
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.evaluateJavascript("if('speechSynthesis' in window)speechSynthesis.cancel()", null);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
