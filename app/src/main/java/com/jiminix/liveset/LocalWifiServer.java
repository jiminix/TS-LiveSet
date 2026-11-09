package com.jiminix.liveset;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Read-only local Wi-Fi viewer. No internet, cloud write, or playlist mutation. */
public final class LocalWifiServer {
    private static ServerSocket server;
    private static volatile boolean running;
    private static final int PORT=8765;
    private LocalWifiServer(){}
    public static synchronized boolean start(Context context){
        if(running)return true;
        try{
            server=new ServerSocket(PORT);
            running=true;
            Context app=context.getApplicationContext();
            Thread accept=new Thread(()->{
                while(running){
                    try{
                        Socket client=server.accept();
                        new Thread(()->serve(app,client),"LiveSet-Local-Client").start();
                    }catch(Exception e){if(!running)break;}
                }
            },"LiveSet-Local-Wifi");
            accept.setDaemon(true);
            accept.start();
            return true;
        }catch(Exception e){return false;}
    }
    public static synchronized void stop(){
        running=false;
        try{if(server!=null)server.close();}catch(Exception ignored){}
        server=null;
    }
    public static boolean isRunning(){return running;}
    private static JSONObject playlist(Context c,SetListModel sl)throws Exception{
        JSONObject o=new JSONObject();
        o.put("available",sl!=null);
        o.put("playlist_name",sl==null?"":sl.name);
        JSONArray songs=new JSONArray();
        if(sl!=null)for(String id:sl.songIds){
            Song s=AppStore.findSong(c,id);
            if(s==null)continue;
            JSONObject song=new JSONObject();
            song.put("id",s.id);
            song.put("title",s.title);
            song.put("artist",s.artist);
            song.put("bpm",s.bpm);
            song.put("mediaUrl",s.mediaUrl==null?"":s.mediaUrl);
            song.put("disabled",sl.disabledSongIds.contains(id));
            songs.put(song);
        }
        o.put("songs",songs);
        return o;
    }
    private static JSONObject snapshot(Context c)throws Exception{
        JSONObject result=playlist(c,AppStore.findSetlist(c,AppStore.getViewerSetlistId(c)));
        result.put("in_progress",playlist(c,AppStore.getOrCreateInProgressSetlist(c)));
        SetListModel fred=null;
        for(SetListModel sl:AppStore.loadSetlists(c)){
            if(sl.name!=null&&sl.name.trim().matches("(?i)^(2[.\\s-]*)?with\\s+fred$")){fred=sl;break;}
        }
        result.put("with_fred",playlist(c,fred));
        result.put("updatedAt",System.currentTimeMillis());
        return result;
    }
    private static void serve(Context c,Socket socket){
        try(Socket s=socket){
            s.setSoTimeout(3500);
            InputStream in=s.getInputStream();
            byte[] bytes=new byte[4096];
            int n=in.read(bytes);
            if(n<=0)return;
            String request=new String(bytes,0,n,StandardCharsets.UTF_8);
            String path=request.split("\\s+")[1];
            String body;String type;
            if(path.startsWith("/api/playlist")){
                body=snapshot(c).toString();
                type="application/json; charset=utf-8";
            }else if(path.equals("/")||path.startsWith("/index")){
                body=HTML;
                type="text/html; charset=utf-8";
            }else{
                body="Not found";
                type="text/plain; charset=utf-8";
            }
            byte[] payload=body.getBytes(StandardCharsets.UTF_8);
            String header="HTTP/1.1 200 OK\r\nContent-Type: "+type+
                "\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: "+
                payload.length+"\r\nConnection: close\r\n\r\n";
            OutputStream out=s.getOutputStream();
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(payload);
            out.flush();
        }catch(Exception ignored){}
    }
    private static final String HTML="<!doctype html><html lang='fr'><meta name='viewport' content='width=device-width,initial-scale=1'><meta charset='utf-8'><title>TS Playlist LOCAL</title>"+
        "<style>body{background:#10151a;color:white;font:16px Arial;margin:0}header{position:sticky;top:0;background:#202830;padding:8px;text-align:center}button{padding:8px;margin:3px;border:0;border-radius:5px;font-weight:bold}.yellow{background:#f7d129}.red{background:#c62828;color:white}#list{padding:8px}.song{padding:12px 4px;border-bottom:1px solid #35414a}footer{position:fixed;bottom:0;background:#202830;width:100%;text-align:center;font-size:12px;padding:8px}</style>"+
        "<header><b id='name'>TS PLAYLIST LOCAL</b><div><button onclick=\"selectList('main')\">PRINCIPAL</button><button class='red' onclick=\"selectList('in_progress')\">EN COURS</button><button class='yellow' onclick=\"selectList('with_fred')\">WITH FRED</button></div></header><main id='list'>Chargement...</main><footer>TS PLAYLIST · MODE LOCAL · <span id='status'>Connexion...</span></footer>"+
        "<script>let selected='main',signature='';function selectList(x){selected=x;signature='';refresh()}async function refresh(){try{const r=await fetch('/api/playlist',{cache:'no-store'});const data=await r.json();const list=selected==='main'?data:data[selected];const s=JSON.stringify(list);if(s!==signature){signature=s;document.getElementById('name').textContent=(list.playlist_name||selected).toUpperCase();const root=document.getElementById('list');root.replaceChildren();(list.songs||[]).forEach((song,i)=>{const row=document.createElement('div');row.className='song';row.textContent=String(i+1).padStart(2,'0')+'  '+song.title+(song.bpm?' · '+song.bpm+' BPM':'');if(song.disabled)row.style.opacity='.45';root.appendChild(row)});if(!list.available)root.textContent='Playlist indisponible';}document.getElementById('status').textContent='Connecté · actualisation 2 s'}catch(e){document.getElementById('status').textContent='Connexion perdue'}}refresh();setInterval(refresh,2000)</script></html>";
}
