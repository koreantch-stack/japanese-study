package kr.koreantch.japanesestudy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerController;
import android.webkit.ServiceWorkerWebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String HOME = "https://koreantch-stack.github.io/japanese-study/";
    private static final String HOST = "koreantch-stack.github.io";
    private static final String RELEASE_API = "https://api.github.com/repos/koreantch-stack/japanese-study/releases/tags/apk";
    private static final String APK_URL = "https://github.com/koreantch-stack/japanese-study/releases/download/apk/japanese-study.apk";
    private WebView web;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private static final String TTS_SHIM = """
(function(){
  if(window.__ttsShim)return;
  window.__ttsShim=true;
  var cbs={},seq=0;
  window.__ttsDone=function(id,err){
    var u=cbs[id];delete cbs[id];
    synth.speaking=Object.keys(cbs).length>0;
    if(!u)return;
    try{if(err){u.onerror&&u.onerror({error:'synthesis-failed'});}else{u.onend&&u.onend({});}}catch(e){}
  };
  function U(t){this.text=t===undefined?'':String(t);this.lang='';this.rate=1;this.pitch=1;this.volume=1;this.voice=null;this.onstart=null;this.onend=null;this.onerror=null;}
  var synth={
    speaking:false,pending:false,paused:false,onvoiceschanged:null,
    speak:function(u){
      var id=String(++seq);cbs[id]=u;this.speaking=true;
      AndroidTTS.speak(id,String(u.text),u.lang||'ja-JP',Number(u.rate)||1,Number(u.pitch)||1);
      try{u.onstart&&u.onstart({});}catch(e){}
    },
    cancel:function(){cbs={};this.speaking=false;AndroidTTS.stop();},
    pause:function(){},resume:function(){},
    getVoices:function(){return [{lang:'ja-JP',name:'ja-JP',voiceURI:'ja-JP',localService:true,default:true}];},
    addEventListener:function(){},removeEventListener:function(){}
  };
  try{Object.defineProperty(window,'speechSynthesis',{value:synth,configurable:true,writable:true});}catch(e){window.speechSynthesis=synth;}
  try{window.SpeechSynthesisUtterance=U;}catch(e){}
  window.addEventListener('pagehide',function(){try{synth.cancel();}catch(e){}});
})();
""";

    private class TtsBridge {
        @JavascriptInterface
        public void speak(String id, String text, String lang, double rate, double pitch) {
            if (tts == null || !ttsReady) {
                done(id, true);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "일본어 음성을 쓸 수 없어요. 휴대폰 설정에서 텍스트 음성 변환의 일본어 음성 데이터를 설치해 주세요.",
                        Toast.LENGTH_LONG).show());
                return;
            }
            Locale loc = Locale.forLanguageTag(lang);
            int r = tts.setLanguage(loc);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                done(id, true);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "일본어 음성 데이터가 없어요. 설정 > 일반 > 언어 > 텍스트 음성 변환에서 일본어를 설치해 주세요.",
                        Toast.LENGTH_LONG).show());
                return;
            }
            tts.setSpeechRate((float) rate);
            tts.setPitch((float) pitch);
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, id);
        }

        @JavascriptInterface
        public void stop() {
            if (tts != null) tts.stop();
        }
    }

    private void done(String id, boolean error) {
        final String js = "window.__ttsDone&&window.__ttsDone('" + id + "'," + error + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF477657);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ServiceWorkerWebSettings sw = ServiceWorkerController.getInstance().getServiceWorkerWebSettings();
        sw.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { }
                    @Override public void onDone(String utteranceId) { done(utteranceId, false); }
                    @Override public void onError(String utteranceId) { done(utteranceId, true); }
                });
            }
        });
        web.addJavascriptInterface(new TtsBridge(), "AndroidTTS");
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, TTS_SHIM,
                    Collections.singleton("https://" + HOST));
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (tts != null) tts.stop();
                view.evaluateJavascript(TTS_SHIM, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(TTS_SHIM, null);
            }

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
        if (tts != null) tts.stop();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
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
