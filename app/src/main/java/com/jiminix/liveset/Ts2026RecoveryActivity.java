package com.jiminix.liveset;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import org.json.*;
import java.net.*;
import java.io.*;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.view.ViewGroup;
import android.widget.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Explicit, non-destructive import of missing TS 2026 titles from the Docs index. */
public class Ts2026RecoveryActivity extends Activity {
    private static final int AUTH_REQUEST=2907;
    private String playlistId;
    private LinearLayout results;
    private TextView status;
    private final List<String> missing = new ArrayList<>();
    private final List<CheckBox> boxes = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        playlistId=getIntent().getStringExtra("setlist_id");
        SetListModel list=AppStore.findSetlist(this,playlistId);
        if(list==null || !Ts2026GoogleDocsSource.isTs2026(list.name)){finish();return;}
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18,16,18,12);
        root.setBackgroundColor(Color.rgb(15,15,15));
        TextView title=new TextView(this);
        title.setText("Récupération des titres · TS 2026");
        title.setTextColor(Color.WHITE);title.setTextSize(20);
        root.addView(title);
        status=new TextView(this);status.setTextColor(Color.LTGRAY);
        status.setText("Comparaison avec l'index Google Docs…");
        root.addView(status);
        Button refresh=new Button(this);refresh.setText("Relire Google Docs");
        refresh.setOnClickListener(v->authorizeAndRead());root.addView(refresh);
        ScrollView scroll=new ScrollView(this);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button add=new Button(this);add.setText("Ajouter les titres cochés à TS 2026");
        add.setOnClickListener(v->confirmAdd());root.addView(add);
        setContentView(root);
        authorizeAndRead();
    }

    private static String plain(String html) {
        String s=html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>","");
        s=s.replaceAll("(?i)<br\\s*/?>","\\n").replaceAll("(?i)</(?:p|div|tr|li|h[1-6])>","\\n");
        return Html.fromHtml(s,Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00a0',' ');
    }
    private static String key(String s){
        String n=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","");
    }
    private static List<String> parseIndex(String text){
        // Only use the opening numbered index, never numbered lyrics sections.
        String[] lines=text.split("\\n");
        LinkedHashMap<String,String> found=new LinkedHashMap<>();
        boolean started=false;
        int idle=0;
        for(String raw:lines){
            String line=raw.trim().replaceAll("\\s+"," ");
            Matcher m=Pattern.compile("^(?:0?[1-9]|[12][0-9]|3[0-9])\\s*[.\\)\\-–:]\\s*(.{3,110})$").matcher(line);
            if(m.matches()){
                String name=m.group(1).trim().replaceAll("\\s+\\.{2,}.*$","").trim();
                if(!name.isEmpty() && !key(name).isEmpty()){
                    found.putIfAbsent(key(name),name);started=true;idle=0;
                }
            }else if(started && ++idle>18)break;
            if(found.size()>=60)break;
        }
        return new ArrayList<>(found.values());
    }
    private void authorizeAndRead(){
        status.setText("Autorisation Google pour lire les titres TS 2026…");
        AuthorizationRequest request=AuthorizationRequest.builder()
            .setRequestedScopes(Arrays.asList(new Scope("https://www.googleapis.com/auth/documents")))
            .build();
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener(this::handleAuth)
            .addOnFailureListener(e->status.setText("Connexion Google impossible : "+e.getMessage()));
    }
    private void handleAuth(AuthorizationResult result){
        if(result.hasResolution()){
            try{
                PendingIntent pending=result.getPendingIntent();
                startIntentSenderForResult(pending.getIntentSender(),AUTH_REQUEST,null,0,0,0);
            }catch(Exception e){status.setText("Autorisation : "+e.getMessage());}
        }else if(result.getAccessToken()!=null){
            readDocument(result.getAccessToken());
        }else status.setText("Autorisation Google sans jeton. Réessaie.");
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=AUTH_REQUEST)return;
        if(resultCode!=RESULT_OK){status.setText("Autorisation annulée.");return;}
        try{
            handleAuth(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data));
        }catch(Exception e){status.setText("Autorisation : "+e.getMessage());}
    }
    private static void collectText(Object node,StringBuilder out) throws JSONException {
        if(node instanceof JSONObject){
            JSONObject object=(JSONObject)node;
            JSONObject textRun=object.optJSONObject("textRun");
            if(textRun!=null)out.append(textRun.optString("content",""));
            JSONArray content=object.optJSONArray("content");
            if(content!=null)for(int i=0;i<content.length();i++)collectText(content.get(i),out);
            JSONObject paragraph=object.optJSONObject("paragraph");
            if(paragraph!=null)collectText(paragraph,out);
            JSONObject table=object.optJSONObject("table");
            if(table!=null)collectText(table,out);
            JSONArray rows=object.optJSONArray("tableRows");
            if(rows!=null)for(int i=0;i<rows.length();i++)collectText(rows.get(i),out);
            JSONArray cells=object.optJSONArray("tableCells");
            if(cells!=null)for(int i=0;i<cells.length();i++)collectText(cells.get(i),out);
        }
    }
    private void readDocument(String accessToken){
        status.setText("Lecture authentifiée de Google Docs…");
        new Thread(()->{
            HttpURLConnection connection=null;
            try{
                URL url=new URL("https://docs.googleapis.com/v1/documents/"+
                    Ts2026GoogleDocsSource.DOCUMENT_ID+"?includeTabsContent=true");
                connection=(HttpURLConnection)url.openConnection();
                connection.setConnectTimeout(12000);connection.setReadTimeout(20000);
                connection.setRequestProperty("Authorization","Bearer "+accessToken);
                int http=connection.getResponseCode();
                if(http!=200)throw new IOException("Google Docs API HTTP "+http);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream in=connection.getInputStream()){
                    byte[] buf=new byte[8192];int n;
                    while((n=in.read(buf))!=-1){
                        if(bytes.size()+n>8*1024*1024)throw new IOException("Document trop volumineux");
                        bytes.write(buf,0,n);
                    }
                }
                JSONObject doc=new JSONObject(bytes.toString("UTF-8"));
                StringBuilder text=new StringBuilder();
                JSONArray tabs=doc.optJSONArray("tabs");
                if(tabs!=null && tabs.length()>0){
                    for(int i=0;i<tabs.length();i++){
                        JSONObject tab=tabs.getJSONObject(i);
                        JSONObject documentTab=tab.optJSONObject("documentTab");
                        if(documentTab!=null)collectText(documentTab.optJSONObject("body"),text);
                    }
                }else collectText(doc.optJSONObject("body"),text);
                List<String> index=parseIndex(text.toString());
                if(index.isEmpty())throw new IOException("Index numéroté introuvable dans le document.");
                runOnUiThread(()->{if(!isFinishing())render(index,"Google Docs (lecture autorisée)");});
            }catch(Exception e){
                runOnUiThread(()->{if(!isFinishing())status.setText("Lecture impossible : "+e.getMessage());});
            }finally{if(connection!=null)connection.disconnect();}
        },"Ts2026AuthorizedRead").start();
    }
    private void render(List<String> index,String source){
        results.removeAllViews();boxes.clear();missing.clear();
        SetListModel list=AppStore.findSetlist(this,playlistId);
        if(list==null)return;
        Set<String> existing=new HashSet<>();
        for(String id:list.songIds){Song s=AppStore.findSong(this,id);if(s!=null)existing.add(key(s.title));}
        for(String title:index)if(!existing.contains(key(title)))missing.add(title);
        status.setText(source+" · "+index.size()+" titres détectés dans l'index · "+missing.size()+" à vérifier.\nVérifie les noms avant d'ajouter : les titres ne sont pas toujours reconnus automatiquement.");
        if(index.isEmpty()){
            TextView help=new TextView(this);help.setTextColor(Color.WHITE);
            help.setText("Index non reconnu automatiquement. Aucun titre ne sera ajouté. Vérifie que la première page de Google Docs contient une liste numérotée (ex. 01. TITRE).");
            results.addView(help);
        }
        for(String title:missing){
            CheckBox cb=new CheckBox(this);cb.setText(title);cb.setTextColor(Color.WHITE);
            cb.setChecked(false);results.addView(cb);boxes.add(cb);
        }
    }
    private void confirmAdd(){
        List<String> chosen=new ArrayList<>();
        for(int i=0;i<boxes.size();i++)if(boxes.get(i).isChecked())chosen.add(missing.get(i));
        if(chosen.isEmpty()){Toast.makeText(this,"Coche les titres à récupérer.",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle("Ajouter "+chosen.size()+" titre(s) ?")
            .setMessage("Les nouveaux morceaux seront ajoutés à la fin de TS 2026. L'ordre actuel et les paroles existantes ne seront pas modifiés. Les paroles des nouveaux titres seront à associer ensuite.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Ajouter",(d,w)->{
                SetListModel list=AppStore.findSetlist(this,playlistId);
                if(list==null)return;
                AppStore.recordUndoSnapshot(this,"Récupération titres Google Docs TS 2026");
                List<Song> library=AppStore.loadSongs(this);
                Set<String> current=new HashSet<>();
                for(String id:list.songIds){Song s=AppStore.findSong(this,id);if(s!=null)current.add(key(s.title));}
                int count=0;
                for(String name:chosen){
                    if(current.contains(key(name)))continue;
                    Song match=null;
                    for(Song song:library)if(key(song.title).equals(key(name))){match=song;break;}
                    if(match==null){match=new Song();match.title=name;library.add(match);}
                    if(!list.songIds.contains(match.id)){list.songIds.add(match.id);count++;}
                    current.add(key(name));
                }
                AppStore.saveSongs(this,library);
                AppStore.upsertSetlist(this,list);
                Toast.makeText(this,count+" titre(s) ajoutés à TS 2026",Toast.LENGTH_LONG).show();
                authorizeAndRead();
            }).show();
    }
}
