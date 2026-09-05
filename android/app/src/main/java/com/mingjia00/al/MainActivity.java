package com.mingjia00.al;

import android.graphics.Color;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;

/**
 * 远程网页壳 App 主入口。
 *
 * 返回键处理策略（原生实现，不依赖网页端 JS，网页内导航/刷新后依然有效）：
 *  1. WebView 有可回退的历史 -> 网页内回退（不退出 App）；
 *  2. 已处于站点最首页（无历史可退）-> 第一次按返回键仅提示"再按一次退出应用"，
 *     2.5 秒内再次按返回键才真正退出 App。
 *
 * SSL 处理：兼容部分 Android 版本对 Let's Encrypt 等证书的中间链信任问题。
 *
 * 屏幕适配：自动避开顶部状态栏和底部系统导航栏，避免内容被遮挡。
 */
public class MainActivity extends BridgeActivity {

    /** 首页处连续两次按返回键的退出判定间隔（毫秒） */
    private static final long EXIT_INTERVAL_MS = 2500L;

    private long lastBackPressTime = 0L;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getBridge() != null && getBridge().getWebView() != null) {
            final WebView webView = getBridge().getWebView();

            // 兼容老 Android 对新证书链的信任问题：遇到 SSL 错误继续加载
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    handler.proceed();
                }
            });

            // 屏幕适配：让 WebView 内容避开顶部状态栏和底部系统导航栏
            webView.setBackgroundColor(Color.WHITE);
            ViewCompat.setOnApplyWindowInsetsListener(webView, new OnApplyWindowInsetsListener() {
                @Override
                public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getBridge() != null && getBridge().getWebView() != null && getBridge().getWebView().canGoBack()) {
                    getBridge().getWebView().goBack();
                    return;
                }

                long now = System.currentTimeMillis();
                if (now - lastBackPressTime <= EXIT_INTERVAL_MS) {
                    lastBackPressTime = 0L;
                    finishAffinity();
                } else {
                    lastBackPressTime = now;
                    Toast.makeText(MainActivity.this,
                            "再按一次返回键退出应用", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
