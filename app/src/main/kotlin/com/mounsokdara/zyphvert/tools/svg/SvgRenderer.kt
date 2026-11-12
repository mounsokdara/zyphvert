package com.mounsokdara.zyphvert.tools.svg

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

class SvgSource(val text: String, val width: Float, val height: Float)

class RenderSpec(val width: Int, val height: Int, val background: Int)

/**
 * Renders SVG (including CSS animations, SMIL and scripts) with an offscreen WebView, fully offline.
 * A virtual clock is injected so every frame is deterministic: performance.now, Date.now,
 * requestAnimationFrame, setTimeout/setInterval, CSS/Web animations and SMIL all follow the frame time.
 */
object SvgRenderer {

    private const val CLOCK = """
(function(){
var T=0,id=1,raf=[],tm=[],t0=Date.now();
performance.now=function(){return T};
Date.now=function(){return t0+T};
window.requestAnimationFrame=function(f){raf.push([id,f]);return id++};
window.cancelAnimationFrame=function(i){raf=raf.filter(function(x){return x[0]!==i})};
window.setTimeout=function(f,d){var i=id++;tm.push({i:i,f:f,at:T+(+d||0),r:0});return i};
window.setInterval=function(f,d){var i=id++;d=Math.max(+d||0,1);tm.push({i:i,f:f,at:T+d,r:d});return i};
window.clearTimeout=window.clearInterval=function(i){tm=tm.filter(function(x){return x.i!==i})};
window.__step=function(ms){
 T=ms;var g=0;
 for(;;){
  var due=tm.filter(function(x){return x.at<=T}).sort(function(a,b){return a.at-b.at});
  if(!due.length||g++>500)break;
  var x=due[0];
  if(x.r)x.at+=x.r;else tm=tm.filter(function(y){return y!==x});
  try{x.f()}catch(e){}
 }
 var q=raf;raf=[];q.forEach(function(r){try{r[1](T)}catch(e){}});
 try{document.getAnimations().forEach(function(a){a.pause();a.currentTime=T})}catch(e){}
 try{var s=document.querySelector('svg');if(s&&s.pauseAnimations){s.pauseAnimations();s.setCurrentTime(T/1000)}}catch(e){}
 return 1;
};
window.__detect=function(){
 var m=0;
 try{document.getAnimations().forEach(function(a){var e=a.effect.getComputedTiming().endTime;if(isFinite(e))m=Math.max(m,e)})}catch(e){}
 document.querySelectorAll('animate,animateTransform,animateMotion,set').forEach(function(n){
  var d=parseFloat(n.getAttribute('dur'))||0,b=parseFloat(n.getAttribute('begin'))||0,rc=n.getAttribute('repeatCount');
  var k=(rc&&rc!=='indefinite')?(parseFloat(rc)||1):1;m=Math.max(m,(b+d*k)*1000)});
 return m;
};
window.__bake=function(){
 var s=document.querySelector('svg');if(!s)return '';
 var P=['fill','fill-opacity','fill-rule','stroke','stroke-width','stroke-opacity','stroke-linecap','stroke-linejoin','stroke-dasharray','stroke-dashoffset','opacity','display','visibility','stop-color','stop-opacity','font-size','font-family','font-weight','filter','clip-path','mask','mix-blend-mode'];
 var c=s.cloneNode(true),a=s.querySelectorAll('*'),b=c.querySelectorAll('*');
 for(var i=0;i<a.length;i++){
  var cs=getComputedStyle(a[i]),st='',tr=cs.getPropertyValue('transform');
  if(tr&&tr!=='none'){st+='transform:'+tr+';transform-origin:'+cs.getPropertyValue('transform-origin')+';transform-box:'+cs.getPropertyValue('transform-box')+';'}
  for(var j=0;j<P.length;j++){
   var v=cs.getPropertyValue(P[j]);
   if(!v||(v==='none'&&(P[j]==='filter'||P[j]==='clip-path'||P[j]==='mask'))||(P[j]==='mix-blend-mode'&&v==='normal'))continue;
   st+=P[j]+':'+v+';';
  }
  if(st)b[i].setAttribute('style',st);
 }
 Array.prototype.slice.call(c.querySelectorAll('script,style,animate,set,animateTransform,animateMotion')).forEach(function(n){n.remove()});
 if(!c.getAttribute('xmlns'))c.setAttribute('xmlns','http://www.w3.org/2000/svg');
 return new XMLSerializer().serializeToString(c);
};
})();
"""

    fun readText(ctx: Context, uri: Uri): String {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Couldn't open the SVG")
        val raw = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
        return String(raw, Charsets.UTF_8).removePrefix("\uFEFF")
    }

    fun load(ctx: Context, uri: Uri, scripts: Boolean): SvgSource {
        var s = readText(ctx, uri)
        s = s.replace(Regex("<\\?xml.*?\\?>", RegexOption.DOT_MATCHES_ALL), "")
        s = s.replace(Regex("<!DOCTYPE[^\\[>]*(\\[.*?\\])?\\s*>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        if (!scripts) {
            val ci = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            s = s.replace(Regex("<script\\b[^>]*/>", RegexOption.IGNORE_CASE), "")
            s = s.replace(Regex("<script\\b.*?</script\\s*>", ci), "")
            s = s.replace(Regex("\\son[a-zA-Z]+\\s*=\\s*(\"[^\"]*\"|'[^']*')"), "")
            s = s.replace(Regex("(href\\s*=\\s*[\"'])\\s*javascript:[^\"']*", RegexOption.IGNORE_CASE), "\$1#")
        }
        val tag = Regex("<svg\\b[^>]*>", RegexOption.IGNORE_CASE).find(s)?.value
            ?: throw IllegalArgumentException("This file isn't a valid SVG")
        fun attr(n: String) = Regex("\\s$n\\s*=\\s*[\"']([^\"']+)[\"']").find(tag)?.groupValues?.get(1)
        fun px(v: String?) = v?.takeIf { '%' !in it }?.let { Regex("[0-9]*\\.?[0-9]+").find(it)?.value?.toFloatOrNull() }
        val vb = attr("viewBox")?.trim()?.split(Regex("[\\s,]+"))?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 4 && it[2] > 0f && it[3] > 0f }
        val aw = px(attr("width")); val ah = px(attr("height"))
        val (w, h) = when {
            aw != null && ah != null -> aw to ah
            vb != null && aw != null -> aw to aw * vb[3] / vb[2]
            vb != null && ah != null -> ah * vb[2] / vb[3] to ah
            vb != null -> vb[2] to vb[3]
            aw != null -> aw to aw
            ah != null -> ah to ah
            else -> 512f to 512f
        }
        val fixed = if (vb == null)
            s.replaceFirst(tag, tag.replaceFirst(Regex("<svg", RegexOption.IGNORE_CASE), "<svg viewBox=\"0 0 $w $h\""))
        else s
        return SvgSource(fixed, max(w, 1f), max(h, 1f))
    }

    fun minify(s: String): String = s
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<metadata\\b.*?</metadata>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("\\s(inkscape|sodipodi):[a-zA-Z-]+=\"[^\"]*\""), "")
        .replace(Regex(">\\s+<"), "><")
        .trim()

    private fun page(svg: String) =
        "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data: blob:; style-src 'unsafe-inline'; font-src data:; media-src data:; script-src 'unsafe-inline' 'unsafe-eval'\">" +
            "<style>html,body{margin:0;padding:0;overflow:hidden;background:transparent}svg{position:absolute;left:0;top:0;width:100%;height:100%}</style>" +
            "<script>" + CLOCK + "</script></head><body>" + svg + "</body></html>"

    fun dims(src: SvgSource, widthOpt: Int, even: Boolean, cap: Float = 4096f): Pair<Int, Int> {
        var w = if (widthOpt > 0) widthOpt.toFloat() else src.width
        var h = w * src.height / src.width
        if (widthOpt <= 0 && max(w, h) < 512f) { val k = 512f / max(w, h); w *= k; h *= k }
        val m = max(w, h)
        if (m > cap) { val k = cap / m; w *= k; h *= k }
        var wi = w.roundToInt().coerceAtLeast(2); var hi = h.roundToInt().coerceAtLeast(2)
        if (even) { wi -= wi % 2; hi -= hi % 2 }
        return wi to hi
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(ctx: Context, spec: RenderSpec): WebView {
        runCatching { WebView.enableSlowWholeDocumentDraw() }
        val wv = WebView(ctx.applicationContext)
        wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.settings.javaScriptEnabled = true
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = false
        wv.settings.blockNetworkLoads = true
        wv.settings.domStorageEnabled = false
        wv.settings.javaScriptCanOpenWindowsAutomatically = false
        wv.measure(
            View.MeasureSpec.makeMeasureSpec(spec.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(spec.height, View.MeasureSpec.EXACTLY),
        )
        wv.layout(0, 0, spec.width, spec.height)
        return wv
    }

    class Session(private val wv: WebView, private val spec: RenderSpec) {
        suspend fun load(src: SvgSource) {
            val ok = withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine<Boolean> { c ->
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) { if (c.isActive) c.resume(true) }
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val u = request?.url?.toString().orEmpty()
                            return if (u.startsWith("data:") || u.startsWith("https://svg.local/")) null
                            else WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                    }
                    wv.loadDataWithBaseURL("https://svg.local/", SvgRenderer.page(src.text), "text/html", "utf-8", null)
                }
            }
            if (ok == null) throw IllegalStateException("The SVG took too long to load")
            delay(150)
        }

        private suspend fun js(code: String): String = suspendCancellableCoroutine { c ->
            wv.evaluateJavascript(code) { c.resume(it ?: "null") }
        }

        suspend fun detectMs(): Long = js("window.__detect()").trim('"').toDoubleOrNull()?.toLong() ?: 0L

        suspend fun frame(ms: Double): Bitmap {
            js("window.__step($ms)")
            withTimeoutOrNull(400) {
                suspendCancellableCoroutine<Unit> { c ->
                    wv.postVisualStateCallback(System.nanoTime(), object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) { if (c.isActive) c.resume(Unit) }
                    })
                }
            }
            delay(20)
            val bmp = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(spec.background)
            wv.draw(Canvas(bmp))
            return bmp
        }

        suspend fun bake(ms: Double): String {
            js("window.__step($ms)")
            val raw = js("window.__bake()")
            return JSONArray("[$raw]").getString(0)
        }
    }

    suspend fun <T> session(ctx: Context, src: SvgSource, spec: RenderSpec, block: suspend (Session) -> T): T =
        withContext(Dispatchers.Main) {
            val wv = create(ctx, spec)
            try {
                val s = Session(wv, spec)
                s.load(src)
                block(s)
            } finally {
                wv.stopLoading()
                wv.destroy()
            }
        }

    private fun compressFormat(fmt: String) = when (fmt) {
        "png" -> Bitmap.CompressFormat.PNG
        "jpg" -> Bitmap.CompressFormat.JPEG
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
        else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
    }

    private fun save(bmp: Bitmap, out: File, fmt: String, quality: Int) {
        out.parentFile?.mkdirs()
        out.outputStream().use { if (!bmp.compress(compressFormat(fmt), quality, it)) throw IllegalStateException("Couldn't save the image") }
    }

    suspend fun stillFile(ctx: Context, uri: Uri, out: File, fmt: String, quality: Int, widthOpt: Int, bg: Int, scripts: Boolean, atMs: Double) {
        val src = load(ctx, uri, scripts)
        val (w, h) = dims(src, widthOpt, false)
        val bmp = session(ctx, src, RenderSpec(w, h, bg)) { it.frame(atMs) }
        withContext(Dispatchers.IO) { save(bmp, out, fmt, quality); bmp.recycle() }
    }

    suspend fun bakeFile(ctx: Context, uri: Uri, out: File, widthOpt: Int, scripts: Boolean, atMs: Double) {
        val src = load(ctx, uri, scripts)
        val (w, h) = dims(src, widthOpt, false)
        val text = session(ctx, src, RenderSpec(w, h, Color.TRANSPARENT)) { it.bake(atMs) }
        if (text.isBlank()) throw IllegalStateException("Couldn't read the SVG")
        withContext(Dispatchers.IO) { out.parentFile?.mkdirs(); out.writeText(text) }
    }

    /** Writes f_00001.ext, f_00002.ext ... into [dir]. */
    suspend fun framesTo(
        ctx: Context, uri: Uri, dir: File, ext: String, widthOpt: Int, bg: Int, scripts: Boolean,
        fps: Int, durationSec: Int, quality: Int, even: Boolean, cap: Float,
    ) {
        val src = load(ctx, uri, scripts)
        val (w, h) = dims(src, widthOpt, even, cap)
        session(ctx, src, RenderSpec(w, h, bg)) { s ->
            val detected = s.detectMs()
            val ms = if (durationSec > 0) durationSec * 1000L else if (detected > 0) detected.coerceAtMost(60_000L) else 5_000L
            val n = ((ms / 1000.0) * fps).roundToInt().coerceIn(1, 3600)
            dir.mkdirs()
            for (i in 0 until n) {
                yield()
                val bmp = s.frame(i * 1000.0 / fps)
                withContext(Dispatchers.IO) {
                    save(bmp, File(dir, "f_%05d.%s".format(i + 1, ext)), ext, quality)
                    bmp.recycle()
                }
            }
        }
    }
}
