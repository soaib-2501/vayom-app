package com.carawintech.vayom;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {

    private boolean doubleBackToExitPressedOnce = false;
    private volatile boolean pageScrolledDown = false;

    private static final String FULLSCREEN_JS =
        "(function(){" +
        "function applyFs(){" +
        "var m=document.querySelector('meta[name=\"viewport\"]');" +
        "if(m){if(!m.content.includes('viewport-fit=cover'))m.content+=', viewport-fit=cover';}" +
        "else{m=document.createElement('meta');m.name='viewport';m.content='width=device-width, initial-scale=1.0, viewport-fit=cover';if(document.head)document.head.appendChild(m);}" +
        "var id='__vayom_fs_override__';" +
        "if(!document.getElementById(id)){" +
        "var s=document.createElement('style');s.id=id;" +
        "s.textContent=':root,html,body{margin:0!important;padding:0!important;padding-top:0!important;padding-bottom:0!important;width:100%!important;min-height:100vh!important;min-height:100dvh!important;box-sizing:border-box!important;overflow-x:hidden!important;overflow-y:auto!important;}'+" +
        "'#root,main{width:100%!important;min-height:100%!important;}'+" +
        "'body{background-size:cover!important;background-position:center!important;background-repeat:no-repeat!important;}'+" +
        "'div[style*=\"login-bg\"],[class*=\"min-h-screen\"]{min-height:100vh!important;min-height:100dvh!important;width:100%!important;background-size:cover!important;background-position:center!important;background-repeat:no-repeat!important;object-fit:cover!important;}';" +
        "if(document.head)document.head.appendChild(s);else if(document.documentElement)document.documentElement.appendChild(s);" +
        "}" +
        "}" +
        "applyFs();" +
        "if(!window.__fsListenerAttached){" +
        "window.__fsListenerAttached=true;" +
        "window.addEventListener('focusin',function(e){" +
        "if(e.target&&(e.target.tagName==='INPUT'||e.target.tagName==='TEXTAREA'||e.target.isContentEditable)){" +
        "setTimeout(function(){try{e.target.scrollIntoView({behavior:'smooth',block:'center'});}catch(err){e.target.scrollIntoView(false);}},300);" +
        "}" +
        "},true);" +
        "}" +
        "if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',applyFs);}" +
        "window.addEventListener('load',applyFs);" +
        "})();";

    private static final String PTR_JS =
        "(function(){" +
        "if(window.__ptrInit)return;window.__ptrInit=true;" +
        "var last=null;" +
        "function st(el){" +
        "if(!el)return 0;" +
        "if(el===document||el===document.documentElement||el===document.body)return (document.scrollingElement||document.documentElement).scrollTop;" +
        "return el.scrollTop;" +
        "}" +
        "document.addEventListener('scroll',function(e){last=e.target;},{capture:true,passive:true});" +
        "document.addEventListener('touchstart',function(e){" +
        "var n=e.target,up=false;" +
        "while(n&&n!==document.documentElement){if(n.scrollTop>0){up=true;break;}n=n.parentElement;}" +
        "if(!up&&st(last)>0)up=true;" +
        "if(!up&&window.pageYOffset>0)up=true;" +
        "if(window.AndroidPTR)window.AndroidPTR.setCanScrollUp(up);" +
        "},{capture:true,passive:true});" +
        "})();";

    private class ScrollBridge {
        @JavascriptInterface
        public void setCanScrollUp(boolean value) {
            pageScrolledDown = value;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge: content draws behind status bar and nav bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        allowContentInCutout();
        setupFullscreenInsets();
        hideSystemBars();
        setupPullToRefresh();

        // Re-apply after splash screen transition finishes
        new Handler(Looper.getMainLooper()).postDelayed(this::hideSystemBars, 300);
        new Handler(Looper.getMainLooper()).postDelayed(this::hideSystemBars, 1000);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        hideSystemBars();
    }

    @Override
    public void onStart() {
        super.onStart();
        hideSystemBars();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        hideSystemBars();
    }

    private void setupFullscreenInsets() {
        Window window = getWindow();
        View decorView = window.getDecorView();

        ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, windowInsets) -> {
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            // Adjust bottom padding for soft keyboard so inputs are not covered
            v.setPadding(0, 0, 0, imeInsets.bottom);
            return windowInsets;
        });

        WebView webView = this.bridge != null ? this.bridge.getWebView() : null;
        if (webView != null) {
            webView.setFitsSystemWindows(false);
            webView.setPadding(0, 0, 0, 0);
            ViewCompat.setOnApplyWindowInsetsListener(webView, (v, insets) -> {
                v.setPadding(0, 0, 0, 0);
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    // Notch / camera cutout ke aas-paas bhi splash full screen dikhe
    private void allowContentInCutout() {
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
    }

    private void hideSystemBars() {
        Window window = getWindow();

        // Make bars fully transparent so content shows behind them
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // Hide both status bar and navigation bar
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    private void applyFullscreenFor(String url) {
        hideSystemBars();
    }

    private void setupPullToRefresh() {
        WebView webView = this.bridge.getWebView();
        webView.addJavascriptInterface(new ScrollBridge(), "AndroidPTR");

        this.bridge.setWebViewClient(new BridgeWebViewClient(this.bridge) {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                applyFullscreenFor(url);
                view.evaluateJavascript(FULLSCREEN_JS, null);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                view.evaluateJavascript(FULLSCREEN_JS, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                applyFullscreenFor(url);
                view.evaluateJavascript(FULLSCREEN_JS, null);
                view.evaluateJavascript(PTR_JS, null);
            }
        });

        ViewGroup parent = (ViewGroup) webView.getParent();
        int index = parent.indexOfChild(webView);

        SwipeRefreshLayout swipeRefreshLayout = new SwipeRefreshLayout(this);
        swipeRefreshLayout.setFitsSystemWindows(false);
        swipeRefreshLayout.setPadding(0, 0, 0, 0);
        ViewCompat.setOnApplyWindowInsetsListener(swipeRefreshLayout, (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return WindowInsetsCompat.CONSUMED;
        });

        parent.removeView(webView);
        webView.setFitsSystemWindows(false);
        webView.setPadding(0, 0, 0, 0);
        ViewCompat.setOnApplyWindowInsetsListener(webView, (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return WindowInsetsCompat.CONSUMED;
        });

        swipeRefreshLayout.addView(webView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        parent.addView(swipeRefreshLayout, index, webView.getLayoutParams());

        parent.setFitsSystemWindows(false);
        parent.setPadding(0, 0, 0, 0);
        ViewCompat.setOnApplyWindowInsetsListener(parent, (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return WindowInsetsCompat.CONSUMED;
        });

        swipeRefreshLayout.setOnChildScrollUpCallback((p, child) ->
                pageScrolledDown || webView.getScrollY() > 0);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            webView.reload();
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> swipeRefreshLayout.setRefreshing(false), 1500);
        });
    }

    @Override
    public void onBackPressed() {
        WebView webView = this.bridge.getWebView();

        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }

        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(
                () -> doubleBackToExitPressedOnce = false, 2000);
    }
}