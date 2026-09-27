package com.replit.autorunner;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ReplitRunnerPrefs";
    private static final String KEY_TARGET_URL = "target_url";
    private static final String KEY_SELECTOR = "custom_selector";
    private static final String KEY_INTERVAL = "interval_sec";

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvInterval;
    private View statusDot;
    private SwitchCompat switchBackground;
    private MaterialButton btnCustomRun;
    private MaterialButton btnSettings;
    private MaterialButton btnReload;

    private SharedPreferences prefs;
    private String targetUrl;
    private String customSelector;
    private int intervalSec;

    private Handler watcherHandler = new Handler(Looper.getMainLooper());
    private Runnable watcherRunnable;
    private boolean isPageLoaded = false;

    private BroadcastReceiver notificationClickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (RunnerForegroundService.ACTION_TRIGGER_RUN.equals(intent.getAction())) {
                executeCustomRunClick();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        targetUrl = prefs.getString(KEY_TARGET_URL, getString(R.string.default_url));
        customSelector = prefs.getString(KEY_SELECTOR, getString(R.string.default_selector));
        intervalSec = prefs.getInt(KEY_INTERVAL, 8);

        initViews();
        setupWebView();
        setupListeners();
        requestNotificationPermission();

        startRunnerService();

        IntentFilter filter = new IntentFilter(RunnerForegroundService.ACTION_TRIGGER_RUN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationClickReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notificationClickReceiver, filter);
        }

        startAutoWatcher();
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvInterval = findViewById(R.id.tvInterval);
        statusDot = findViewById(R.id.statusDot);
        switchBackground = findViewById(R.id.switchBackground);
        btnCustomRun = findViewById(R.id.btnCustomRun);
        btnSettings = findViewById(R.id.btnSettings);
        btnReload = findViewById(R.id.btnReload);

        tvInterval.setText("Interval: " + intervalSec + "s");
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        String customUA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
        webSettings.setUserAgentString(customUA);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidApp");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isPageLoaded = true;
                tvStatus.setText("Status: Page Ready");
                statusDot.setBackgroundColor(Color.parseColor("#00E599"));
            }
        });

        tvStatus.setText("Status: Loading workspace...");
        webView.loadUrl(targetUrl);
    }

    private void setupListeners() {
        btnCustomRun.setOnClickListener(v -> executeCustomRunClick());

        btnReload.setOnClickListener(v -> {
            isPageLoaded = false;
            tvStatus.setText("Status: Reloading...");
            webView.reload();
        });

        btnSettings.setOnClickListener(v -> showSettingsDialog());

        switchBackground.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startRunnerService();
                Toast.makeText(this, "24/7 Background Runner Active", Toast.LENGTH_SHORT).show();
            } else {
                stopRunnerService();
                Toast.makeText(this, "Background Runner Stopped", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Executes the smart button search and click inside WebView DOM:
     * - Replit: Detects and clicks [action="run_button_used"] / Run command
     * - Colab: Connects runtime AND executes the notebook code cell (Ctrl+F9 / colab-run-button)!
     */
    public void executeCustomRunClick() {
        if (!isPageLoaded || webView == null) {
            Toast.makeText(this, "Page still loading...", Toast.LENGTH_SHORT).show();
            return;
        }

        String escapedSelector = customSelector.replace("'", "\\'");
        String jsScript = "(function() {\n" +
                "  const customSel = '" + escapedSelector + "';\n" +
                "  let btn = null;\n" +
                "  let actionTaken = 'NONE';\n" +
                "\n" +
                "  // Check if we are on Google Colab\n" +
                "  const isColab = window.location.hostname.includes('colab');\n" +
                "\n" +
                "  if (isColab) {\n" +
                "    // 1. If Disconnected: Click Connect button or Dialog OK\n" +
                "    const connectBtn = document.querySelector('colab-connect-button') ||\n" +
                "                       document.querySelector('#connect') ||\n" +
                "                       document.querySelector('#reconnect') ||\n" +
                "                       document.querySelector('colab-dialog paper-button#ok') ||\n" +
                "                       document.querySelector('mwc-button#ok');\n" +
                "    if (connectBtn) {\n" +
                "      const btnTarget = connectBtn.shadowRoot ? connectBtn.shadowRoot.querySelector('#connect') || connectBtn : connectBtn;\n" +
                "      btnTarget.click();\n" +
                "      actionTaken = 'COLAB_CONNECTED';\n" +
                "    }\n" +
                "\n" +
                "    // 2. Cell Execution: Run the code cell so OmniRoute server actually starts!\n" +
                "    // Strategy A: Click colab-run-button\n" +
                "    const runBtn = document.querySelector('colab-run-button');\n" +
                "    if (runBtn) {\n" +
                "      const innerBtn = runBtn.shadowRoot ? runBtn.shadowRoot.querySelector('#run-button') || runBtn : runBtn;\n" +
                "      innerBtn.click();\n" +
                "      actionTaken = (actionTaken === 'NONE' ? 'COLAB_CELL_RUN' : actionTaken + '+CELL_RUN');\n" +
                "    }\n" +
                "\n" +
                "    // Strategy B: Trigger Ctrl+F9 (Run All) keyboard shortcut in Colab\n" +
                "    try {\n" +
                "      const f9Event = new KeyboardEvent('keydown', {\n" +
                "        key: 'F9',\n" +
                "        code: 'F9',\n" +
                "        ctrlKey: true,\n" +
                "        keyCode: 120,\n" +
                "        bubbles: true\n" +
                "      });\n" +
                "      document.dispatchEvent(f9Event);\n" +
                "      if (actionTaken === 'NONE') actionTaken = 'COLAB_RUN_ALL_DISPATCHED';\n" +
                "    } catch (_) {}\n" +
                "\n" +
                "    if (actionTaken !== 'NONE') return 'CLICKED:' + actionTaken;\n" +
                "  }\n" +
                "\n" +
                "  // Replit logic (or custom selector)\n" +
                "  if (customSel && customSel.trim().length > 0) {\n" +
                "    try { btn = document.querySelector(customSel); } catch (_) {}\n" +
                "  }\n" +
                "  if (!btn) {\n" +
                "    btn = document.querySelector('[action=\"run_button_used\"]') ||\n" +
                "          document.querySelector('[data-action=\"run_button_used\"]') ||\n" +
                "          document.querySelector('button:has([action=\"run_button_used\"])') ||\n" +
                "          document.querySelector('button[aria-label*=\"Run\"]');\n" +
                "  }\n" +
                "  if (!btn) {\n" +
                "    const allBtns = Array.from(document.querySelectorAll('button, div[role=\"button\"], a, span'));\n" +
                "    btn = allBtns.find(b => {\n" +
                "      const t = (b.innerText || b.textContent || '').trim().toLowerCase();\n" +
                "      return t.includes('run .replit run command') || t === 'run' || t === '▶ run';\n" +
                "    });\n" +
                "  }\n" +
                "\n" +
                "  if (btn) {\n" +
                "    const clickTarget = btn.closest('button') || btn;\n" +
                "    clickTarget.click();\n" +
                "    return 'CLICKED:REPLIT_RUN (' + (clickTarget.innerText || 'Button') + ')';\n" +
                "  }\n" +
                "  return 'NOT_FOUND';\n" +
                "})();";

        webView.evaluateJavascript(jsScript, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                if (value != null && value.contains("CLICKED")) {
                    tvStatus.setText("🚀 Triggered at " + timestamp);
                    statusDot.setBackgroundColor(Color.parseColor("#00E599"));
                    Toast.makeText(MainActivity.this, "🚀 Triggered: " + value, Toast.LENGTH_SHORT).show();
                } else {
                    tvStatus.setText("🟢 Server Active (Checked " + timestamp + ")");
                    statusDot.setBackgroundColor(Color.parseColor("#00E599"));
                }
            }
        });
    }

    private void startAutoWatcher() {
        watcherRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPageLoaded) {
                    executeCustomRunClick();
                }
                watcherHandler.postDelayed(this, intervalSec * 1000L);
            }
        };
        watcherHandler.postDelayed(watcherRunnable, intervalSec * 1000L);
    }

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_config, null);

        com.google.android.material.textfield.TextInputEditText etUrl = dialogView.findViewById(R.id.etUrl);
        com.google.android.material.textfield.TextInputEditText etSelector = dialogView.findViewById(R.id.etSelector);
        com.google.android.material.textfield.TextInputEditText etInterval = dialogView.findViewById(R.id.etInterval);
        MaterialButton btnDialogReplit = dialogView.findViewById(R.id.btnDialogReplitPreset);
        MaterialButton btnDialogColab = dialogView.findViewById(R.id.btnDialogColabPreset);

        etUrl.setText(targetUrl);
        etSelector.setText(customSelector);
        etInterval.setText(String.valueOf(intervalSec));

        btnDialogReplit.setOnClickListener(v -> {
            etUrl.setText("https://replit.com/@shrqbabu/Gemini-Hub");
            etSelector.setText("[action=\"run_button_used\"]");
            etInterval.setText("8");
            Toast.makeText(this, "Replit preset loaded!", Toast.LENGTH_SHORT).show();
        });

        btnDialogColab.setOnClickListener(v -> {
            etUrl.setText("https://colab.research.google.com");
            etSelector.setText("colab-run-button, #connect");
            etInterval.setText("30");
            Toast.makeText(this, "Colab preset loaded!", Toast.LENGTH_SHORT).show();
        });

        new AlertDialog.Builder(this)
                .setTitle("⚙️ Configure Auto-Runner")
                .setView(dialogView)
                .setPositiveButton("Save & Apply", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newUrl = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
                        String newSelector = etSelector.getText() != null ? etSelector.getText().toString().trim() : "";
                        String newIntervalStr = etInterval.getText() != null ? etInterval.getText().toString().trim() : "8";

                        int newInterval = 8;
                        try {
                            newInterval = Integer.parseInt(newIntervalStr);
                            if (newInterval < 3) newInterval = 3;
                        } catch (Exception ignored) {}

                        targetUrl = newUrl;
                        customSelector = newSelector;
                        intervalSec = newInterval;

                        prefs.edit()
                                .putString(KEY_TARGET_URL, targetUrl)
                                .putString(KEY_SELECTOR, customSelector)
                                .putInt(KEY_INTERVAL, intervalSec)
                                .apply();

                        tvInterval.setText("Interval: " + intervalSec + "s");
                        Toast.makeText(MainActivity.this, "Settings Saved!", Toast.LENGTH_SHORT).show();

                        if (webView.getUrl() == null || !webView.getUrl().equals(targetUrl)) {
                            isPageLoaded = false;
                            webView.loadUrl(targetUrl);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void startRunnerService() {
        Intent serviceIntent = new Intent(this, RunnerForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopRunnerService() {
        Intent serviceIntent = new Intent(this, RunnerForegroundService.class);
        serviceIntent.setAction(RunnerForegroundService.ACTION_STOP_SERVICE);
        startService(serviceIntent);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (watcherHandler != null && watcherRunnable != null) {
            watcherHandler.removeCallbacks(watcherRunnable);
        }
        try {
            unregisterReceiver(notificationClickReceiver);
        } catch (Exception ignored) {}
    }

    public static class WebAppInterface {
        Context mContext;
        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void notifyStatus(String msg) {
        }
    }
}
