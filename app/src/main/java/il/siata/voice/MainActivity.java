package il.siata.voice;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.ContentValues;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.*;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.util.Base64;
import java.io.OutputStream;
import java.io.IOException;

public class MainActivity extends Activity {
  private WebView web;
  private ValueCallback<Uri[]> chooser;
  private final int FILE_REQUEST=200;
  private final String site="https://siata-voice-studio.a0548598728.workers.dev/";
  private final SaveBridge saving=new SaveBridge();
  @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    web=new WebView(this);setContentView(web);
    WebSettings settings=web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(true);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setMediaPlaybackRequiresUserGesture(false);
    web.addJavascriptInterface(saving,"SiataSave");
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onShowFileChooser(WebView w,ValueCallback<Uri[]> callback,FileChooserParams params){
        if(chooser!=null)chooser.onReceiveValue(null);
        chooser=callback;
        try{startActivityForResult(params.createIntent(),FILE_REQUEST);return true;}
        catch(ActivityNotFoundException e){chooser=null;return false;}
      }
    });
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
        Uri uri=request.getUrl();
        if("siata-voice-studio.a0548598728.workers.dev".equals(uri.getHost()) && "https".equals(uri.getScheme()))return false;
        if("http".equals(uri.getScheme())||"https".equals(uri.getScheme())){startActivity(new Intent(Intent.ACTION_VIEW,uri));return true;}
        return true;
      }
      @Override public void onPageFinished(WebView v,String url){
        if(url.startsWith(site))v.evaluateJavascript(DOWNLOAD_SCRIPT,null);
      }
    });
    web.setDownloadListener((url,userAgent,disposition,mime,length)->{
      if(url.startsWith("blob:"))return;
      if(!url.startsWith("https://"))return;
      android.app.DownloadManager dm=(android.app.DownloadManager)getSystemService(DOWNLOAD_SERVICE);
      android.app.DownloadManager.Request r=new android.app.DownloadManager.Request(Uri.parse(url));
      r.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      dm.enqueue(r);
    });
    web.loadUrl(site);
  }
  private static final String DOWNLOAD_SCRIPT =
    "(function(){if(window.siataDownloadReady)return;window.siataDownloadReady=true;"+
    "document.addEventListener('click',async function(e){"+
    "var a=e.target&&e.target.closest&&e.target.closest('a[download]');"+
    "if(!a||!a.href||!a.href.startsWith('blob:'))return;"+
    "e.preventDefault();e.stopImmediatePropagation();"+
    "try{var blob=await(await fetch(a.href)).blob();"+
    "SiataSave.begin(String(a.download||'siata-audio.wav'),String(blob.type||'audio/wav'));"+
    "for(var i=0;i<blob.size;i+=131072){"+
    "var ab=await blob.slice(i,Math.min(i+131072,blob.size)).arrayBuffer();"+
    "var b=new Uint8Array(ab),s='';for(var k=0;k<b.length;k++)s+=String.fromCharCode(b[k]);"+
    "SiataSave.append(btoa(s));"+
    "}SiataSave.finish();"+
    "}catch(err){SiataSave.fail(String(err))}"+
    "},true);})();";

  @Override protected void onActivityResult(int req,int result,Intent data){
    super.onActivityResult(req,result,data);
    if(req==FILE_REQUEST&&chooser!=null){
      chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));
      chooser=null;
    }
  }
  @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}

  public class SaveBridge {
    private OutputStream out;
    private Uri uri;
    @JavascriptInterface public synchronized void begin(String filename,String mime){
      try{
        if(out!=null){out.close();out=null;}
        ContentValues v=new ContentValues();
        filename=filename.replaceAll("[\\\\/:*?\\\"<>|]","_");
        v.put(MediaStore.Downloads.DISPLAY_NAME,filename);
        v.put(MediaStore.Downloads.MIME_TYPE,mime);
        v.put(MediaStore.Downloads.IS_PENDING,1);
        uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
        if(uri==null)throw new IOException("Downloads unavailable");
        out=getContentResolver().openOutputStream(uri);
        if(out==null)throw new IOException("Cannot write download");
      }catch(Exception e){fail(e.toString());}
    }
    @JavascriptInterface public synchronized void append(String base64) {
      try{if(out!=null)out.write(Base64.decode(base64,Base64.DEFAULT));}
      catch(Exception e){fail(e.toString());}
    }
    @JavascriptInterface public synchronized void finish(){
      try{
        if(out==null)return;
        out.close();out=null;
        ContentValues ready=new ContentValues();ready.put(MediaStore.Downloads.IS_PENDING,0);
        getContentResolver().update(uri,ready,null,null);
        runOnUiThread(()->Toast.makeText(MainActivity.this,"ההקלטה נשמרה בתיקיית ההורדות",Toast.LENGTH_LONG).show());
      }catch(Exception e){fail(e.toString());}
    }
    @JavascriptInterface public synchronized void fail(String message){
      try{if(out!=null)out.close();}catch(Exception ignored){}
      out=null;
      runOnUiThread(()->Toast.makeText(MainActivity.this,"תקלה בשמירת קובץ: "+message,Toast.LENGTH_LONG).show());
    }
  }
}