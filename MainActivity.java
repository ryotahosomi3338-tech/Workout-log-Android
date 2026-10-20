package jp.personal.traininglog;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private static final int PICK_FILE = 17, SAVE_BACKUP = 18;
  private static final String ORIGIN = "https://appassets.androidplatform.net";
  private WebView web;
  private ValueCallback<Uri[]> fileCallback;
  private String pendingBackup;

  public final class BackupBridge {
    @JavascriptInterface public void saveBackup(String json) {
      if (json == null || json.length() > 8_000_000) return;
      runOnUiThread(() -> {
        pendingBackup = json;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "training-log-backup.json");
        startActivityForResult(intent, SAVE_BACKUP);
      });
    }
  }

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    web = new WebView(this);
    setContentView(web);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(false);
    s.setAllowFileAccessFromFileURLs(false);
    s.setAllowUniversalAccessFromFileURLs(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    WebView.setWebContentsDebuggingEnabled(false);
    WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
      .build();
    web.setWebViewClient(new WebViewClient() {
      @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
        return loader.shouldInterceptRequest(request.getUrl());
      }
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
        return !request.getUrl().toString().startsWith(ORIGIN + "/assets/");
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileCallback = cb;
        try { startActivityForResult(params.createIntent(), PICK_FILE); return true; }
        catch (Exception ex) { fileCallback = null; return false; }
      }
    });
    web.addJavascriptInterface(new BackupBridge(), "TrainingLogAndroid");
    web.loadUrl(ORIGIN + "/assets/index.html");
  }

  @Override protected void onActivityResult(int req, int result, Intent data) {
    super.onActivityResult(req, result, data);
    if (req == PICK_FILE && fileCallback != null) {
      fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
      fileCallback = null;
    }
    if (req == SAVE_BACKUP) {
      if (result == RESULT_OK && data != null && data.getData() != null && pendingBackup != null) {
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
          if (out == null) throw new Exception("Cannot open destination");
          out.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
          Toast.makeText(this, "バックアップを保存しました", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
          Toast.makeText(this, "バックアップの保存に失敗しました", Toast.LENGTH_LONG).show();
        }
      }
      pendingBackup = null;
    }
  }
  @Override public void onBackPressed() {
    if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
  }
}
