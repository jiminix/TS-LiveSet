package com.jiminix.liveset;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerActivity extends AppCompatActivity {
    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        String title=getIntent().getStringExtra("title"); String url=getIntent().getStringExtra("url");
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        LinearLayout top=Ui.row(this); Button close=Ui.button(this,"‹"); TextView t=Ui.title(this,title==null?"Lecteur":title); t.setTextSize(20); Ui.weight(t,1); Button reload=Ui.button(this,"↻"); top.addView(close);top.addView(t);top.addView(reload);root.addView(top);
        web=new WebView(this); WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setMediaPlaybackRequiresUserGesture(false); s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true); web.setWebViewClient(new WebViewClient()); web.setWebChromeClient(new WebChromeClient()); web.setBackgroundColor(Color.BLACK);
        root.addView(web,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1)); Ui.applySafeArea(root); setContentView(root);
        close.setOnClickListener(v->finish()); reload.setOnClickListener(v->load(url)); load(url);
    }

    private void load(String url){
        if(url==null||url.trim().isEmpty())return; url=url.trim(); String id=youtubeId(url);
        if(id!=null){
            String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'><style>html,body{margin:0;background:#000;height:100%;overflow:hidden}iframe{width:100%;height:100%;border:0}</style></head><body><iframe src='https://www.youtube.com/embed/"+id+"?autoplay=1&playsinline=1&rel=0' allow='autoplay; encrypted-media; picture-in-picture' allowfullscreen></iframe></body></html>";
            web.loadDataWithBaseURL("https://www.youtube.com",html,"text/html","UTF-8",null);
        } else {
            if(!url.startsWith("http://")&&!url.startsWith("https://"))url="https://"+url;
            web.loadUrl(url);
        }
    }

    private String youtubeId(String url){
        Pattern[] ps=new Pattern[]{
            Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})"),
            Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Pattern.compile("youtube\\.com/(?:shorts|embed)/([A-Za-z0-9_-]{11})")
        };
        for(Pattern p:ps){Matcher m=p.matcher(url);if(m.find())return m.group(1);} return null;
    }

    @Override public void onBackPressed(){ if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed(); }
    @Override protected void onDestroy(){ if(web!=null){web.stopLoading();web.destroy();}super.onDestroy(); }
}
