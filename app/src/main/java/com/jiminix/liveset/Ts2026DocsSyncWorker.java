package com.jiminix.liveset;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Persistent, opt-in reconciliation of the TS 2026 index only. */
public class Ts2026DocsSyncWorker extends Worker {
    private static final String UNIQUE="ts2026-docs-background";
    public Ts2026DocsSyncWorker(@NonNull Context context,@NonNull WorkerParameters params){
        super(context,params);
    }
    public static void configure(Context c,boolean enabled){
        WorkManager wm=WorkManager.getInstance(c);
        if(!enabled){wm.cancelUniqueWork(UNIQUE);return;}
        Constraints network=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest periodic=new PeriodicWorkRequest.Builder(
            Ts2026DocsSyncWorker.class,15,TimeUnit.MINUTES)
            .setConstraints(network).build();
        wm.enqueueUniquePeriodicWork(UNIQUE,ExistingPeriodicWorkPolicy.KEEP,periodic);
        enqueue(c);
    }
    public static void enqueue(Context c){
        SharedPreferences prefs=c.getSharedPreferences("ts2026_docs_sync",Context.MODE_PRIVATE);
        if(!prefs.getBoolean("auto_enabled",false))return;
        OneTimeWorkRequest job=new OneTimeWorkRequest.Builder(Ts2026DocsSyncWorker.class)
            .setConstraints(new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build();
        WorkManager.getInstance(c).enqueue(job);
    }
    private static String response(HttpURLConnection c)throws Exception{
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;
            while((n=in.read(b))!=-1)out.write(b,0,n);
            return out.toString("UTF-8");
        }
    }
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();
        SharedPreferences prefs=c.getSharedPreferences("ts2026_docs_sync",Context.MODE_PRIVATE);
        if(!prefs.getBoolean("auto_enabled",false))return Result.success();
        try{
            AuthorizationRequest auth=AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(
                    new Scope("https://www.googleapis.com/auth/documents"))).build();
            AuthorizationResult result=Tasks.await(
                Identity.getAuthorizationClient(c).authorize(auth),25,TimeUnit.SECONDS);
            if(result==null||result.hasResolution()||result.getAccessToken()==null){
                prefs.edit().putBoolean("pending",true).putString("last_error",
                    "Autorisation Google requise dans le Manager").apply();
                return Result.success();
            }
            String desired=snapshot(c);
            if(desired==null)return Result.success();
            updateDocument(result.getAccessToken(),desired);
            prefs.edit().putBoolean("pending",false).remove("last_error").apply();
            return Result.success();
        }catch(Exception e){
            prefs.edit().putBoolean("pending",true)
                .putString("last_error",e.getMessage()==null?"Erreur de synchronisation":e.getMessage())
                .apply();
            return Result.retry();
        }
    }
    private String snapshot(Context c)throws Exception{
        SetListModel playlist=null;
        for(SetListModel item:AppStore.loadSetlists(c))
            if(Ts2026GoogleDocsSource.isTs2026(item.name)){playlist=item;break;}
        if(playlist==null)throw new IOException("Playlist TS 2026 introuvable");
        if(playlist.songIds.isEmpty())throw new IOException("Playlist vide : synchro bloquée par sécurité");
        StringBuilder out=new StringBuilder();
        int i=0;
        for(String id:playlist.songIds){
            Song song=AppStore.findSong(c,id);
            if(song==null||song.title==null||song.title.trim().isEmpty())
                throw new IOException("Morceau sans titre");
            out.append(String.format(Locale.FRANCE,"%02d. %s",++i,song.title.trim()));
            if(song.bpm!=null&&!song.bpm.trim().isEmpty())out.append(" · ").append(song.bpm.trim());
            out.append("\n");
        }
        return out.toString();
    }
    private void updateDocument(String token,String desired)throws Exception{
        String url="https://docs.googleapis.com/v1/documents/"+
            Ts2026GoogleDocsSource.PREPARED_DOCUMENT_ID;
        HttpURLConnection get=(HttpURLConnection)new URL(url+"?includeTabsContent=true").openConnection();
        get.setConnectTimeout(12000);get.setReadTimeout(20000);
        get.setRequestProperty("Authorization","Bearer "+token);
        if(get.getResponseCode()!=200)throw new IOException("Lecture Google Docs HTTP "+get.getResponseCode());
        JSONObject doc=new JSONObject(response(get));get.disconnect();
        JSONArray tabs=doc.optJSONArray("tabs");
        JSONObject tab=tabs!=null&&tabs.length()>0?tabs.optJSONObject(0):null;
        JSONObject dt=tab==null?null:tab.optJSONObject("documentTab");
        JSONObject body=dt==null?doc.optJSONObject("body"):dt.optJSONObject("body");
        if(body==null)throw new IOException("Corps du document absent");
        JSONArray blocks=body.optJSONArray("content");
        if(blocks==null)throw new IOException("Paragraphes introuvables");
        JSONObject props=tab==null?null:tab.optJSONObject("tabProperties");
        String tabId=props==null?"":props.optString("tabId","");
        int header=-1,lyrics=-1,first=-1,last=-1,count=0;
        HashSet<Integer> numbers=new HashSet<>();
        StringBuilder existing=new StringBuilder();
        for(int i=0;i<blocks.length();i++){
            JSONObject block=blocks.optJSONObject(i);
            JSONObject paragraph=block==null?null:block.optJSONObject("paragraph");
            if(paragraph==null)continue;
            JSONArray elements=paragraph.optJSONArray("elements");
            if(elements==null)continue;
            StringBuilder line=new StringBuilder();
            for(int j=0;j<elements.length();j++){
                JSONObject element=elements.optJSONObject(j);
                JSONObject run=element==null?null:element.optJSONObject("textRun");
                if(run!=null)line.append(run.optString("content",""));
            }
            String value=line.toString().trim();
            if(value.toUpperCase(Locale.ROOT).contains("SOMMAIRE TS 2026"))header=i;
            if(value.toUpperCase(Locale.ROOT).contains("PAROLES ET NOTES")){lyrics=i;break;}
            if(header>=0&&i>header){
                java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                    "^(\\d{1,3})\\s*[.)-]\\s*(.+)$").matcher(value);
                if(m.matches()){
                    int n=Integer.parseInt(m.group(1));
                    if(n<1||n>100||!numbers.add(n))throw new IOException("Numérotation invalide");
                    if(first<0)first=i;
                    last=i;count++;
                    existing.append(line);
                }else if(!value.isEmpty()&&first>=0)
                    throw new IOException("Texte inattendu entre les titres");
            }
        }
        if(header<0||lyrics<0||first<0||last<first||count<1)
            throw new IOException("Sommaire non reconnu");
        for(int n=1;n<=count;n++)if(!numbers.contains(n))
            throw new IOException("Numérotation discontinue");
        if(existing.toString().trim().equals(desired.trim()))return;
        int start=blocks.getJSONObject(first).optInt("startIndex",-1);
        int end=blocks.getJSONObject(last).optInt("endIndex",-1);
        int lyricsStart=blocks.getJSONObject(lyrics).optInt("startIndex",-1);
        if(start<1||end<=start||lyricsStart<end)throw new IOException("Limites du sommaire invalides");
        String revision=doc.optString("revisionId","");
        if(revision.isEmpty())throw new IOException("Révision absente");
        JSONObject range=new JSONObject().put("startIndex",start).put("endIndex",end);
        JSONObject location=new JSONObject().put("index",start);
        if(!tabId.isEmpty()){range.put("tabId",tabId);location.put("tabId",tabId);}
        JSONArray requests=new JSONArray()
            .put(new JSONObject().put("deleteContentRange",
                new JSONObject().put("range",range)))
            .put(new JSONObject().put("insertText",new JSONObject()
                .put("location",location).put("text",desired)));
        JSONObject payload=new JSONObject().put("requests",requests)
            .put("writeControl",new JSONObject().put("requiredRevisionId",revision));
        HttpURLConnection post=(HttpURLConnection)new URL(url+":batchUpdate").openConnection();
        post.setRequestMethod("POST");post.setConnectTimeout(12000);post.setReadTimeout(20000);
        post.setDoOutput(true);
        post.setRequestProperty("Authorization","Bearer "+token);
        post.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        try(OutputStream out=post.getOutputStream())
            {out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
        int code=post.getResponseCode();post.disconnect();
        if(code<200||code>=300)throw new IOException("Écriture Google Docs HTTP "+code);
    }
}
