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
    private boolean autoSyncLaunch;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        pendingSongId=getIntent().getStringExtra("sync_song_id");
        autoSyncLaunch=getIntent().getBooleanExtra("auto_playlist_sync",false);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout layout=new LinearLayout(this);
        layout.setPadding(12,8,12,8);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(15,15,15));
        TextView heading=new TextView(this);
        heading.setText("TS 2026 · Google Docs");
        heading.setTextSize(16);heading.setTextColor(Color.WHITE);layout.addView(heading);
        TextView explanation=new TextView(this);
        explanation.setText("1. Crée une copie de ton Google Docs original dans Google Drive.\n2. Colle l'identifiant de cette COPIE ci-dessous.\n3. Autorise l'accès Google.\n4. Confirme l'ajout d'une ligne de test à la fin de la copie.\n\nAucune modification automatique de la playlist ni du document original.");
        explanation.setTextSize(11);explanation.setLineSpacing(0f,1.0f);
        explanation.setTextColor(Color.LTGRAY);layout.addView(explanation);
        copyId=new EditText(this);copyId.setSingleLine(true);copyId.setTextColor(Color.WHITE);
        copyId.setHintTextColor(Color.GRAY);copyId.setHint("Identifiant ou lien Google Docs de la copie");
        copyId.setText(getPreferences(MODE_PRIVATE).getString("test_copy_id",""));
        layout.addView(copyId,new LinearLayout.LayoutParams(-1,-2));
        status=new TextView(this);status.setTextColor(Color.WHITE);
        status.setText(getSharedPreferences("ts2026_docs_sync",MODE_PRIVATE).getBoolean("pending",false)
            ? "Modification en attente : autorise Google puis synchronise le sommaire."
            : "Non connecté · autorisation Google nécessaire.");
        status.setTextSize(12);status.setPadding(4,4,4,4);
        layout.addView(status);
        Button authorize=new Button(this);authorize.setText("Autoriser Google Docs");
        authorize.setOnClickListener(v->authorize());layout.addView(authorize);
        Button syncPlaylist=new Button(this);
        syncPlaylist.setText("SYNCHRONISER PLAYLIST → SOMMAIRE DOCS");
        syncPlaylist.setOnClickListener(v->confirmPlaylistSync());
        layout.addView(syncPlaylist);
        CheckBox autoToggle=new CheckBox(this);
        autoToggle.setText("Activer la synchro automatique TS 2026");
        autoToggle.setTextColor(Color.WHITE);
        autoToggle.setChecked(getSharedPreferences("ts2026_docs_sync",MODE_PRIVATE).getBoolean("auto_enabled",false));
        autoToggle.setOnCheckedChangeListener((button,checked)->
            getSharedPreferences("ts2026_docs_sync",MODE_PRIVATE).edit()
                .putBoolean("auto_enabled",checked).apply());
        layout.addView(autoToggle);
        Button official=new Button(this);
        official.setText("AJOUTER 2 TITRES · DOCS OFFICIEL");
        official.setOnClickListener(v->confirmOfficialMissingTwo());
        layout.addView(official);
        Button updateSummary=new Button(this);
        updateSummary.setText("METTRE À JOUR SOMMAIRE · 34 TITRES");
        updateSummary.setOnClickListener(v->confirmOfficialSummary());
        layout.addView(updateSummary);
        Button formatSummary=new Button(this);
        formatSummary.setText("HARMONISER FORMAT · TITRES 33–34");
        formatSummary.setOnClickListener(v->confirmFormatSummary());
        layout.addView(formatSummary);
        Button compactSummary=new Button(this);
        compactSummary.setText("SOMMAIRE · chiffres BPM et liste continue");
        compactSummary.setOnClickListener(v->confirmCompactSummary());
        layout.addView(compactSummary);

        TextView testHeading=new TextView(this);
        testHeading.setText("OUTILS DE TEST · COPIE UNIQUEMENT");
        testHeading.setTextSize(12);
        testHeading.setTextColor(Color.LTGRAY);
        layout.addView(testHeading);
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
        for(int i=0;i<layout.getChildCount();i++){
            android.view.View child=layout.getChildAt(i);
            if(child instanceof Button){
                Button b=(Button)child;
                b.setTextSize(13);
                b.setAllCaps(false);
                b.setMinHeight(Ui.dp(this,48));b.setMinimumHeight(Ui.dp(this,48));
                b.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),0);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,52));
                lp.bottomMargin=Ui.dp(this,4);
                b.setLayoutParams(lp);
            }
        }
        scroll.addView(layout);
        setContentView(scroll);
        if(autoSyncLaunch)authorize();
        if(pendingSongId!=null){
            status.setText("Morceau validé dans TS 2026. Autorise Google Docs puis confirme l’ajout de sa section dans la COPIE.");
        }
    }





    private void confirmPlaylistSync(){
        if(token==null||token.isEmpty()){
            status.setText("Autorise Google Docs avant la synchronisation.");return;
        }
        SetListModel playlist=null;
        for(SetListModel item:AppStore.loadSetlists(this))
            if(Ts2026GoogleDocsSource.isTs2026(item.name)){playlist=item;break;}
        if(playlist==null){status.setText("Playlist TS 2026 introuvable.");return;}
        StringBuilder snapshot=new StringBuilder();
        java.util.Set<String> keys=new java.util.HashSet<>();
        int count=0;
        for(String id:playlist.songIds){
            Song song=AppStore.findSong(this,id);
            if(song==null||song.title==null||song.title.trim().isEmpty()){
                status.setText("Titre Manager introuvable : synchronisation annulée.");return;
            }
            String key=normalized(song.title);
            if(!keys.add(key)){status.setText("Titre en double dans le Manager : synchronisation annulée.");return;}
            snapshot.append(String.format(Locale.FRANCE,"%02d. %s",++count,song.title.trim()));
            if(song.bpm!=null&&!song.bpm.trim().isEmpty())
                snapshot.append(" · ").append(song.bpm.trim());
            snapshot.append("\n");
        }
        if(count<1||count>100){status.setText("Nombre de titres invalide.");return;}
        final String desired=snapshot.toString();
        final String auth=token;
        new AlertDialog.Builder(this)
            .setTitle("Synchroniser "+count+" titres vers Google Docs")
            .setMessage("Le sommaire du document officiel sera remplacé par l'ordre actuel de TS 2026.\n"+
                "Les titres retirés du Manager disparaîtront UNIQUEMENT du sommaire.\n"+
                "Les paroles, les morceaux de la bibliothèque et l'ancien Google Docs resteront intacts.\n\n"+
                "Aucune écriture sans validation de la structure actuelle du document.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Synchroniser",(d,w)->syncOfficialPlaylist(auth,desired))
            .show();
    }
    private void syncOfficialPlaylist(String accessToken,String desired){
        status.setText("Vérification du sommaire Google Docs…");
        new Thread(()->{
            HttpURLConnection get=null,post=null;
            try{
                String dest=Ts2026GoogleDocsSource.PREPARED_DOCUMENT_ID;
                get=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    dest+"?includeTabsContent=true").openConnection();
                get.setConnectTimeout(12000);get.setReadTimeout(20000);
                get.setRequestProperty("Authorization","Bearer "+accessToken);
                if(get.getResponseCode()!=200)throw new java.io.IOException("Lecture HTTP "+get.getResponseCode());
                JSONObject doc=new JSONObject(readResponse(get));
                JSONArray tabs=doc.optJSONArray("tabs");
                JSONObject tab=tabs!=null&&tabs.length()>0?tabs.optJSONObject(0):null;
                JSONObject dt=tab==null?null:tab.optJSONObject("documentTab");
                JSONObject body=dt==null?doc.optJSONObject("body"):dt.optJSONObject("body");
                if(body==null)throw new java.io.IOException("Corps du document absent");
                JSONObject props=tab==null?null:tab.optJSONObject("tabProperties");
                String tabId=props==null?"":props.optString("tabId","");
                JSONArray blocks=body.optJSONArray("content");
                if(blocks==null)throw new java.io.IOException("Paragraphes indisponibles");
                int header=-1,lyrics=-1,first=-1,last=-1,count=0;
                java.util.Set<Integer> numbers=new java.util.HashSet<>();
                for(int i=0;i<blocks.length();i++){
                    JSONObject block=blocks.optJSONObject(i);
                    JSONObject paragraph=block==null?null:block.optJSONObject("paragraph");
                    if(paragraph==null)continue;
                    JSONArray elements=paragraph.optJSONArray("elements");
                    if(elements==null)continue;
                    StringBuilder line=new StringBuilder();
                    for(int j=0;j<elements.length();j++){
                        JSONObject el=elements.optJSONObject(j);
                        if(el==null)continue;
                        JSONObject run=el.optJSONObject("textRun");
                        if(run!=null)line.append(run.optString("content",""));
                        // Page breaks elsewhere in the document must not block index synchronization.
                    }
                    String value=line.toString().trim();
                    if(value.toUpperCase(Locale.ROOT).contains("SOMMAIRE TS 2026"))header=i;
                    if(value.toUpperCase(Locale.ROOT).contains("PAROLES ET NOTES")){lyrics=i;break;}
                    if(header>=0 && i>header){
                        java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                            "^(\\d{1,3})\\s*[.)-]\\s*(.+)$").matcher(value);
                        if(m.matches()){
                            int n=Integer.parseInt(m.group(1));
                            if(n<1||n>100||!numbers.add(n))
                                throw new java.io.IOException("Numérotation du sommaire invalide");
                            if(first<0)first=i;
                            last=i;count++;
                        }else if(!value.isEmpty() && first>=0)
                            throw new java.io.IOException("Texte inattendu entre les titres : "+value);
                    }
                }
                if(header<0||lyrics<0||first<0||last<first||count<1)
                    throw new java.io.IOException("Sommaire non reconnu");
                for(int n=1;n<=count;n++)
                    if(!numbers.contains(n))throw new java.io.IOException("Numérotation discontinue");
                // Only edit the index paragraphs, never the heading or the lyrics heading.
                JSONObject firstBlock=blocks.getJSONObject(first),lastBlock=blocks.getJSONObject(last);
                int start=firstBlock.optInt("startIndex",-1),end=lastBlock.optInt("endIndex",-1);
                int lyricsStart=blocks.getJSONObject(lyrics).optInt("startIndex",-1);
                if(start<1||end<=start||lyricsStart<end)
                    throw new java.io.IOException("Limites du sommaire invalides");
                String revision=doc.optString("revisionId","");
                if(revision.isEmpty())throw new java.io.IOException("Révision Google Docs manquante");
                JSONObject range=new JSONObject().put("startIndex",start).put("endIndex",end);
                if(!tabId.isEmpty())range.put("tabId",tabId);
                JSONObject location=new JSONObject().put("index",start);
                if(!tabId.isEmpty())location.put("tabId",tabId);
                JSONArray requests=new JSONArray()
                    .put(new JSONObject().put("deleteContentRange",new JSONObject().put("range",range)))
                    .put(new JSONObject().put("insertText",new JSONObject()
                        .put("location",location).put("text",desired)));
                JSONObject payload=new JSONObject().put("requests",requests)
                    .put("writeControl",new JSONObject().put("requiredRevisionId",revision));
                post=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    dest+":batchUpdate").openConnection();
                post.setRequestMethod("POST");post.setConnectTimeout(12000);post.setReadTimeout(20000);
                post.setDoOutput(true);
                post.setRequestProperty("Authorization","Bearer "+accessToken);
                post.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try(OutputStream out=post.getOutputStream()){
                    out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code=post.getResponseCode();
                if(code<200||code>=300)throw new java.io.IOException("Écriture refusée HTTP "+code);
                getSharedPreferences("ts2026_docs_sync",MODE_PRIVATE).edit().putBoolean("pending",false).apply();
                runOnUiThread(()->status.setText("Sommaire synchronisé avec la playlist Manager. Paroles conservées."));
            }catch(Exception e){
                runOnUiThread(()->{
                    String message="Synchronisation non confirmée : "+e.getMessage();
                    status.setText(message);
                    new AlertDialog.Builder(this).setTitle("Google Docs : synchronisation bloquée")
                        .setMessage(message+"\n\nAucune suppression supplémentaire ne sera tentée automatiquement.")
                        .setPositiveButton("OK",null).show();
                });
            }finally{
                if(get!=null)get.disconnect();
                if(post!=null)post.disconnect();
            }
        },"Ts2026PlaylistToDocs").start();
    }

    private void confirmCompactSummary(){
        if(token==null||token.isEmpty()){
            status.setText("Autorise Google Docs avant la correction.");return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Sommaire continu")
            .setMessage("Retirer uniquement le mot BPM des lignes 33 et 34, conserver 120 et 105, "+
                "et supprimer le saut de page avant ces lignes. Les autres titres et les paroles restent inchangés.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Appliquer",(d,w)->compactOfficialSummary(token))
            .show();
    }
    private void compactOfficialSummary(String accessToken){
        status.setText("Analyse de la pagination du sommaire…");
        new Thread(()->{
            HttpURLConnection get=null,post=null;
            try{
                String dest=Ts2026GoogleDocsSource.PREPARED_DOCUMENT_ID;
                get=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    dest+"?includeTabsContent=true").openConnection();
                get.setConnectTimeout(12000);get.setReadTimeout(20000);
                get.setRequestProperty("Authorization","Bearer "+accessToken);
                if(get.getResponseCode()!=200)throw new java.io.IOException("Lecture HTTP "+get.getResponseCode());
                JSONObject doc=new JSONObject(readResponse(get));
                JSONArray tabs=doc.optJSONArray("tabs");
                JSONObject tab=tabs!=null&&tabs.length()>0?tabs.optJSONObject(0):null;
                JSONObject dt=tab==null?null:tab.optJSONObject("documentTab");
                JSONObject body=dt==null?doc.optJSONObject("body"):dt.optJSONObject("body");
                if(body==null)throw new java.io.IOException("Structure du document inconnue");
                JSONObject props=tab==null?null:tab.optJSONObject("tabProperties");
                String tabId=props==null?"":props.optString("tabId","");
                JSONArray blocks=body.optJSONArray("content");
                if(blocks==null)throw new java.io.IOException("Paragraphes indisponibles");
                int thirtyTwo=-1,thirtyThree=-1,thirtyFour=-1;
                boolean explicitBreak=false;
                JSONArray requests=new JSONArray();
                for(int i=0;i<blocks.length();i++){
                    JSONObject block=blocks.optJSONObject(i);
                    JSONObject paragraph=block==null?null:block.optJSONObject("paragraph");
                    if(paragraph==null)continue;
                    JSONArray elements=paragraph.optJSONArray("elements");
                    if(elements==null)continue;
                    StringBuilder line=new StringBuilder();
                    for(int j=0;j<elements.length();j++){
                        JSONObject el=elements.optJSONObject(j);
                        if(el==null)continue;
                        if(el.optJSONObject("textRun")!=null)
                            line.append(el.optJSONObject("textRun").optString("content",""));
                        if(el.has("pageBreak") && i>0 && i<blocks.length()-1) { /* checked below for target paragraphs */ }
                    }
                    String value=line.toString().trim();
                    int number=value.matches("(?i)^32[.]\\s*UPTOWN FUNK.*")?32:
                        value.matches("(?i)^33[.]\\s*SMOOTH OPERATOR.*")?33:
                        value.matches("(?i)^34[.]\\s*IS IT A CRIME.*")?34:0;
                    if(number==0)continue;
                    for(int j=0;j<elements.length();j++){
                        JSONObject el=elements.optJSONObject(j);
                        if(el!=null&&el.has("pageBreak"))explicitBreak=true;
                    }
                    int start=block.optInt("startIndex",-1),end=block.optInt("endIndex",-1);
                    if(start<1||end<=start)throw new java.io.IOException("Position du titre "+number+" inconnue");
                    if(number==32)thirtyTwo=start;
                    if(number==33)thirtyThree=start;
                    if(number==34)thirtyFour=start;
                    JSONObject range=new JSONObject().put("startIndex",start).put("endIndex",end);
                    if(!tabId.isEmpty())range.put("tabId",tabId);
                    JSONObject style=new JSONObject()
                        .put("pageBreakBefore",false)
                        .put("keepWithNext",false)
                        .put("spaceAbove",new JSONObject().put("magnitude",0).put("unit","PT"))
                        .put("spaceBelow",new JSONObject().put("magnitude",0).put("unit","PT"));
                    requests.put(new JSONObject().put("updateParagraphStyle",new JSONObject()
                        .put("range",range).put("paragraphStyle",style)
                        .put("fields","pageBreakBefore,keepWithNext,spaceAbove,spaceBelow")));
                }
                if(thirtyTwo<0||thirtyThree<0||thirtyFour<0||
                    !(thirtyTwo<thirtyThree&&thirtyThree<thirtyFour))
                    throw new java.io.IOException("Titres 32–34 non trouvés dans l'ordre attendu");
                if(explicitBreak)
                    throw new java.io.IOException("Saut de page explicite détecté : correction automatique annulée");
                String revision=doc.optString("revisionId","");
                if(revision.isEmpty())throw new java.io.IOException("Révision Google Docs indisponible");
                String[] originals={"33. SMOOTH OPERATOR · 120 BPM","34. IS IT A CRIME · 105 BPM"};
                String[] replacements={"33. SMOOTH OPERATOR · 120","34. IS IT A CRIME · 105"};
                for(int i=0;i<2;i++){
                    requests.put(new JSONObject().put("replaceAllText",new JSONObject()
                        .put("containsText",new JSONObject().put("text",originals[i]).put("matchCase",true))
                        .put("replaceText",replacements[i])));
                }
                JSONObject payload=new JSONObject().put("requests",requests)
                    .put("writeControl",new JSONObject().put("requiredRevisionId",revision));
                post=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    dest+":batchUpdate").openConnection();
                post.setRequestMethod("POST");post.setConnectTimeout(12000);post.setReadTimeout(20000);
                post.setDoOutput(true);
                post.setRequestProperty("Authorization","Bearer "+accessToken);
                post.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try(OutputStream out=post.getOutputStream()){
                    out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code=post.getResponseCode();
                if(code<200||code>=300)throw new java.io.IOException("Google Docs HTTP "+code);
                JSONObject response=new JSONObject(readResponse(post));
                JSONArray replies=response.optJSONArray("replies");
                int n=requests.length();
                for(int i=n-2;i<n;i++){
                    JSONObject reply=replies==null?null:replies.optJSONObject(i);
                    JSONObject replacement=reply==null?null:reply.optJSONObject("replaceAllText");
                    if(replacement==null||replacement.optInt("occurrencesChanged",0)!=1)
                        throw new java.io.IOException("Remplacement BPM non confirmé : vérifier le sommaire");
                }
                runOnUiThread(()->status.setText("Sommaire ajusté : 120 et 105 conservés, saut de page avant les titres désactivé."));
            }catch(Exception e){
                runOnUiThread(()->status.setText("Correction non confirmée : "+e.getMessage()));
            }finally{
                if(get!=null)get.disconnect();
                if(post!=null)post.disconnect();
            }
        },"Ts2026CompactSummary").start();
    }

    private void confirmFormatSummary(){
        if(token==null||token.isEmpty()){
            status.setText("Autorise Google Docs avant de corriger le format.");return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Harmoniser les titres 33 et 34")
            .setMessage("Mettre les deux nouvelles lignes en noir, sans gras, en petite police, "+
                "avec un espacement réduit. Aucun texte, titre ou parole ne sera supprimé.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Corriger le format",(d,w)->formatOfficialSummary(token))
            .show();
    }
    private void formatOfficialSummary(String accessToken){
        status.setText("Recherche des deux lignes dans le sommaire…");
        new Thread(()->{
            HttpURLConnection get=null,post=null;
            try{
                String dest=Ts2026GoogleDocsSource.PREPARED_DOCUMENT_ID;
                get=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    dest+"?includeTabsContent=true").openConnection();
                get.setConnectTimeout(12000);get.setReadTimeout(20000);
                get.setRequestProperty("Authorization","Bearer "+accessToken);
                if(get.getResponseCode()!=200)throw new java.io.IOException("Lecture HTTP "+get.getResponseCode());
                JSONObject doc=new JSONObject(readResponse(get));
                JSONArray tabs=doc.optJSONArray("tabs");
                JSONObject firstTab=tabs!=null&&tabs.length()>0?tabs.optJSONObject(0):null;
                JSONObject dt=firstTab==null?null:firstTab.optJSONObject("documentTab");
                JSONObject contentBody=dt==null?doc.optJSONObject("body"):dt.optJSONObject("body");
                if(contentBody==null)throw new java.io.IOException("Structure du document inconnue");
                String tabId=firstTab==null?"":firstTab.optJSONObject("tabProperties").optString("tabId","");
                JSONArray blocks=contentBody.optJSONArray("content");
                if(blocks==null)throw new java.io.IOException("Paragraphes indisponibles");
                JSONArray requests=new JSONArray();
                int found=0;
                for(int i=0;i<blocks.length();i++){
                    JSONObject block=blocks.optJSONObject(i);
                    JSONObject paragraph=block==null?null:block.optJSONObject("paragraph");
                    if(paragraph==null)continue;
                    JSONArray elements=paragraph.optJSONArray("elements");
                    if(elements==null)continue;
                    StringBuilder line=new StringBuilder();
                    for(int j=0;j<elements.length();j++){
                        JSONObject el=elements.optJSONObject(j);
                        if(el!=null&&el.optJSONObject("textRun")!=null)
                            line.append(el.optJSONObject("textRun").optString("content",""));
                    }
                    String value=line.toString().trim();
                    if(!value.matches("(?i)^(33[.]\\s*SMOOTH OPERATOR|34[.]\\s*IS IT A CRIME).*"))continue;
                    int start=block.optInt("startIndex",-1),end=block.optInt("endIndex",-1);
                    if(start<1||end<=start)throw new java.io.IOException("Position de ligne inconnue");
                    JSONObject range=new JSONObject().put("startIndex",start).put("endIndex",end);
                    if(!tabId.isEmpty())range.put("tabId",tabId);
                    JSONObject style=new JSONObject()
                        .put("bold",false)
                        .put("foregroundColor",new JSONObject().put("color",
                            new JSONObject().put("rgbColor",new JSONObject()
                                .put("red",0).put("green",0).put("blue",0))))
                        .put("fontSize",new JSONObject().put("magnitude",9).put("unit","PT"));
                    requests.put(new JSONObject().put("updateTextStyle",new JSONObject()
                        .put("range",range).put("textStyle",style)
                        .put("fields","bold,foregroundColor,fontSize")));
                    JSONObject paragraphStyle=new JSONObject()
                        .put("spaceAbove",new JSONObject().put("magnitude",0).put("unit","PT"))
                        .put("spaceBelow",new JSONObject().put("magnitude",0).put("unit","PT"));
                    requests.put(new JSONObject().put("updateParagraphStyle",new JSONObject()
                        .put("range",range).put("paragraphStyle",paragraphStyle)
                        .put("fields","spaceAbove,spaceBelow")));
                    found++;
                }
                if(found!=2)throw new java.io.IOException("Trouvé "+found+" ligne(s) sur 2 : aucun changement");
                String revision=doc.optString("revisionId","");
                if(revision.isEmpty())throw new java.io.IOException("Révision manquante");
                JSONObject body=new JSONObject().put("requests",requests)
                    .put("writeControl",new JSONObject().put("requiredRevisionId",revision));
                post=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    dest+":batchUpdate").openConnection();
                post.setRequestMethod("POST");post.setConnectTimeout(12000);post.setReadTimeout(20000);
                post.setDoOutput(true);
                post.setRequestProperty("Authorization","Bearer "+accessToken);
                post.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try(OutputStream out=post.getOutputStream()){
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                if(post.getResponseCode()<200||post.getResponseCode()>=300)
                    throw new java.io.IOException("Correction HTTP "+post.getResponseCode());
                runOnUiThread(()->status.setText("Format des titres 33–34 corrigé. Vérifie la mise en page dans Google Docs."));
            }catch(Exception e){
                runOnUiThread(()->status.setText("Format non modifié : "+e.getMessage()));
            }finally{
                if(get!=null)get.disconnect();
                if(post!=null)post.disconnect();
            }
        },"Ts2026FormatSummary").start();
    }

    private void confirmOfficialSummary(){
        if(token==null||token.isEmpty()){
            status.setText("Autorise Google Docs avant de mettre à jour le sommaire.");return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Mettre le sommaire à 34 titres ?")
            .setMessage("Insérer juste avant « PAROLES ET NOTES » :\n"+
                "33. SMOOTH OPERATOR · 120 BPM\n"+
                "34. IS IT A CRIME · 105 BPM\n\n"+
                "Les 32 titres et toutes les paroles existantes seront conservés. "+
                "L'opération sera refusée si la structure a changé ou si ces titres sont déjà dans le sommaire.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Mettre à jour",(d,w)->updateOfficialSummary(token))
            .show();
    }
    private static void collectDocText(Object node,StringBuilder out){
        if(node instanceof JSONObject){
            JSONObject object=(JSONObject)node;
            JSONObject run=object.optJSONObject("textRun");
            if(run!=null)out.append(run.optString("content",""));
            java.util.Iterator<String> keys=object.keys();
            while(keys.hasNext()){
                String key=keys.next();
                if(!key.equals("textRun"))collectDocText(object.opt(key),out);
            }
        }else if(node instanceof JSONArray){
            JSONArray arr=(JSONArray)node;
            for(int i=0;i<arr.length();i++)collectDocText(arr.opt(i),out);
        }
    }
    private void updateOfficialSummary(String accessToken){
        status.setText("Vérification du sommaire officiel…");
        new Thread(()->{
            HttpURLConnection get=null,post=null;
            try{
                String destination=Ts2026GoogleDocsSource.PREPARED_DOCUMENT_ID;
                get=(HttpURLConnection)new URL("https://docs.googleapis.com/v1/documents/"+
                    destination+"?includeTabsContent=true").openConnection();
                get.setConnectTimeout(12000);get.setReadTimeout(20000);
                get.setRequestProperty("Authorization","Bearer "+accessToken);
                if(get.getResponseCode()!=200)throw new java.io.IOException("Lecture HTTP "+get.getResponseCode());
                JSONObject doc=new JSONObject(readResponse(get));
                StringBuilder all=new StringBuilder();
                JSONArray tabs=doc.optJSONArray("tabs");
                if(tabs!=null)collectDocText(tabs,all);
                else collectDocText(doc.optJSONObject("body"),all);
                String text=all.toString();
                int start=text.indexOf("SOMMAIRE TS 2026");
                int end=text.indexOf("PAROLES ET NOTES");
                if(start<0||end<=start||end-start>14000)
                    throw new java.io.IOException("Sommaire ou séparation des paroles introuvable");
                if(text.indexOf("PAROLES ET NOTES",end+1)>=0)
                    throw new java.io.IOException("Plusieurs séparateurs détectés : opération annulée");
                String summary=text.substring(start,end);
                java.util.regex.Matcher numbered=java.util.regex.Pattern.compile(
                    "(?m)^\\s*(0?[1-9]|[12][0-9]|3[0-4])\\s*[.)-]\\s*(.{3,140})$").matcher(summary);
                java.util.Set<Integer> entries=new java.util.HashSet<>();
                while(numbered.find())entries.add(Integer.parseInt(numbered.group(1)));
                for(int n=1;n<=32;n++)
                    if(!entries.contains(n))throw new java.io.IOException("Entrée "+n+" absente : sommaire non conforme");
                if(entries.contains(33)||entries.contains(34)||
                    summary.toUpperCase(Locale.ROOT).contains("SMOOTH OPERATOR")||
                    summary.toUpperCase(Locale.ROOT).contains("IS IT A CRIME"))
                    throw new java.io.IOException("Un titre 33/34 existe déjà : aucun doublon ajouté");
                String revision=doc.optString("revisionId","");
                if(revision.isEmpty())throw new java.io.IOException("Révision Google Docs indisponible");
                String addition="33. SMOOTH OPERATOR · 120 BPM\n34. IS IT A CRIME · 105 BPM\n\n";
                JSONObject replace=new JSONObject()
                    .put("containsText",new JSONObject().put("text","PAROLES ET NOTES").put("matchCase",true))
                    .put("replaceText",addition+"PAROLES ET NOTES");
                JSONObject body=new JSONObject()
                    .put("requests",new JSONArray().put(new JSONObject().put("replaceAllText",replace)))
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
                JSONObject response=new JSONObject(readResponse(post));
                JSONArray replies=response.optJSONArray("replies");
                int changed=replies==null||replies.length()==0?0:
                    replies.optJSONObject(0).optJSONObject("replaceAllText").optInt("occurrencesChanged",0);
                if(changed!=1)throw new java.io.IOException("Réponse inattendue : "+changed+" remplacement(s). Vérifier le document.");
                runOnUiThread(()->status.setText("Sommaire mis à jour : 34 titres. Paroles conservées."));
            }catch(Exception e){
                runOnUiThread(()->status.setText("Mise à jour non confirmée : "+e.getMessage()));
            }finally{
                if(get!=null)get.disconnect();
                if(post!=null)post.disconnect();
            }
        },"Ts2026OfficialSummary").start();
    }

    private void confirmOfficialMissingTwo(){
        if(token==null||token.isEmpty()){
            status.setText("Autorise Google Docs avant toute écriture.");return;
        }
        Song smooth=null,crime=null;
        SetListModel playlist=null;
        for(SetListModel item:AppStore.loadSetlists(this))
            if(Ts2026GoogleDocsSource.isTs2026(item.name)){playlist=item;break;}
        if(playlist==null){status.setText("Playlist TS 2026 introuvable.");return;}
        for(String songId:playlist.songIds){
            Song song=AppStore.findSong(this,songId);
            if(song==null)continue;
            if(normalized(song.title).equals("smoothoperator"))smooth=song;
            if(normalized(song.title).equals("isitacrime"))crime=song;
        }
        if(smooth==null||crime==null){
            status.setText("Les deux titres doivent être présents dans TS 2026.");return;
        }
        final Song first=smooth,second=crime;
        String preview=preparedSongSection(first)+preparedSongSection(second);
        new AlertDialog.Builder(this)
            .setTitle("ÉCRITURE DANS LE NOUVEAU GOOGLE DOCS")
            .setMessage("ATTENTION : ceci modifie réellement le document reformatté.\n"+
                "Deux sections seront ajoutées à la fin. Les 32 titres existants et leur sommaire ne seront PAS modifiés à cette étape.\n"+
                "Aucune parole ne sera inventée.\n\n"+
                preview.substring(0,Math.min(preview.length(),1400))+
                "\n\nConfirmer l'écriture dans le document officiel ?")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Confirmer l'ajout",(d,w)->appendMissingTwo(
                Ts2026GoogleDocsSource.PREPARED_DOCUMENT_ID,token,first,second,true))
            .show();
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
        appendMissingTwo(destination,accessToken,first,second,false);
    }
    private void appendMissingTwo(String destination,String accessToken,Song first,Song second,boolean official){
        status.setText(official?"Vérification du document officiel…":"Vérification de la COPIE avant écriture…");
        new Thread(()->{
            HttpURLConnection get=null,post=null;
            try{
                if(official){
                    if(!Ts2026GoogleDocsSource.PREPARED_DOCUMENT_ID.equals(destination))
                        throw new IllegalArgumentException("Destination officielle invalide");
                }else if(isProtectedDocument(destination))throw new IllegalArgumentException("Document protégé");
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
                runOnUiThread(()->status.setText(official ?
                    "Deux sections ajoutées au NOUVEAU document officiel. Sommaire encore à actualiser." :
                    "Deux sections ajoutées à la COPIE. Sommaire original inchangé."));
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
            if(token!=null && autoSyncLaunch){autoSyncLaunch=false;confirmPlaylistSync();}
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
