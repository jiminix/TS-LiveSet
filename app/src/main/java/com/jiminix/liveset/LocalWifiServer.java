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
    private static final java.util.concurrent.ConcurrentHashMap<String,Long> viewers=new java.util.concurrent.ConcurrentHashMap<>();
    private static final long VIEWER_TTL_MS=12000;
    private static int activeViewers(String clientId){
        long now=System.currentTimeMillis();
        viewers.entrySet().removeIf(e->now-e.getValue()>VIEWER_TTL_MS);
        if(clientId!=null && clientId.matches("[A-Za-z0-9_-]{8,100}"))viewers.put(clientId,now);
        return viewers.size();
    }
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
        viewers.clear();
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
            song.put("stageNum1",s.stageNum1);
            song.put("stageNum2",s.stageNum2);
            song.put("stageGuitar",s.stageGuitar);
            song.put("stageKeyboard",s.stageKeyboard);
            JSONArray items=new JSONArray();for(String x:s.medleyItems)items.put(x);
            song.put("medleyItems",items);
            JSONArray artists=new JSONArray();for(String x:s.medleyArtists)artists.put(x);
            song.put("medleyArtists",artists);
            songs.put(song);
        }
        o.put("songs",songs);
        return o;
    }
    private static JSONObject snapshot(Context c)throws Exception{
        String selectedId=AppStore.getViewerSetlistId(c);
        SetListModel selected=selectedId==null?null:AppStore.findSetlist(c,selectedId);
        JSONObject result=playlist(c,selected);
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
            String[] parts=request.split("\\s+");
            String path=parts.length>1?parts[1]:"/";
            String body;String type;
            if(path.startsWith("/api/viewers")){
                String clientId=null;
                int q=path.indexOf('?');
                if(q>=0){
                    for(String part:path.substring(q+1).split("&")){
                        if(part.startsWith("id=")){clientId=part.substring(3);break;}
                    }
                }
                JSONObject response=new JSONObject();
                response.put("connected",activeViewers(clientId));
                response.put("windowSeconds",12);
                body=response.toString();
                type="application/json; charset=utf-8";
            }else if(path.startsWith("/api/playlist")){
                body=snapshot(c).toString();
                type="application/json; charset=utf-8";
            }else if(path.equals("/")||path.startsWith("/index")){
                body=asset(c,"local-viewer.html");
                type="text/html; charset=utf-8";
            }else if(path.startsWith("/tequila-viewer.svg")){
                body=asset(c,"tequila-viewer.svg");
                type="image/svg+xml";
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
        }catch(Exception e){android.util.Log.e("LiveSetLocal","HTTP request failed",e);}
    }
    private static String asset(Context c,String name)throws Exception{
        try(InputStream stream=c.getAssets().open(name)){
            java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
            byte[] bytes=new byte[8192];int n;
            while((n=stream.read(bytes))!=-1)buffer.write(bytes,0,n);
            return buffer.toString("UTF-8");
        }
    }
}
