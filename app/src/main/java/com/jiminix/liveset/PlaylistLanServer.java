package com.jiminix.liveset;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class PlaylistLanServer {
    public static final int HTTP_PORT=8787;
    public static final int DISCOVERY_PORT=8788;
    public static final String DISCOVER="TS_LIVESET_DISCOVER";
    public static final String HERE="TS_LIVESET_HERE";

    private static volatile boolean running=false;
    private static Context app;

    private PlaylistLanServer(){}

    public static synchronized void start(Context context){
        if(running)return;
        app=context.getApplicationContext();
        running=true;

        Thread http=new Thread(PlaylistLanServer::httpLoop,"TS-Playlist-HTTP");
        http.start();

        Thread discovery=new Thread(PlaylistLanServer::discoveryLoop,"TS-Playlist-Discovery");
        discovery.start();
    }

    private static void httpLoop(){
        try(ServerSocket server=new ServerSocket(HTTP_PORT)){
            server.setReuseAddress(true);
            while(running){
                try{
                    Socket socket=server.accept();
                    Thread t=new Thread(()->handleClient(socket),"TS-Playlist-Client");
                    t.start();
                }catch(Exception ignored){}
            }
        }catch(Exception ignored){}
    }

    private static void handleClient(Socket socket){
        try(Socket s=socket){
            s.setSoTimeout(3000);
            BufferedReader reader=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
            String request=reader.readLine();
            String path="/";
            if(request!=null){
                String[] parts=request.split(" ");
                if(parts.length>1)path=parts[1];
            }
            String line;
            while((line=reader.readLine())!=null && !line.isEmpty()){}

            String body;
            int code=200;
            if("/playlist".equals(path) || "/".equals(path)){
                body=selectedPlaylistJson().toString();
            }else if("/status".equals(path)){
                JSONObject o=new JSONObject();
                o.put("ok",true);
                o.put("service","TS Playlist Manager");
                body=o.toString();
            }else{
                code=404;
                JSONObject o=new JSONObject();
                o.put("ok",false);
                o.put("error","not_found");
                body=o.toString();
            }

            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8));
            writer.write("HTTP/1.1 "+code+(code==200?" OK":" Not Found")+"\r\n");
            writer.write("Content-Type: application/json; charset=utf-8\r\n");
            writer.write("Cache-Control: no-store\r\n");
            writer.write("Access-Control-Allow-Origin: *\r\n");
            writer.write("Content-Length: "+bytes.length+"\r\n");
            writer.write("Connection: close\r\n\r\n");
            writer.flush();
            s.getOutputStream().write(bytes);
            s.getOutputStream().flush();
        }catch(Exception ignored){}
    }

    private static JSONObject selectedPlaylistJson(){
        JSONObject out=new JSONObject();
        try{
            String id=AppStore.getViewerSetlistId(app);
            SetListModel list=(id==null || id.isEmpty())?null:AppStore.findSetlist(app,id);
            if(list==null){
                out.put("available",false);
                out.put("managerVersion","0.34");
                return out;
            }

            JSONArray songs=new JSONArray();
            for(String songId:list.songIds){
                Song song=AppStore.findSong(app,songId);
                if(song==null)continue;
                JSONObject o=new JSONObject();
                o.put("id",song.id);
                o.put("title",song.title);
                o.put("bpm",song.bpm);
                o.put("stageNum1",song.stageNum1);
                o.put("stageNum2",song.stageNum2);
                o.put("stageGuitar",song.stageGuitar);
                o.put("stageKeyboard",song.stageKeyboard);
                songs.put(o);
            }

            out.put("available",true);
            out.put("playlist_id",list.id);
            out.put("playlist_name",list.name);
            out.put("songs",songs);
            out.put("managerVersion","0.34");
        }catch(Exception ignored){
            try{out.put("available",false);}catch(Exception ignored2){}
        }
        return out;
    }

    private static void discoveryLoop(){
        byte[] buf=new byte[256];
        try(DatagramSocket socket=new DatagramSocket(DISCOVERY_PORT)){
            socket.setBroadcast(true);
            while(running){
                try{
                    DatagramPacket packet=new DatagramPacket(buf,buf.length);
                    socket.receive(packet);
                    String msg=new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.UTF_8).trim();
                    if(DISCOVER.equals(msg)){
                        String reply=HERE+":"+HTTP_PORT;
                        byte[] data=reply.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket response=new DatagramPacket(data,data.length,packet.getAddress(),packet.getPort());
                        socket.send(response);
                    }
                }catch(Exception ignored){}
            }
        }catch(Exception ignored){}
    }
}
