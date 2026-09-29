#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: spectrachord-host-fix.py <project_dir>')

p = Path(sys.argv[1]) / 'app/src/main/java/com/sonolume/spectra/ui/Instruments.kt'
if not p.exists():
    raise SystemExit(f'missing {p}')

s = p.read_text(encoding='utf-8')

s = s.replace('import android.webkit.JavascriptInterface\n', 'import android.view.View\nimport android.webkit.JavascriptInterface\n', 1)
s = s.replace('import android.webkit.WebSettings\n', 'import android.webkit.WebChromeClient\nimport android.webkit.WebResourceRequest\nimport android.webkit.WebSettings\n', 1)
s = s.replace('import androidx.compose.ui.window.DialogProperties\n', 'import androidx.compose.ui.window.DialogProperties\nimport androidx.webkit.WebViewAssetLoader\n', 1)

old = '''                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            // Spectrachord intentionally makes labels extremely small when
                            // the 25x25 grid is fitted to a phone screen. Android WebView's
                            // normal minimum font size breaks that scaling unless lowered.
                            settings.minimumFontSize = 1
                            settings.minimumLogicalFontSize = 1
                            settings.textZoom = 100
                            setBackgroundColor(android.graphics.Color.rgb(8, 9, 13))
                            addJavascriptInterface(InstrumentNativeBridge(ctx.applicationContext), "InstrumentNative")
                            webViewClient = WebViewClient()
                            loadUrl("file:///android_asset/instruments/$name.html")
                        }
                    },
'''

new = '''                    factory = { ctx ->
                        // Use the same appassets HTTPS origin that the last known-good
                        // WebView build used. Spectrachord's fitted 25x25 grid behaved
                        // correctly there; the separate file:// host introduced different
                        // WebView layout/origin timing on some Android builds.
                        val assetLoader = WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                            .build()
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.minimumFontSize = 1
                            settings.minimumLogicalFontSize = 1
                            settings.textZoom = 100
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            setBackgroundColor(android.graphics.Color.rgb(8, 9, 13))
                            overScrollMode = View.OVER_SCROLL_NEVER
                            webChromeClient = WebChromeClient()
                            addJavascriptInterface(InstrumentNativeBridge(ctx.applicationContext), "InstrumentNative")
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                                    assetLoader.shouldInterceptRequest(request.url)

                                override fun onPageFinished(view: WebView, url: String) {
                                    super.onPageFinished(view, url)
                                    if (name == "spectrachord") {
                                        // The Compose dialog can finish measuring after the page's
                                        // first layout pass. Force Spectrachord to refit once the
                                        // WebView is actually attached and sized, then again after
                                        // the system WebView settles its viewport.
                                        view.post {
                                            view.evaluateJavascript(
                                                """
                                                (function(){
                                                  var g=document.getElementById('grid');
                                                  var w=document.getElementById('gridWrapper');
                                                  if(g){g.style.display='grid';g.style.visibility='visible';g.style.opacity='1';}
                                                  if(w){w.style.visibility='visible';w.style.opacity='1';}
                                                  function r(){try{if(window.requestRelayout)window.requestRelayout();}catch(e){}}
                                                  r(); setTimeout(r,120); setTimeout(r,450); setTimeout(r,1000);
                                                })();
                                                """.trimIndent(),
                                                null
                                            )
                                        }
                                    }
                                }
                            }
                            loadUrl("https://appassets.androidplatform.net/assets/instruments/$name.html")
                        }
                    },
'''

if old not in s:
    raise SystemExit('Instruments.kt WebView factory anchor not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('spectrachord host fix applied')
