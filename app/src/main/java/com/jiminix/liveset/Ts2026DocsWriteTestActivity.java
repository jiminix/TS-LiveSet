package com.jiminix.liveset;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;
import android.view.ViewGroup;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Explicit OAuth authorization + append-only write probe on a USER-SUPPLIED COPY.
 * Never targets the source document; no automatic sync, reordering or deletion.
 */
public class Ts2026DocsWriteTestActivity extends Activity {
    private static final int AUTH_REQUEST=2906;
    private EditText copyId;
    private TextView status;
    private String token;
    private String pendingSongId;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        pendingSongId=getIntent().getStringExtra("sync_song_id");
        LinearLayout layout=new LinearLayout(this);
        layout.setPadding(24,24,24,24);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(15,15,15));
        TextView heading=new TextView(this);
        heading.setText("Google Docs · connexion et test sur COPIE");
        heading.setTextSize(20);heading.setTextColor(Color.WHITE);layout.addView(heading);
        TextView explanation=new TextView(this);
        explanation.setText("1. Crée une copie de ton Google Docs original dans Google Drive.\n2. Colle l'identifiant de cette COPIE ci-dessous.\n3. Autorise l'accès Google.\n4. Confirme l'ajout d'une ligne de test à la fin de la copie.\n\nAucune modification automatique de la playlist ni du document original.");
        explanation.setTextColor(Color.LTGRAY);layout.addView(explanation);
        copyId=new EditText(this);copyId.setSingleLine(true);copyId.setTextColor(Color.WHITE);
        copyId.setHintTextColor(Color.GRAY);copyId.setHint("Identifiant ou lien Google Docs de la copie");
        copyId.setText(getPreferences(MODE_PRIVATE).getString("test_copy_id",""));
        layout.addView(copyId,new LinearLayout.LayoutParams(-1,-2));
        Button authorize=new Button(this);authorize.setText("Autoriser Google Docs");
        authorize.setOnClickListener(v->authorize());layout.addView(authorize);
        Button write=new Button(this);write.setText("Écrire une ligne TEST dans la copie");
        write.setOnClickListener(v->confirmWrite());layout.addView(write);
        Button index=new Button(this);
        index.setText("Exporter la liste TS 2026 vers la COPIE");
        index.setOnClickListener(v->confirmIndexExport());
        layout.addView(index);
        Button names=new Button(this);
        names.setText("Prévisualiser les noms Manager → COPIE");
        names.setOnClickListener(v->previewManagerNames());
        layout.addView(names);
        Button lyrics=new Button(this);
        lyrics.setText("Tester une nouvelle section de paroles → COPIE");
        lyrics.setOnClickListener(v->chooseNewLyricsSection());
        layout.addView(lyrics);
        Button missingTwo=new Button(this);
        missingTwo.setText("Préparer SMOOTH OPERATOR + IS IT A CRIME → COPIE");
        missingTwo.setOnClickListener(v->confirmMissingTwo());
        layout.addView(missingTwo);
        if(pendingSongId!=null){
            Button retryIndex=new Button(this);
            retryIndex.setText("Réessayer le sommaire uniquement → COPIE");
            retryIndex.setOnClickListener(v->{
                if(!validCopy())return;
                if(token==null||token.isEmpty()){
                    status.setText("Autorise Google Docs avant de réessayer.");return;
                }
                Song pending=AppStore.findSong(this,pendingSongId);
                if(pending!=null)previewIndexInsertion(id(),token,pending);
            });
            layout.addView(retryIndex);
        }
        status=new TextView(this);status.setTextColor(Color.WHITE);
        status.setText("Non connecté. La connexion nécessite la configuration OAuth Android du projet dans Google Cloud.");
        layout.addView(status);
        setContentView(layout);
        if(pendingSongId!=null){
            status.setText("Morceau validé dans TS 2026. Autorise Google Docs puis confirme l’ajout de sa section dans la COPIE.");
        }
    }

    private void confirmMissingTwo(){
        if(!validCopy())return;
        if(token==null||token.isEmpty()){
            status.setText("Autorise Google Docs avant le test.");return;
        }
        Song smooth=null,crime=null;
        for(Song song:AppStore.loadSongs(this)){
            String k=normalized(song.title);
            if(k.equals("smoothoperator"))smooth=song;
            if(k.equals("isitacrime"))crime=song;
        }
        if(smooth==null||crime==null){
            status.setText("Les deux morceaux doivent exister dans la bibliothèque Manager.");return;
        }
        final Song first=smooth,second=crime;
        final String destination=id(),accessToken=token;
        String preview=preparedSongSection(first)+"\n"+preparedSongSection(second);
        new AlertDialog.Builder(this)
            .setTitle("Préparer 2 morceaux dans une COPIE")
            .setMessage("Ajout de deux sections de test à la FIN d'une copie, sans changer le sommaire ni les 32 titres existants.\n\n"+
                "Les paroles sont reprises uniquement si elles sont déjà enregistrées dans le Manager.\n\n"+
                preview.substring(0,Math.min(preview.length(),2200))+
                "\n\nAucun changement dans le nouveau document officiel.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Ajouter à la COPIE",(d,w)->appendMissingTwo(destination,accessToken,first,second))
            .show();
    }
    private static String preparedSongSection(Song song){
        StringBuilder text=new StringBuilder();
        text.append("\n=== TS2026-SONG:").append(normalized(song.title)).append(" ===\n");
        text.append(song.title.trim());
        if(song.bpm!=null&&!song.bpm.trim().isEmpty())text.append(" · ").append(song.bpm.trim()).append(" BPM");
        if(song.stageGuitar)text.append(" 🎸");
        if(song.stageKeyboard)text.append(" 🎹");
        text.append("\n");
        if(song.lyrics!=null&&!song.lyrics.trim().isEmpty())text.append(song.lyrics.trim()).append("\n");
        else text.append("[Paroles non renseignées dans le Manager]\n");
        text.append("=== FIN TS2026-SONG ===\n");
        return text.toString();
    }
    private void appendMissingTwo(String destination,String accessToken,Song first,Song second){
        status.setText("Vérification de la COPIE avant écriture…");
        new Thread(()->{
            HttpURLConnection get=null,post=null;
            try{
                if(isProtectedDocument(destination))throw new IllegalArgumentException("Document protégé");
                get=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    destination+"?includeTabsContent=true").openConnection();
                get.setConnectTimeout(12000);get.setReadTimeout(20000);
                get.setRequestProperty("Authorization","Bearer "+accessToken);
                if(get.getResponseCode()!=200)throw new java.io.IOException("Lecture HTTP "+get.getResponseCode());
                JSONObject doc=new JSONObject(readResponse(get));
                String content=doc.toString();
                if(!content.toUpperCase(Locale.ROOT).contains("TS 2026"))
                    throw new java.io.IOException("Copie non reconnue comme document TS 2026");
                for(Song song:new Song[]{first,second}){
                    if(content.contains("TS2026-SONG:"+normalized(song.title)))
                        throw new java.io.IOException("Section déjà présente : "+song.title+" (doublon évité)");
                }
                String revision=doc.optString("revisionId","");
                if(revision.isEmpty())throw new java.io.IOException("Révision Google Docs absente");
                JSONObject insert=new JSONObject().put("endOfSegmentLocation",new JSONObject())
                    .put("text",preparedSongSection(first)+preparedSongSection(second));
                JSONObject body=new JSONObject()
                    .put("requests",new JSONArray().put(new JSONObject().put("insertText",insert)))
                    .put("writeControl",new JSONObject().put("requiredRevisionId",revision));
                post=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    destination+":batchUpdate").openConnection();
                post.setRequestMethod("POST");post.setConnectTimeout(12000);post.setReadTimeout(20000);
                post.setDoOutput(true);
                post.setRequestProperty("Authorization","Bearer "+accessToken);
                post.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try(OutputStream out=post.getOutputStream()){
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                if(post.getResponseCode()<200||post.getResponseCode()>=300)
                    throw new java.io.IOException("Écriture refusée HTTP "+post.getResponseCode());
                runOnUiThread(()->status.setText("Deux sections ajoutées à la COPIE. Sommaire original inchangé."));
            }catch(Exception e){
                runOnUiThread(()->status.setText("Aucune écriture confirmée : "+e.getMessage()));
            }finally{
                if(get!=null)get.disconnect();
                if(post!=null)post.disconnect();
            }
        },"Ts2026MissingTwoCopy").start();
    }

    private String id(){
        String value=copyId.getText().toString().trim();
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("/document/d/([a-zA-Z0-9_-]+)").matcher(value);
        if(m.find())value=m.group(1);
        return value;
    }
    private static boolean isProtectedDocument(String id){
        return Ts2026GoogleDocsSource.DOCUMENT_ID.equals(id) ||
            Ts2026GoogleDocsSource.PREPARED_DOCUMENT_ID.equals(id);
    }
    private boolean validCopy(){
        String id=id();
        if(isProtectedDocument(id)){
            status.setText("REFUS : il s'agit du document ORIGINAL. Crée une copie.");return false;
        }
        if(!id.matches("[a-zA-Z0-9_-]{20,}")){
            status.setText("Indique le lien ou l'identifiant valide de la COPIE.");return false;
        }
        return true;
    }
    private void authorize(){
        status.setText("Ouverture de l'autorisation Google…");
        AuthorizationRequest request=AuthorizationRequest.builder()
            .setRequestedScopes(Arrays.asList(
                new Scope("https://www.googleapis.com/auth/documents")))
            .build();
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener(result->handleAuthorization(result))
            .addOnFailureListener(e->status.setText("Autorisation impossible : "+e.getMessage()+
                "\nVérifie la configuration du client OAuth Android (package et empreinte SHA-1) dans Google Cloud."));
    }
    private void handleAuthorization(AuthorizationResult result){
        if(result.hasResolution()){
            try{
                PendingIntent pending=result.getPendingIntent();
                startIntentSenderForResult(pending.getIntentSender(),AUTH_REQUEST,null,0,0,0);
            }catch(Exception e){status.setText("Connexion Google : "+e.getMessage());}
        }else{
            token=result.getAccessToken();
            status.setText(token==null?"Jeton Google absent.":"Google Docs autorisé. Choisis une COPIE pour le test.");
            if(token!=null && pendingSongId!=null)confirmPendingSong();
        }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=AUTH_REQUEST)return;
        if(resultCode!=RESULT_OK){status.setText("Autorisation annulée.");return;}
        try{
            AuthorizationResult result=Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);
            handleAuthorization(result);
        }catch(Exception e){status.setText("Autorisation : "+e.getMessage());}
    }
    private void confirmWrite(){
        if(!validCopy())return;
        if(token==null||token.isEmpty()){
            status.setText("Autorise Google Docs avant de lancer le test.");return;
        }
        String target=id();
        new AlertDialog.Builder(this).setTitle("Écriture dans la COPIE seulement")
            .setMessage("Ajouter une ligne « TEST SYNCHRO TS 2026 » à la fin du document de copie ?\n\nIdentifiant : "+target+"\n\nL'original ne sera pas modifié.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Écrire le test",(d,w)->writeProbe(target,token)).show();
    }
    private void writeProbe(String target,String accessToken){
        status.setText("Écriture de test en cours…");
        new Thread(()->{
            HttpURLConnection conn=null;
            try{
                // Recheck destination in the worker, too.
                if(isProtectedDocument(target))
                    throw new IllegalArgumentException("Document original interdit");
                URL url=new URL("https://docs.googleapis.com/v1/documents/"+target+":batchUpdate");
                conn=(HttpURLConnection)url.openConnection();
                conn.setRequestMethod("POST");conn.setConnectTimeout(12000);conn.setReadTimeout(20000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization","Bearer "+accessToken);
                conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                JSONObject insert=new JSONObject();
                insert.put("endOfSegmentLocation",new JSONObject());
                insert.put("text","\\nTEST SYNCHRO TS 2026 — vérification d'écriture depuis Manager\\n");
                JSONObject request=new JSONObject().put("insertText",insert);
                JSONObject body=new JSONObject().put("requests",new JSONArray().put(request));
                try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
                int code=conn.getResponseCode();
                if(code<200||code>=300)throw new java.io.IOException("Google Docs HTTP "+code+
                    " (vérifie le partage de la copie, les API Google et l'autorisation)");
                runOnUiThread(()->status.setText("TEST RÉUSSI : ligne ajoutée à la COPIE uniquement."));
            }catch(Exception e){
                runOnUiThread(()->status.setText("Écriture non effectuée : "+e.getMessage()));
            }finally{if(conn!=null)conn.disconnect();}
        },"DocsCopyWriteProbe").start();
    }
    /**
     * Safe first phase: append one clearly delimited playlist index to a COPY.
     * Does not touch the original index or rich-text lyrics.
     */
    private void confirmIndexExport(){
        if(!validCopy())return;
        if(token==null||token.isEmpty()){
            status.setText("Autorise Google Docs avant l'export.");return;
        }
        SetListModel list=null;
        for(SetListModel candidate:AppStore.loadSetlists(this)){
            if(Ts2026GoogleDocsSource.isTs2026(candidate.name)){list=candidate;break;}
        }
        if(list==null){status.setText("Playlist TS 2026 introuvable.");return;}
        StringBuilder titles=new StringBuilder();
        int number=0;
        for(String songId:list.songIds){
            Song song=AppStore.findSong(this,songId);
            if(song==null || song.title==null || song.title.trim().isEmpty())continue;
            titles.append(String.format(java.util.Locale.FRANCE,"%02d. %s\\n",++number,song.title.trim()));
        }
        if(number==0){status.setText("La playlist TS 2026 est vide.");return;}
        final String destination=id();
        final String snapshot=titles.toString();
        new AlertDialog.Builder(this)
            .setTitle("Exporter "+number+" titres dans la COPIE ?")
            .setMessage("Une section distincte sera ajoutée à la fin de la COPIE. Les paroles et la liste originale ne seront pas modifiées.\\n\\n"+snapshot)
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Exporter",(d,w)->exportIndex(destination,token,snapshot))
            .show();
    }

    private static String readResponse(HttpURLConnection connection) throws Exception{
        try(InputStream in=connection.getInputStream();
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))!=-1){
                if(out.size()+n>6*1024*1024)throw new java.io.IOException("Document trop volumineux");
                out.write(buf,0,n);
            }
            return out.toString("UTF-8");
        }
    }

    private void exportIndex(String destination,String accessToken,String snapshot){
        status.setText("Vérification de la COPIE…");
        new Thread(()->{
            HttpURLConnection get=null,post=null;
            try{
                if(isProtectedDocument(destination))
                    throw new IllegalArgumentException("Document original interdit");
                get=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+destination).openConnection();
                get.setConnectTimeout(12000);get.setReadTimeout(20000);
                get.setRequestProperty("Authorization","Bearer "+accessToken);
                int code=get.getResponseCode();
                if(code!=200)throw new java.io.IOException("Lecture Google Docs HTTP "+code);
                JSONObject doc=new JSONObject(readResponse(get));
                String marker="=== TS 2026 / INDEX MANAGER (TEST) ===";
                if(doc.toString().contains(marker))
                    throw new java.io.IOException("Un index de test existe déjà dans cette copie. Export annulé pour éviter les doublons.");
                String revision=doc.optString("revisionId","");
                if(revision.isEmpty())throw new java.io.IOException("Révision du document indisponible : export annulé.");
                String text="\\n"+marker+"\\n"+snapshot+"=== FIN INDEX MANAGER (TEST) ===\\n";
                JSONObject insert=new JSONObject()
                    .put("endOfSegmentLocation",new JSONObject())
                    .put("text",text);
                JSONObject body=new JSONObject()
                    .put("requests",new JSONArray().put(new JSONObject().put("insertText",insert)))
                    .put("writeControl",new JSONObject().put("requiredRevisionId",revision));
                post=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+destination+":batchUpdate").openConnection();
                post.setRequestMethod("POST");post.setConnectTimeout(12000);post.setReadTimeout(20000);
                post.setDoOutput(true);
                post.setRequestProperty("Authorization","Bearer "+accessToken);
                post.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try(OutputStream out=post.getOutputStream()){
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int writeCode=post.getResponseCode();
                if(writeCode<200||writeCode>=300)
                    throw new java.io.IOException("Export refusé HTTP "+writeCode+
                        " (document modifié entre-temps ou autorisation expirée)");
                runOnUiThread(()->status.setText("INDEX EXPORTÉ dans la COPIE. Paroles et mise en page originales conservées."));
            }catch(Exception e){
                runOnUiThread(()->status.setText("Export non confirmé : "+e.getMessage()));
            }finally{
                if(get!=null)get.disconnect();
                if(post!=null)post.disconnect();
            }
        },"Ts2026IndexExport").start();
    }

    private static final class Rename {
        final int start,end;
        final String from,to;
        Rename(int start,int end,String from,String to){
            this.start=start;this.end=end;this.from=from;this.to=to;
        }
    }
    private static String normalized(String title){
        String value=java.text.Normalizer.normalize(title,java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","");
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","");
    }
    private static String alias(String title){
        String key=normalized(title);
        if(key.equals("alalalalong")||key.equals("sweatalalalalalong"))return "sweatalalalalalong";
        if(key.equals("loireetcher")||key.equals("leloiretcher"))return "leloiretcher";
        if(key.equals("whenyourheartis")||key.equals("whenyourheartisweak"))return "whenyourheartisweak";
        if(key.equals("shouldistay")||key.equals("shouldistayorshouldigo"))return "shouldistayorshouldigo";
        if(key.equals("meddleycine")||key.equals("medleycine"))return "medleycine";
        if(key.equals("dontyouforget")||key.equals("dontyouforgetaboutme"))return "dontyouforgetaboutme";
        return key;
    }
    private void previewManagerNames(){
        if(!validCopy())return;
        if(token==null||token.isEmpty()){
            status.setText("Autorise Google Docs d'abord.");return;
        }
        SetListModel list=null;
        for(SetListModel item:AppStore.loadSetlists(this)){
            if(Ts2026GoogleDocsSource.isTs2026(item.name)){list=item;break;}
        }
        if(list==null){status.setText("TS 2026 introuvable.");return;}
        final java.util.Map<String,String> titles=new java.util.HashMap<>();
        for(String songId:list.songIds){
            Song song=AppStore.findSong(this,songId);
            if(song!=null)titles.put(alias(song.title),song.title);
        }
        String destination=id(),accessToken=token;
        status.setText("Analyse de la COPIE, sans modification…");
        new Thread(()->{
            HttpURLConnection get=null;
            try{
                get=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    destination+"?includeTabsContent=true").openConnection();
                get.setConnectTimeout(12000);get.setReadTimeout(20000);
                get.setRequestProperty("Authorization","Bearer "+accessToken);
                if(get.getResponseCode()!=200)throw new java.io.IOException("Lecture refusée HTTP "+get.getResponseCode());
                JSONObject doc=new JSONObject(readResponse(get));
                String revision=doc.optString("revisionId","");
                if(revision.isEmpty())throw new java.io.IOException("Révision Google Docs manquante");
                JSONArray tabs=doc.optJSONArray("tabs");
                JSONObject body=null;
                if(tabs!=null && tabs.length()>0){
                    JSONObject tab=tabs.optJSONObject(0);
                    JSONObject dt=tab==null?null:tab.optJSONObject("documentTab");
                    body=dt==null?null:dt.optJSONObject("body");
                }else body=doc.optJSONObject("body");
                List<Rename> changes=collectRenameCandidates(body,titles);
                runOnUiThread(()->{
                    if(changes.isEmpty()){
                        status.setText("Aucun nom à harmoniser dans les cellules TS 2026 de la COPIE.");
                        return;
                    }
                    StringBuilder preview=new StringBuilder();
                    for(Rename c:changes)preview.append(c.from).append(" → ").append(c.to).append("\n");
                    new AlertDialog.Builder(this)
                        .setTitle("Modifier "+changes.size()+" nom(s) dans la COPIE ?")
                        .setMessage(preview.toString()+"\nSeuls les noms des cellules du sommaire TS 2026 seront remplacés. BPM, icônes, paroles et document ORIGINAL préservés.")
                        .setNegativeButton("Annuler",null)
                        .setPositiveButton("Appliquer à la COPIE",(d,w)->applyRenames(destination,accessToken,revision,changes))
                        .show();
                });
            }catch(Exception e){
                runOnUiThread(()->status.setText("Prévisualisation impossible : "+e.getMessage()));
            }finally{if(get!=null)get.disconnect();}
        },"DocsCopyNamePreview").start();
    }
    private static List<Rename> collectRenameCandidates(JSONObject body,java.util.Map<String,String> titles) throws Exception{
        List<Rename> changes=new ArrayList<>();
        if(body==null)return changes;
        JSONArray blocks=body.optJSONArray("content");
        if(blocks==null)return changes;
        for(int i=0;i<Math.min(blocks.length(),8);i++){
            JSONObject block=blocks.optJSONObject(i);
            JSONObject table=block==null?null:block.optJSONObject("table");
            JSONArray rows=table==null?null:table.optJSONArray("tableRows");
            if(rows==null||rows.length()<2)continue;
            JSONArray header=rows.optJSONObject(0).optJSONArray("tableCells");
            if(header==null||header.length()<2)continue;
            StringBuilder headerText=new StringBuilder();
            gatherText(header.optJSONObject(0),headerText);
            if(!headerText.toString().contains("PLAYLIST 2026"))continue;
            JSONArray cells=rows.optJSONObject(1).optJSONArray("tableCells");
            if(cells==null||cells.length()<2)return changes;
            for(int col=0;col<2;col++){
                JSONObject cell=cells.optJSONObject(col);
                JSONArray content=cell==null?null:cell.optJSONArray("content");
                if(content==null)continue;
                for(int j=0;j<content.length();j++){
                    JSONObject paragraph=content.optJSONObject(j);
                    JSONObject p=paragraph==null?null:paragraph.optJSONObject("paragraph");
                    JSONArray elements=p==null?null:p.optJSONArray("elements");
                    if(elements==null)continue;
                    for(int k=0;k<elements.length();k++){
                        JSONObject element=elements.optJSONObject(k);
                        JSONObject run=element==null?null:element.optJSONObject("textRun");
                        if(run==null)continue;
                        String text=run.optString("content","");
                        String[] lines=text.split("(?<=\\n)");
                        int offset=0;
                        for(String line:lines){
                            String cleaned=line.trim();
                            java.util.regex.Matcher marker=java.util.regex.Pattern
                                .compile("[🎸🎹🔺🔻🔀🔁⏩⇻➤]|\\s+\\d{2,3}\\s*$|\\.{2,}")
                                .matcher(cleaned);
                            int end=marker.find()?marker.start():cleaned.length();
                            String candidate=cleaned.substring(0,end).trim();
                            String official=titles.get(alias(candidate));
                            if(official!=null && !normalized(candidate).equals(normalized(official))){
                                int prefix=line.indexOf(candidate);
                                if(prefix>=0){
                                    int startIndex=element.optInt("startIndex",-1)+offset+prefix;
                                    if(startIndex>=0)changes.add(new Rename(startIndex,startIndex+candidate.length(),candidate,official));
                                }
                            }
                            offset+=line.length();
                        }
                    }
                }
            }
            break;
        }
        return changes;
    }
    private static void gatherText(Object node,StringBuilder out) throws Exception{
        if(!(node instanceof JSONObject))return;
        JSONObject o=(JSONObject)node;
        JSONObject run=o.optJSONObject("textRun");
        if(run!=null)out.append(run.optString("content",""));
        for(String field:new String[]{"content","elements","tableRows","tableCells"}){
            JSONArray arr=o.optJSONArray(field);
            if(arr!=null)for(int i=0;i<arr.length();i++)gatherText(arr.opt(i),out);
        }
        for(String field:new String[]{"paragraph","table"}){
            JSONObject child=o.optJSONObject(field);
            if(child!=null)gatherText(child,out);
        }
    }
    private void applyRenames(String destination,String accessToken,String revision,List<Rename> changes){
        status.setText("Mise à jour des noms dans la COPIE…");
        new Thread(()->{
            HttpURLConnection post=null;
            try{
                if(isProtectedDocument(destination))
                    throw new java.io.IOException("Original interdit");
                JSONArray requests=new JSONArray();
                // Reverse order: editing text never shifts the earlier indices.
                changes.sort((a,b)->Integer.compare(b.start,a.start));
                for(Rename change:changes){
                    JSONObject range=new JSONObject().put("startIndex",change.start).put("endIndex",change.end);
                    requests.put(new JSONObject().put("deleteContentRange",new JSONObject().put("range",range)));
                    requests.put(new JSONObject().put("insertText",new JSONObject()
                        .put("location",new JSONObject().put("index",change.start))
                        .put("text",change.to)));
                }
                JSONObject payload=new JSONObject()
                    .put("requests",requests)
                    .put("writeControl",new JSONObject().put("requiredRevisionId",revision));
                post=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    destination+":batchUpdate").openConnection();
                post.setRequestMethod("POST");post.setDoOutput(true);
                post.setConnectTimeout(12000);post.setReadTimeout(20000);
                post.setRequestProperty("Authorization","Bearer "+accessToken);
                post.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try(OutputStream out=post.getOutputStream()){
                    out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                int http=post.getResponseCode();
                if(http<200||http>=300)throw new java.io.IOException("Écriture refusée HTTP "+http);
                runOnUiThread(()->status.setText(changes.size()+" nom(s) harmonisés dans la COPIE. L'original est inchangé."));
            }catch(Exception e){
                runOnUiThread(()->status.setText("Écriture non confirmée : "+e.getMessage()));
            }finally{if(post!=null)post.disconnect();}
        },"DocsCopyNameWrite").start();
    }

    // Safe first milestone: append a new empty lyrics section to the copy.
    // The rich-text overview is intentionally untouched until its insertion
    // points and formatting can be validated independently.
    private static String docsMetadata(Song song){
        StringBuilder result=new StringBuilder(song.title.trim());
        if(song.stageGuitar)result.append(" 🎸");
        if(song.stageKeyboard)result.append(" 🎹");
        if(song.bpm!=null && !song.bpm.trim().isEmpty())result.append(" ").append(song.bpm.trim()).append(" BPM");
        return result.toString();
    }
    private static final class IndexLocation {
        final int index;
        final String nextTitle;
        IndexLocation(int index,String nextTitle){this.index=index;this.nextTitle=nextTitle;}
    }
    private IndexLocation locateIndex(JSONObject doc,Song song) throws Exception{
        SetListModel list=null;
        for(SetListModel candidate:AppStore.loadSetlists(this))
            if(Ts2026GoogleDocsSource.isTs2026(candidate.name)){list=candidate;break;}
        if(list==null)return null;
        int pos=list.songIds.indexOf(song.id);
        if(pos<0)return null;
        JSONArray tabs=doc.optJSONArray("tabs");
        JSONObject body=null;
        if(tabs!=null && tabs.length()>0){
            JSONObject tab=tabs.optJSONObject(0);
            JSONObject dt=tab==null?null:tab.optJSONObject("documentTab");
            body=dt==null?null:dt.optJSONObject("body");
        }else body=doc.optJSONObject("body");
        if(body==null)return null;
        JSONArray blocks=body.optJSONArray("content");
        if(blocks==null)return null;
        JSONArray cells=null;
        for(int i=0;i<Math.min(8,blocks.length());i++){
            JSONObject block=blocks.optJSONObject(i);
            JSONObject table=block==null?null:block.optJSONObject("table");
            JSONArray rows=table==null?null:table.optJSONArray("tableRows");
            if(rows==null||rows.length()<2)continue;
            JSONArray headerCells=rows.optJSONObject(0).optJSONArray("tableCells");
            if(headerCells==null||headerCells.length()<1)continue;
            StringBuilder header=new StringBuilder();
            gatherText(headerCells.optJSONObject(0),header);
            if(header.toString().contains("PLAYLIST 2026")){
                cells=rows.optJSONObject(1).optJSONArray("tableCells");break;
            }
        }
        if(cells==null||cells.length()<2)return null;
        // Anchor only to the next existing title, not to approximate row counts.
        for(int n=pos+1;n<list.songIds.size();n++){
            Song neighbor=AppStore.findSong(this,list.songIds.get(n));
            if(neighbor==null||neighbor.title==null)continue;
            for(int col=0;col<2;col++){
                JSONArray paragraphs=cells.optJSONObject(col).optJSONArray("content");
                if(paragraphs==null)continue;
                for(int j=0;j<paragraphs.length();j++){
                    JSONObject paragraph=paragraphs.optJSONObject(j);
                    JSONObject p=paragraph==null?null:paragraph.optJSONObject("paragraph");
                    JSONArray elements=p==null?null:p.optJSONArray("elements");
                    if(elements==null)continue;
                    StringBuilder line=new StringBuilder();
                    for(int k=0;k<elements.length();k++){
                        JSONObject run=elements.optJSONObject(k).optJSONObject("textRun");
                        if(run!=null)line.append(run.optString("content",""));
                    }
                    String value=line.toString();
                    // Docs index lines start with a number and may contain BPM,
                    // instrument icons and stage markers after the title.
                    String wanted=alias(neighbor.title);
                    int offset=0;
                    for(String raw:value.split("\\n",-1)){
                        String cleaned=raw.trim().replaceFirst("^\\d{1,3}\\s*[.)-]\\s*","");
                        if(!wanted.isEmpty() && alias(cleaned).startsWith(wanted)){
                            int start=paragraph.optInt("startIndex",-1);
                            if(start>0)return new IndexLocation(start+offset,neighbor.title);
                        }
                        offset+=raw.length()+1;
                    }
                }
            }
        }
        return null; // Never guess where the index ends.
    }
    private void previewIndexInsertion(String destination,String accessToken,Song song){
        status.setText("Recherche de la position exacte dans le sommaire de la COPIE…");
        new Thread(()->{
            HttpURLConnection get=null,post=null;
            try{
                if(isProtectedDocument(destination))
                    throw new java.io.IOException("Original interdit");
                get=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    destination+"?includeTabsContent=true").openConnection();
                get.setConnectTimeout(12000);get.setReadTimeout(20000);
                get.setRequestProperty("Authorization","Bearer "+accessToken);
                if(get.getResponseCode()!=200)throw new java.io.IOException("Lecture HTTP "+get.getResponseCode());
                JSONObject doc=new JSONObject(readResponse(get));
                String revision=doc.optString("revisionId","");
                if(revision.isEmpty())throw new java.io.IOException("Révision indisponible");
                IndexLocation location=locateIndex(doc,song);
                if(location==null||location.index<1)
                    throw new java.io.IOException("Position non vérifiable : aucune insertion dans le sommaire.");
                String entry=docsMetadata(song)+"\\n";
                runOnUiThread(()->new AlertDialog.Builder(this)
                    .setTitle("Sommaire TS 2026 · COPIE")
                    .setMessage("Insérer « "+docsMetadata(song)+" » avant « "+location.nextTitle+" » ?\\n\\nSeule la COPIE sera modifiée.")
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Insérer",(d,w)->writeIndexEntry(destination,accessToken,revision,location.index,entry))
                    .show());
            }catch(Exception e){
                runOnUiThread(()->status.setText("Sommaire inchangé : "+e.getMessage()));
            }finally{if(get!=null)get.disconnect();}
        },"DocsIndexPreview").start();
    }
    private void writeIndexEntry(String destination,String accessToken,String revision,int index,String entry){
        status.setText("Insertion dans le sommaire de la COPIE…");
        new Thread(()->{
            HttpURLConnection post=null;
            try{
                if(isProtectedDocument(destination))
                    throw new java.io.IOException("Original interdit");
                JSONObject insert=new JSONObject().put("location",new JSONObject().put("index",index))
                    .put("text",entry);
                JSONObject payload=new JSONObject().put("requests",new JSONArray().put(
                    new JSONObject().put("insertText",insert)))
                    .put("writeControl",new JSONObject().put("requiredRevisionId",revision));
                post=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    destination+":batchUpdate").openConnection();
                post.setRequestMethod("POST");post.setDoOutput(true);
                post.setConnectTimeout(12000);post.setReadTimeout(20000);
                post.setRequestProperty("Authorization","Bearer "+accessToken);
                post.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try(OutputStream out=post.getOutputStream())
                    {out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
                if(post.getResponseCode()!=200)
                    throw new java.io.IOException("Écriture HTTP "+post.getResponseCode());
                runOnUiThread(()->status.setText("Titre, icônes et BPM insérés dans le sommaire de la COPIE."));
            }catch(Exception e){
                runOnUiThread(()->status.setText("Insertion non confirmée : "+e.getMessage()));
            }finally{if(post!=null)post.disconnect();}
        },"DocsIndexWrite").start();
    }
    private void confirmPendingSong(){
        if(!validCopy())return;
        Song song=AppStore.findSong(this,pendingSongId);
        if(song==null || song.title==null || song.title.trim().isEmpty())return;
        String destination=id();
        String metadata=docsMetadata(song);
        new AlertDialog.Builder(this).setTitle("Synchroniser sur la COPIE ?")
            .setMessage("Créer à la fin de la COPIE une section pour « "+metadata+" » ? Le sommaire ne sera pas encore modifié.")
            .setNegativeButton("Plus tard",null)
            .setPositiveButton("Créer",(d,w)->{
                getPreferences(MODE_PRIVATE).edit().putString("test_copy_id",destination).apply();
                appendLyricsSection(destination,token,song);
            }).show();
    }
    private void chooseNewLyricsSection(){
        if(!validCopy())return;
        if(token==null||token.isEmpty()){
            status.setText("Autorise Google Docs d'abord.");return;
        }
        SetListModel list=null;
        for(SetListModel candidate:AppStore.loadSetlists(this)){
            if(Ts2026GoogleDocsSource.isTs2026(candidate.name)){list=candidate;break;}
        }
        if(list==null){status.setText("TS 2026 introuvable.");return;}
        List<Song> songs=new ArrayList<>();
        for(String songId:list.songIds){
            Song song=AppStore.findSong(this,songId);
            if(song!=null && song.title!=null && !song.title.trim().isEmpty())songs.add(song);
        }
        if(songs.isEmpty()){status.setText("Aucun morceau TS 2026.");return;}
        String[] labels=new String[songs.size()];
        for(int i=0;i<songs.size();i++)labels[i]=songs.get(i).title;
        new AlertDialog.Builder(this)
            .setTitle("Créer une section de paroles dans la COPIE")
            .setItems(labels,(dialog,which)->{
                Song selected=songs.get(which);
                new AlertDialog.Builder(this)
                    .setTitle(selected.title)
                    .setMessage("Ajouter une section vide à la fin de la COPIE ?\n"+
                        "Aucune modification du sommaire ou des paroles existantes.\n"+
                        "L'original est interdit.")
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Créer dans la COPIE",(d,w)->
                        appendLyricsSection(id(),token,selected))
                    .show();
            }).show();
    }
    private void appendLyricsSection(String destination,String accessToken,Song song){
        final String title=song.title;
        final String metadata=docsMetadata(song);
        status.setText("Vérification de la copie et des doublons…");
        new Thread(()->{
            HttpURLConnection get=null,post=null;
            try{
                if(isProtectedDocument(destination))
                    throw new java.io.IOException("Document original interdit");
                get=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    destination+"?includeTabsContent=true").openConnection();
                get.setConnectTimeout(12000);get.setReadTimeout(20000);
                get.setRequestProperty("Authorization","Bearer "+accessToken);
                if(get.getResponseCode()!=200)
                    throw new java.io.IOException("Lecture HTTP "+get.getResponseCode());
                JSONObject doc=new JSONObject(readResponse(get));
                String revision=doc.optString("revisionId","");
                if(revision.isEmpty())throw new java.io.IOException("Révision indisponible");
                String marker="TS2026-NEW-SONG:"+normalized(title);
                if(doc.toString().contains(marker))
                    throw new java.io.IOException("Section de test déjà créée pour ce titre.");
                String insertion="\n\n"+metadata+"\n[Paroles à compléter]\n"+
                    "[Repère de synchronisation : "+marker+"]\n";
                JSONObject insert=new JSONObject()
                    .put("endOfSegmentLocation",new JSONObject())
                    .put("text",insertion);
                JSONObject payload=new JSONObject()
                    .put("requests",new JSONArray().put(
                        new JSONObject().put("insertText",insert)))
                    .put("writeControl",new JSONObject().put("requiredRevisionId",revision));
                post=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    destination+":batchUpdate").openConnection();
                post.setRequestMethod("POST");post.setDoOutput(true);
                post.setConnectTimeout(12000);post.setReadTimeout(20000);
                post.setRequestProperty("Authorization","Bearer "+accessToken);
                post.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try(OutputStream out=post.getOutputStream()){
                    out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                int http=post.getResponseCode();
                if(http<200||http>=300)throw new java.io.IOException("Écriture HTTP "+http);
                runOnUiThread(()->{
                    status.setText("Section de paroles créée dans la COPIE. Vérification du sommaire…");
                    if(pendingSongId!=null && pendingSongId.equals(song.id))
                        previewIndexInsertion(destination,accessToken,song);
                });
            }catch(Exception e){
                runOnUiThread(()->status.setText("Aucune écriture confirmée : "+e.getMessage()));
            }finally{
                if(get!=null)get.disconnect();
                if(post!=null)post.disconnect();
            }
        },"Ts2026CopyLyricsSection").start();
    }

}
