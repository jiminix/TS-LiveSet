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
    // Read only the opening index block, never the lyrics following it.
    // The first 32 numbered entries are the intended source; a missing
    // boundary or insufficient overlap fails closed rather than guessing.
    private List<String> recoverOpeningIndex(String text){
        Set<String> known=new HashSet<>();
        SetListModel list=AppStore.findSetlist(this,playlistId);
        if(list==null)return Collections.emptyList();
        for(String id:list.songIds){
            Song song=AppStore.findSong(this,id);
            if(song!=null)known.add(key(song.title));
        }
        String[] lines=text.split("\\n");
        LinkedHashMap<String,String> found=new LinkedHashMap<>();
        int overlap=0, scanned=0;
        for(String raw:lines){
            if(scanned++>=48 || found.size()>=32)break;
            String line=raw.trim().replaceAll("\\s+"," ");
            line=line.replaceFirst("^(?:0?[1-9]|[12][0-9]|3[0-9])\\s*[.)–:-]\\s*","");
            if(line.length()<3 || line.length()>100)continue;
            if(line.matches("(?i)^(page|sommaire|setlist|ts 2026|index|repertoire|répertoire).*"))continue;
            String normalized=key(line);
            if(!normalized.isEmpty()&&!found.containsKey(normalized)){
                found.put(normalized,line);
                if(known.contains(normalized))overlap++;
            }
        }
        // Never offer lyrics if the opening section cannot be confidently found.
        if(overlap<20 || found.size()<28 || found.size()>32)return Collections.emptyList();
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
            JSONArray elements=object.optJSONArray("elements");
            if(elements!=null)for(int i=0;i<elements.length();i++)collectText(elements.get(i),out);
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
    // Diagnostic only: show the real top-level layout (paragraphs/tables).
    // Does not infer or import song titles.
    private static void inspectOpening(JSONArray content,StringBuilder diagnostic,int depth) throws JSONException {
        if(content==null || diagnostic.length()>9000)return;
        for(int i=0;i<content.length() && diagnostic.length()<9000 && i<65;i++){
            JSONObject item=content.optJSONObject(i);
            if(item==null)continue;
            JSONObject paragraph=item.optJSONObject("paragraph");
            if(paragraph!=null){
                StringBuilder line=new StringBuilder();
                collectText(paragraph,line);
                String title=line.toString().replace('\n',' ').trim();
                if(title.length()>105)title=title.substring(0,105)+"…";
                diagnostic.append("P").append(i+1)
                    .append(paragraph.has("bullet")?" [liste] ":" ")
                    .append(title).append("\\n");
            }
            JSONObject table=item.optJSONObject("table");
            if(table!=null){
                JSONArray rows=table.optJSONArray("tableRows");
                diagnostic.append("TABLEAU ").append(rows==null?0:rows.length()).append(" lignes\\n");
                if(rows!=null)for(int row=0;row<Math.min(rows.length(),36);row++){
                    JSONObject obj=rows.optJSONObject(row);
                    JSONArray cells=obj==null?null:obj.optJSONArray("tableCells");
                    diagnostic.append("  ").append(row+1).append(": ");
                    if(cells!=null)for(int col=0;col<cells.length();col++){
                        JSONObject cell=cells.optJSONObject(col);
                        StringBuilder cellText=new StringBuilder();
                        if(cell!=null)collectText(cell,cellText);
                        String txt=cellText.toString().replace('\n',' ').trim();
                        if(txt.length()>85)txt=txt.substring(0,85)+"…";
                        diagnostic.append("[").append(txt).append("] ");
                    }
                    diagnostic.append("\\n");
                }
            }
        }
    }
    private void showDiagnostic(String message){
        results.removeAllViews();boxes.clear();missing.clear();
        status.setText("Index non reconnu. Aucun titre proposé. Structure de début du document :");
        TextView details=new TextView(this);
        details.setTextColor(Color.WHITE);
        details.setTextSize(13);
        details.setText(message);
        results.addView(details);
        Button copy=new Button(this);
        copy.setText("Copier le diagnostic");
        copy.setOnClickListener(v->{
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostic TS 2026",message));
            Toast.makeText(this,"Diagnostic copié",Toast.LENGTH_SHORT).show();
        });
        results.addView(copy);
    }


    // TS 2026 is the second row, first two cells of the opening 70-row table.
    // Preserve the raw overview because titles and BPM are concatenated.
    private static String readTs2026Overview(JSONObject body) throws JSONException {
        if(body==null)return "";
        JSONArray content=body.optJSONArray("content");
        if(content==null)return "";
        for(int i=0;i<Math.min(8,content.length());i++){
            JSONObject block=content.optJSONObject(i);
            JSONObject table=block==null?null:block.optJSONObject("table");
            JSONArray rows=table==null?null:table.optJSONArray("tableRows");
            if(rows==null||rows.length()<3)continue;
            JSONObject header=rows.optJSONObject(0);
            JSONArray hc=header==null?null:header.optJSONArray("tableCells");
            if(hc==null||hc.length()<2)continue;
            StringBuilder label=new StringBuilder();
            collectText(hc.optJSONObject(0),label);
            if(!label.toString().toUpperCase(Locale.ROOT).contains("PLAYLIST 2026"))continue;
            JSONObject second=rows.optJSONObject(1);
            JSONArray cells=second==null?null:second.optJSONArray("tableCells");
            if(cells==null||cells.length()<2)return "";
            StringBuilder overview=new StringBuilder();
            collectText(cells.optJSONObject(0),overview);
            overview.append("\\n--- COLONNE 2 ---\\n");
            collectText(cells.optJSONObject(1),overview);
            return overview.toString();
        }
        return "";
    }

    private static String titleFromOverviewLine(String line){
        String cleaned=line.trim();
        if(cleaned.isEmpty()||cleaned.startsWith("--- COLONNE"))return "";
        // Preserve the raw line for the UI; strip only trailing performance metadata
        // for matching against the Manager song title.
        cleaned=cleaned.replaceFirst("\\s*[🎸🎹⏩⇻➤🔺🔻🔀🔁].*$","");
        cleaned=cleaned.replaceFirst("\\s+(?:\\.{2,}\\s*)?(?:\\d{2,3})(?:\\s*BPM)?\\s*$","");
        return cleaned.trim();
    }
    private void showOverview(String overview){
        results.removeAllViews();boxes.clear();missing.clear();
        SetListModel playlist=AppStore.findSetlist(this,playlistId);
        if(playlist==null)return;
        LinkedHashMap<String,Song> manager=new LinkedHashMap<>();
        for(String songId:playlist.songIds){
            Song song=AppStore.findSong(this,songId);
            if(song!=null)manager.put(key(song.title),song);
        }
        LinkedHashMap<String,String> docs=new LinkedHashMap<>();
        String[] lines=overview.replace("\\n","\n").split("\n");
        for(String raw:lines){
            String title=titleFromOverviewLine(raw);
            if(title.length()<3 || title.length()>100)continue;
            docs.putIfAbsent(key(title),raw.trim());
        }
        int matched=0;
        for(String titleKey:docs.keySet())if(manager.containsKey(titleKey))matched++;
        // Fail closed: no automatic imports, even if the format is ambiguous.
        status.setText("Comparaison TS 2026 (lecture seule) : "+docs.size()+
            " lignes Docs, "+manager.size()+" titres Manager, "+matched+
            " correspondances exactes. BPM, icônes et annotations conservés ci-dessous.");
        TextView intro=new TextView(this);
        intro.setTextColor(Color.LTGRAY);
        intro.setText("DOCS UNIQUEMENT / À VÉRIFIER :");
        results.addView(intro);
        int docsOnly=0;
        for(Map.Entry<String,String> entry:docs.entrySet()){
            if(manager.containsKey(entry.getKey()))continue;
            docsOnly++;
            TextView row=new TextView(this);
            row.setTextColor(Color.WHITE);row.setTextSize(16);
            row.setPadding(0,9,0,9);
            row.setText("Docs → "+entry.getValue());
            results.addView(row);
        }
        if(docsOnly==0){
            TextView none=new TextView(this);none.setTextColor(Color.LTGRAY);
            none.setText("Aucune différence détectée.");results.addView(none);
        }
        TextView heading=new TextView(this);
        heading.setTextColor(Color.LTGRAY);
        heading.setText("\nMANAGER UNIQUEMENT / À VÉRIFIER :");
        results.addView(heading);
        int managerOnly=0;
        for(Map.Entry<String,Song> entry:manager.entrySet()){
            if(docs.containsKey(entry.getKey()))continue;
            managerOnly++;
            Song song=entry.getValue();
            TextView row=new TextView(this);
            row.setTextColor(Color.WHITE);row.setTextSize(16);
            row.setPadding(0,9,0,9);
            row.setText("Manager → "+song.title+" · "+song.bpm+" BPM");
            results.addView(row);
        }
        if(managerOnly==0){
            TextView none=new TextView(this);none.setTextColor(Color.LTGRAY);
            none.setText("Aucune différence détectée.");results.addView(none);
        }
        Button copy=new Button(this);
        copy.setText("Copier le sommaire brut pour vérifier les BPM et icônes");
        copy.setOnClickListener(v->{
            android.content.ClipboardManager managerClipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            managerClipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sommaire TS 2026",overview));
            Toast.makeText(this,"Sommaire copié",Toast.LENGTH_SHORT).show();
        });
        results.addView(copy);
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
                StringBuilder layout=new StringBuilder();
                if(tabs!=null && tabs.length()>0){
                    JSONObject firstTab=tabs.optJSONObject(0);
                    JSONObject documentTab=firstTab==null?null:firstTab.optJSONObject("documentTab");
                    JSONObject body=documentTab==null?null:documentTab.optJSONObject("body");
                    inspectOpening(body==null?null:body.optJSONArray("content"),layout,0);
                }else{
                    JSONObject body=doc.optJSONObject("body");
                    inspectOpening(body==null?null:body.optJSONArray("content"),layout,0);
                }
                JSONObject firstBody=null;
                if(tabs!=null && tabs.length()>0){
                    JSONObject first=tabs.optJSONObject(0);
                    JSONObject documentTab=first==null?null:first.optJSONObject("documentTab");
                    firstBody=documentTab==null?null:documentTab.optJSONObject("body");
                }else firstBody=doc.optJSONObject("body");
                String overview=readTs2026Overview(firstBody);
                if(!overview.isEmpty()){
                    runOnUiThread(()->{if(!isFinishing())showOverview(overview);});
                }else{
                    String diagnostic=layout.toString();
                    runOnUiThread(()->{if(!isFinishing())showDiagnostic(diagnostic);});
                }
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
