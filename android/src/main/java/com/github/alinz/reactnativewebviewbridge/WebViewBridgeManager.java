package com.github.alinz.reactnativewebviewbridge;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.views.webview.ReactWebViewManager;
import com.facebook.react.uimanager.annotations.ReactProp;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static okhttp3.internal.Util.UTF_8;

public class WebViewBridgeManager extends SimpleViewManager<WebViewBridgeManager.ReactWebView> {
    private static final String REACT_CLASS = "RCTWebViewBridge";

    public static final int COMMAND_SEND_TO_BRIDGE = 101;

    public final static String HEADER_CONTENT_TYPE = "content-type";

    private static final String MIME_TEXT_HTML = "text/html";
    private static final String MIME_UNKNOWN = "application/octet-stream";

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    public
    @Nullable
    Map<String, Integer> getCommandsMap() {
        Map<String, Integer> commandsMap = new HashMap<>();

        commandsMap.put("sendToBridge", COMMAND_SEND_TO_BRIDGE);

        return commandsMap;
    }

    @Override
    protected ReactWebView createViewInstance(ThemedReactContext reactContext) {
        ReactWebView webView;
        webView = new ReactWebView(reactContext);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(params);
        webView.addJavascriptInterface(new JavascriptBridge(webView), "WebViewBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new ReactWebViewClient());
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setJavaScriptEnabled(true);
        return webView;
    }

    @Override
    public void receiveCommand(ReactWebView root, int commandId, @Nullable ReadableArray args) {
        super.receiveCommand(root, commandId, args);

        switch (commandId) {
            case COMMAND_SEND_TO_BRIDGE:
                sendToBridge(root, args.getString(0));
                break;
            default:
                //do nothing!!!!
        }
    }

    private void sendToBridge(WebView view, String script) {
//        String script = "WebViewBridge.onMessage('" + message + "');";
//        String scrip2 = "WebViewBridge.send(\"aa\")";
        Log.d("console", script);
        WebViewBridgeManager.evaluateJavascript(view, script);
    }

    static private void evaluateJavascript(WebView root, String javascript) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            root.evaluateJavascript(javascript, null);
        } else {
            root.loadUrl("javascript:" + javascript);
        }
    }

    private void evaluateJS(WebView root, String javascript) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            root.evaluateJavascript(javascript, null);
        } else {
            root.loadUrl("javascript:" + javascript);
        }
    }

    @ReactProp(name = "injectedOnStartLoadingJavaScript")
    public void setInjectedOnStartLoadingJavaScript(WebView view, @Nullable String injectedJavaScript) {
        ((ReactWebView) view).setInjectedOnStartLoadingJavaScript(injectedJavaScript);
    }

    @ReactProp(name="source")
    public void loadUrl(WebView view, String url) {
        view.loadUrl(url);
    }

    @ReactProp(name="javaScriptEnabled")
    public void javaScriptEnabled(WebView view, Boolean enable) {
        view.getSettings().setJavaScriptEnabled(enable);
    }

    @ReactProp(name="injectedJavaScript")
    public void injectedJavaScript(WebView view, String js) {
        view.evaluateJavascript(js, null);
        Log.d("inject", js);
    }
//
//    @ReactProp(name = "allowFileAccessFromFileURLs")
//    public void setAllowFileAccessFromFileURLs(WebView root, boolean allows) {
//        root.getSettings().setAllowFileAccessFromFileURLs(allows);
//    }
//
//    @ReactProp(name = "allowUniversalAccessFromFileURLs")
//    public void setAllowUniversalAccessFromFileURLs(WebView root, boolean allows) {
//        root.getSettings().setAllowUniversalAccessFromFileURLs(allows);
//    }

    public static class ReactWebView extends WebView implements LifecycleEventListener {
        private @Nullable
        String injectedJS;
        private @Nullable
        String injectedOnStartLoadingJS;
        private boolean messagingEnabled = false;

        private class ReactWebViewBridge {
            ReactWebView mContext;

            ReactWebViewBridge(ReactWebView c) {
                mContext = c;
            }
        }

        /**
         * WebView must be created with an context of the current activity
         * <p>
         * Activity Context is required for creation of dialogs internally by WebView
         * Reactive Native needed for access to ReactNative internal system functionality
         */
        public ReactWebView(ThemedReactContext reactContext) {
            super(reactContext);
        }

        @Override
        public void onHostResume() {
            // do nothing
        }

        @Override
        public void onHostPause() {
            // do nothing
        }

        @Override
        public void onHostDestroy() {

        }

        public void setInjectedJavaScript(@Nullable String js) {
            injectedJS = js;
        }

        public void setInjectedOnStartLoadingJavaScript(@Nullable String js) {
            injectedOnStartLoadingJS = js;
        }

        public void callInjectedJavaScript() {
            if (getSettings().getJavaScriptEnabled() &&
                    injectedJS != null &&
                    !TextUtils.isEmpty(injectedJS)) {
                loadUrl("javascript:(function() {\n" + injectedJS + ";\n})();");
            }
        }
    }

    public static Boolean responseRequiresJSInjection(Response response) {
        // we don't want to inject JS into redirects
        if (response.isRedirect()) {
            Log.d("redirect", "true");
            return false;
        }

        // ...okhttp appends charset to content type sometimes, like "text/html; charset=UTF8"
        final String contentTypeAndCharset = response.header(HEADER_CONTENT_TYPE, MIME_UNKNOWN);
        // ...and we only want to inject it in to HTML, really
        return contentTypeAndCharset.startsWith(MIME_TEXT_HTML);
    }

    public static Boolean urlStringLooksInvalid(String urlString) {
        return urlString == null ||
                urlString.trim().equals("") ||
                !(urlString.startsWith("http") && !urlString.startsWith("www")) ||
                urlString.contains("|");
    }

    public WebResourceResponse shouldInterceptRequest(WebResourceRequest request, Boolean onlyMainFrame, ReactWebView webView) {
        Uri url = request.getUrl();
        String urlStr = url.toString();

        if (onlyMainFrame && !request.isForMainFrame()) {
            return null;
        }

        if (urlStringLooksInvalid(urlStr)) {
            Log.d("INVALID", urlStr);
            return null;
        }

        Log.d("URL", urlStr);

        try {
            Request req = new Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", "")
                    .build();

            OkHttpClient.Builder b = new OkHttpClient.Builder();
            OkHttpClient httpClient = b
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build();

            Response response = httpClient.newCall(req).execute();

            if (!responseRequiresJSInjection(response)) {
                Log.d("JSINJECT", "true");
                return null;
            }

            InputStream is = response.body().byteStream();
            MediaType contentType = response.body().contentType();
            Charset charset = contentType != null ? contentType.charset(UTF_8) : UTF_8;

            if (response.code() == HttpURLConnection.HTTP_OK) {
                is = new InputStreamWithInjectedJS(is, webView.injectedOnStartLoadingJS, charset);
            }
            return new WebResourceResponse("text/html", charset.name(), is);
        } catch (IOException e) {
            return null;
        }
    }

    private class ReactWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView webView, String url) {
            super.onPageFinished(webView, url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            WebResourceResponse response = WebViewBridgeManager.this.shouldInterceptRequest(request, true, (ReactWebView)view);
            if (response != null) {
                Log.d("GOLDEN", "shouldInterceptRequest / WebViewClient -> return intercept response");
                return response;
            }
            return super.shouldInterceptRequest(view, request);
        }
    }
}