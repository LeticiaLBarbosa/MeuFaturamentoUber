package com.thiago.faturamento;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** App nativo: mostra o formulário (assets/index.html) em tela cheia; dados no armazenamento local do celular. */
public class MainActivity extends Activity {
    private static final int SAVE_CSV = 1;
    private WebView web;
    private WebView printWeb;
    private String pendingCsv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#111827"));
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // localStorage guardado no celular
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return !url.startsWith("file:///android_asset/");   // nunca abre navegador externo
            }
        });
        web.setWebChromeClient(new WebChromeClient()); // habilita confirm() dos botões Apagar
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        setContentView(web);
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override protected void onPause() { super.onPause(); android.webkit.CookieManager.getInstance().flush(); }

    /** Exportar CSV e gerar PDF: pontes chamadas pelo JavaScript da página. */
    class Bridge {
        @JavascriptInterface
        public void saveFile(final String name, final String content) {
            runOnUiThread(() -> {
                pendingCsv = content;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("text/csv");
                i.putExtra(Intent.EXTRA_TITLE, name);
                startActivityForResult(i, SAVE_CSV);
            });
        }

        // window.open()/window.print() não funcionam no WebView: renderiza o relatório
        // numa WebView oculta e aciona o diálogo nativo de impressão/"Salvar como PDF".
        @JavascriptInterface
        public void printReport(final String html) {
            runOnUiThread(() -> {
                printWeb = new WebView(MainActivity.this);
                printWeb.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView view, String url) {
                        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                        pm.print("Relatorio Meu Faturamento Uber",
                                view.createPrintDocumentAdapter("Relatorio Meu Faturamento Uber"),
                                new PrintAttributes.Builder().build());
                    }
                });
                printWeb.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            });
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != SAVE_CSV || pendingCsv == null) return;
        if (res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                os.write(pendingCsv.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "CSV exportado", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Erro ao exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        pendingCsv = null;
    }

    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
