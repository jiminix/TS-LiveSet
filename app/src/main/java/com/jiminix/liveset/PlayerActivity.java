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
        LinearLayout top=Ui.row(this); Button close=Ui.button(this,"‹"); TextView t=Ui.title(this,title==null?"Lecteur":title); Ui.compactHeaderTitle(t,this); Button reload=Ui.button(this,"↻"); Ui.compactHeaderButton(close,this,46); Ui.compactHeaderButton(reload,this,52); top.addView(close);top.addView(t);top.addView(reload);root.addView(top);
        web=new WebView(this); WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setMediaPlaybackRequiresUserGesture(false); s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true); web.setWebViewClient(new WebViewClient()); web.setWebChromeClient(new WebChromeClient()); web.setBackgroundColor(Color.BLACK);
        root.addView(web,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        LinearLayout controls=Ui.row(this);
        Button back10=Ui.button(this,"−10 s");
        Button pause=Ui.button(this,"⏯");
        Button forward10=Ui.button(this,"+10 s");
        Ui.weight(back10,1); Ui.weight(pause,1); Ui.weight(forward10,1);
        controls.addView(back10); controls.addView(pause); controls.addView(forward10);
        root.addView(controls);
        boolean youtube=youtubeId(url==null?"":url)!=null;
        controls.setVisibility(youtube?android.view.View.VISIBLE:android.view.View.GONE);
        back10.setOnClickListener(v->web.evaluateJavascript("seekRelative(-10)",null));
        forward10.setOnClickListener(v->web.evaluateJavascript("seekRelative(10)",null));
        pause.setOnClickListener(v->web.evaluateJavascript("togglePlayback()",null));
        Ui.applySafeArea(root); setContentView(root);
        close.setOnClickListener(v->finish()); reload.setOnClickListener(v->load(url)); load(url);
    }

    private void load(String url){
        if(url==null||url.trim().isEmpty())return; url=url.trim(); String id=youtubeId(url);
        if(id!=null){
            String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>"+
                "<style>html,body{margin:0;background:#000;height:100%;overflow:hidden}#player{width:100%;height:100%}</style></head>"+
                "<body><div id='player'></div><script src='https://www.youtube.com/iframe_api'></script><script>"+
                "var player=null;function onYouTubeIframeAPIReady(){player=new YT.Player('player',{height:'100%',width:'100%',videoId:'"+id+"',"+
                "playerVars:{autoplay:1,playsinline:1,controls:1,rel:0,enablejsapi:1,origin:'https://www.youtube.com'}});}"+
                "function seekRelative(delta){try{if(player&&player.getCurrentTime&&player.seekTo){"+
                "var t=player.getCurrentTime()+delta;var d=player.getDuration();player.seekTo(Math.max(0,d>0?Math.min(t,d):t),true);}}catch(e){}}"+
                "function togglePlayback(){try{if(!player||!player.getPlayerState)return;"+
                "if(player.getPlayerState()===1)player.pauseVideo();else player.playVideo();}catch(e){}}"+
                "</script></body></html>";
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
