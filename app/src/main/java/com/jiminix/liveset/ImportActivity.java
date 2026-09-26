package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ImportActivity extends AppCompatActivity {
    private static final int PICK_FILE = 42;
    private EditText source;
    private TextView preview;
    private CheckBox createSetlist;
    private CheckBox replaceDuplicates;
    private EditText setlistName;
    private Button importButton;
    private String targetSetlistId;
    private SetListModel targetSetlist;
    private final List<Song> parsed = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        targetSetlistId=getIntent().getStringExtra("target_setlist_id");
        if(targetSetlistId!=null) targetSetlist=AppStore.findSetlist(this,targetSetlistId);
        buildUi();
    }

    private void buildUi() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.rgb(18,18,18));

        LinearLayout head = Ui.row(this);
        Button back = Ui.button(this, "‹");
        TextView title = Ui.title(this, "Import en masse");
        title.setTextSize(21);
        Ui.weight(title,1);
        head.addView(back);
        head.addView(title);
        outer.addView(head);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),Ui.dp(this,30));
        scroll.addView(root);

        TextView info = new TextView(this);
        info.setText("Depuis Google Docs : tu peux coller tout le texte, ou importer le document en DOCX depuis Google Drive.\n\nEn DOCX, LiveSet reconnaît les styles Titre / Heading comme débuts de chansons. En texte collé, il reconnaît # Titre, « Titre : ... », les séparateurs --- et les tableaux copiés depuis Google Sheets.");
        info.setTextColor(Color.LTGRAY);
        info.setTextSize(15);
        info.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,12));
        root.addView(info);

        LinearLayout actions = Ui.row(this);
        Button paste = Ui.button(this,"📋 Coller");
        Button file = Ui.button(this,"📁 Ouvrir fichier");
        Ui.weight(paste,1); Ui.weight(file,1);
        actions.addView(paste); actions.addView(file);
        root.addView(actions);

        source = new EditText(this);
        source.setHint("Colle ici tous tes titres et paroles…");
        source.setGravity(Gravity.TOP);
        source.setMinLines(12);
        source.setTextColor(Color.WHITE);
        source.setHintTextColor(Color.GRAY);
        root.addView(source,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,320)));

        LinearLayout parseRow = Ui.row(this);
        Button analyse = Ui.button(this,"Analyser");
        Button clear = Ui.button(this,"Effacer");
        Ui.weight(analyse,2); Ui.weight(clear,1);
        parseRow.addView(analyse); parseRow.addView(clear);
        root.addView(parseRow);

        if(targetSetlist!=null){
            TextView dest=new TextView(this);
            dest.setText("Destination : " + targetSetlist.name);
            dest.setTextColor(Color.rgb(255,193,7));
            dest.setTextSize(17);
            dest.setPadding(Ui.dp(this,6),Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,2));
            root.addView(dest);
        }

        preview = new TextView(this);
        preview.setText("Aucun morceau analysé.");
        preview.setTextColor(Color.WHITE);
        preview.setTextSize(16);
        preview.setPadding(Ui.dp(this,6),Ui.dp(this,14),Ui.dp(this,6),Ui.dp(this,14));
        root.addView(preview);

        createSetlist = new CheckBox(this);
        createSetlist.setText("Créer aussi une setlist avec les morceaux importés");
        createSetlist.setTextColor(Color.WHITE);
        createSetlist.setChecked(true);
        root.addView(createSetlist);

        setlistName = new EditText(this);
        setlistName.setHint("Nom de la setlist");
        setlistName.setText("Import Google");
        setlistName.setTextColor(Color.WHITE);
        setlistName.setHintTextColor(Color.GRAY);
        root.addView(setlistName);

        if(targetSetlist!=null){
            createSetlist.setChecked(false);
            createSetlist.setVisibility(View.GONE);
            setlistName.setVisibility(View.GONE);
        }

        replaceDuplicates = new CheckBox(this);
        replaceDuplicates.setText("Remplacer les doublons (même titre + artiste)");
        replaceDuplicates.setTextColor(Color.WHITE);
        replaceDuplicates.setChecked(false);
        root.addView(replaceDuplicates);

        outer.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        importButton = Ui.button(this,"IMPORTER");
        importButton.setEnabled(false);
        importButton.setTextSize(18);
        outer.addView(importButton,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,60)));

        Ui.applySafeArea(outer); setContentView(outer);

        back.setOnClickListener(v->finish());
        paste.setOnClickListener(v->pasteClipboard());
        file.setOnClickListener(v->pickFile());
        analyse.setOnClickListener(v->analyse());
        clear.setOnClickListener(v->{ source.setText(""); parsed.clear(); renderPreview(); });
        importButton.setOnClickListener(v->importSongs());
        createSetlist.setOnCheckedChangeListener((bttn,checked)->setlistName.setVisibility(checked?View.VISIBLE:View.GONE));
    }

    private void pasteClipboard() {
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm==null||!cm.hasPrimaryClip()){Toast.makeText(this,"Presse-papiers vide.",Toast.LENGTH_SHORT).show();return;}
        ClipData c=cm.getPrimaryClip();
        if(c==null||c.getItemCount()==0)return;
        CharSequence txt=c.getItemAt(0).coerceToText(this);
        source.setText(txt);
        analyse();
    }

    private void pickFile() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{
            "text/plain","text/csv","text/tab-separated-values",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        });
        startActivityForResult(i,PICK_FILE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_FILE&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{
                String txt=readUri(uri);
                if(txt==null || txt.trim().isEmpty()){
                    throw new Exception("Le document sélectionné ne fournit aucun texte. Pour un Google Doc natif, utilise « Envoyer une copie » en DOCX, ou copie-colle tout le document.");
                }
                source.setText(txt);
                analyse();
                if(parsed.isEmpty()){
                    new AlertDialog.Builder(this)
                        .setTitle("0 morceau détecté")
                        .setMessage("Le fichier a bien été lu, mais LiveSet n’a pas reconnu la séparation entre les chansons. Essaie le DOCX avec les noms de morceaux en style Titre, ou colle le texte complet.")
                        .setPositiveButton("OK",null).show();
                }
            }catch(Exception e){
                new AlertDialog.Builder(this).setTitle("Import impossible").setMessage(e.getMessage()==null?"Fichier non lisible.":e.getMessage()).setPositiveButton("OK",null).show();
            }
        }
    }

    private String readUri(Uri uri) throws Exception {
        String name=fileName(uri).toLowerCase(Locale.ROOT);
        String mime=getContentResolver().getType(uri);
        if(name.endsWith(".docx") || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime)){
            return readDocx(uri);
        }
        StringBuilder sb=new StringBuilder();
        try(InputStream in=getContentResolver().openInputStream(uri);
            BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            String line;
            while((line=br.readLine())!=null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private String readDocx(Uri uri) throws Exception {
        try(InputStream in=getContentResolver().openInputStream(uri); ZipInputStream zip=new ZipInputStream(in)){
            ZipEntry e;
            while((e=zip.getNextEntry())!=null){
                if("word/document.xml".equals(e.getName())){
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    byte[] buf=new byte[8192]; int n;
                    while((n=zip.read(buf))>0) out.write(buf,0,n);
                    return docxXmlToText(out.toString("UTF-8"));
                }
            }
        }
        throw new Exception("Ce fichier DOCX ne contient pas de texte lisible.");
    }

    private String docxXmlToText(String xml) {
        StringBuilder result=new StringBuilder();
        Pattern paragraph=Pattern.compile("(?s)<w:p\\b.*?</w:p>");
        Pattern stylePattern=Pattern.compile("w:pStyle[^>]*w:val=\\\"([^\\\"]+)\\\"");
        Matcher pm=paragraph.matcher(xml);
        while(pm.find()){
            String p=pm.group();
            Matcher sm=stylePattern.matcher(p);
            String style=sm.find()?sm.group(1).toLowerCase(Locale.ROOT):"";
            boolean heading=style.equals("title")||style.equals("titre")||style.matches("heading[1-6]")||style.matches("titre[1-6]");
            String text=p.replaceAll("<w:tab[^>]*/>","\\t").replaceAll("<w:br[^>]*/>","\\n").replaceAll("<[^>]+>","");
            text=decodeXml(text).trim();
            if(text.isEmpty()){
                result.append('\n');
            }else{
                if(heading)result.append("# ");
                result.append(text).append('\n');
            }
        }
        return result.toString();
    }

    private String decodeXml(String text) {
        return text.replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\\\"").replace("&apos;","'");
    }

    private String fileName(Uri uri){
        String result="";
        try(android.database.Cursor c=getContentResolver().query(uri,null,null,null,null)){
            if(c!=null&&c.moveToFirst()){
                int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(i>=0)result=c.getString(i);
            }
        }catch(Exception ignored){}
        return result==null?"":result;
    }

    private void analyse() {
        parsed.clear();
        String raw=source.getText().toString().replace("\r\n","\n").replace('\r','\n').trim();
        if(raw.isEmpty()){renderPreview();return;}

        if(looksLikeTsv(raw)) parseTsv(raw);
        if(parsed.isEmpty()) parseHeadings(raw);
        if(parsed.isEmpty()) parseTitleLabels(raw);
        if(parsed.isEmpty()) parseSeparators(raw);
        if(parsed.isEmpty()) parseTripleBlankBlocks(raw);

        parsed.removeIf(s->s.title.trim().isEmpty());
        renderPreview();
    }

    private boolean looksLikeTsv(String raw){
        String first=raw.split("\n",2)[0].toLowerCase(Locale.ROOT);
        return first.contains("\t") && (first.contains("titre")||first.contains("title"));
    }

    private void parseTsv(String raw){
        String[] lines=raw.split("\n");
        if(lines.length<2)return;
        String[] headers=lines[0].split("\t",-1);
        Map<String,Integer> col=new HashMap<>();
        for(int i=0;i<headers.length;i++)col.put(norm(headers[i]),i);
        Integer titleCol=firstCol(col,"titre","title","chanson","song");
        if(titleCol==null)return;
        for(int r=1;r<lines.length;r++){
            if(lines[r].trim().isEmpty())continue;
            String[] v=lines[r].split("\t",-1);
            Song s=new Song();
            s.title=val(v,titleCol);
            s.artist=val(v,firstCol(col,"artiste","artist","groupe","band"));
            s.lyrics=val(v,firstCol(col,"paroles","lyrics","texte"));
            s.key=val(v,firstCol(col,"tonalite","key"));
            s.bpm=val(v,firstCol(col,"bpm","tempo"));
            s.tuning=val(v,firstCol(col,"accordage","tuning"));
            s.capo=val(v,firstCol(col,"capo"));
            s.duration=val(v,firstCol(col,"duree","duration"));
            s.singer=val(v,firstCol(col,"chanteur","singer","chant"));
            s.guitar=val(v,firstCol(col,"guitare","guitar","instrument"));
            s.notes=val(v,firstCol(col,"notes","note"));
            s.mediaUrl=val(v,firstCol(col,"youtube","lien","url","media"));
            if(!s.title.trim().isEmpty())parsed.add(s);
        }
    }

    private Integer firstCol(Map<String,Integer> c,String... names){
        for(String n:names)if(c.containsKey(norm(n)))return c.get(norm(n)); return null;
    }
    private String val(String[] v,Integer i){return i==null||i<0||i>=v.length?"":v[i].replace("\\n","\n").trim();}
    private String norm(String s){return s.toLowerCase(Locale.ROOT).replace("é","e").replace("è","e").replace("ê","e").replace("à","a").replace("ô","o").replace("ï","i").replaceAll("[^a-z0-9]","");}

    private void parseHeadings(String raw){
        Pattern p=Pattern.compile("(?m)^#{1,6}\\s+(.+?)\\s*$");
        Matcher m=p.matcher(raw);
        List<Integer> starts=new ArrayList<>(), ends=new ArrayList<>(); List<String> titles=new ArrayList<>();
        while(m.find()){starts.add(m.start());ends.add(m.end());titles.add(m.group(1).trim());}
        if(titles.size()<2)return;
        for(int i=0;i<titles.size();i++){
            int bodyStart=ends.get(i); int bodyEnd=i+1<titles.size()?starts.get(i+1):raw.length();
            Song s=new Song(); setTitleArtist(s,titles.get(i)); s.lyrics=raw.substring(bodyStart,bodyEnd).trim(); parsed.add(s);
        }
    }

    private void parseTitleLabels(String raw){
        Pattern p=Pattern.compile("(?mi)^(?:titre|title)\\s*:\\s*(.+?)\\s*$");
        Matcher m=p.matcher(raw);
        List<Integer> starts=new ArrayList<>(), ends=new ArrayList<>(); List<String> titles=new ArrayList<>();
        while(m.find()){starts.add(m.start());ends.add(m.end());titles.add(m.group(1).trim());}
        if(titles.size()<2)return;
        for(int i=0;i<titles.size();i++){
            int bodyStart=ends.get(i); int bodyEnd=i+1<titles.size()?starts.get(i+1):raw.length();
            Song s=new Song(); setTitleArtist(s,titles.get(i)); s.lyrics=raw.substring(bodyStart,bodyEnd).trim(); parsed.add(s);
        }
    }

    private void parseSeparators(String raw){
        String[] blocks=raw.split("(?m)^\\s*-{3,}\\s*$");
        if(blocks.length<2)return;
        for(String b:blocks)addBlock(b);
    }

    private void parseTripleBlankBlocks(String raw){
        String[] blocks=raw.split("\n\\s*\n\\s*\n+");
        if(blocks.length<2)return;
        for(String b:blocks)addBlock(b);
    }

    private void addBlock(String block){
        String b=block.trim(); if(b.isEmpty())return;
        String[] lines=b.split("\n",2);
        Song s=new Song(); setTitleArtist(s,lines[0].trim());
        s.lyrics=lines.length>1?lines[1].trim():"";
        parsed.add(s);
    }

    private void setTitleArtist(Song s,String header){
        String h=header.replaceAll("^[-•*\\s]+","").trim();
        String[] parts=h.split("\\s+[—–]\\s+|\\s+\\|\\s+",2);
        s.title=parts[0].trim();
        if(parts.length>1)s.artist=parts[1].trim();
    }

    private void renderPreview(){
        if(parsed.isEmpty()){
            preview.setText("Aucun morceau détecté.\n\nAstuce : dans Google Docs, applique le style « Titre » aux noms des chansons, ou mets --- entre deux morceaux.");
            importButton.setEnabled(false);
            return;
        }
        StringBuilder sb=new StringBuilder();
        sb.append(parsed.size()).append(" morceau").append(parsed.size()>1?"x":"").append(" détecté").append(parsed.size()>1?"s":"").append(" :\n\n");
        int max=Math.min(parsed.size(),25);
        for(int i=0;i<max;i++){
            Song s=parsed.get(i);
            sb.append(i+1).append(". ").append(s.title);
            if(!s.artist.isEmpty())sb.append(" — ").append(s.artist);
            if(!s.lyrics.isEmpty())sb.append("  ·  ").append(s.lyrics.split("\n").length).append(" lignes");
            sb.append('\n');
        }
        if(parsed.size()>max)sb.append("\n… +").append(parsed.size()-max).append(" autres");
        preview.setText(sb.toString());
        importButton.setEnabled(true);
    }

    private void importSongs(){
        if(parsed.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Rien à importer").setMessage("Aucun morceau n’a été détecté.").setPositiveButton("OK",null).show();
            return;
        }

        List<Song> existing=AppStore.loadSongs(this);
        Set<String> keys=new HashSet<>();
        Map<String,Song> byKey=new HashMap<>();
        for(Song s:existing){
            String k=dupKey(s);
            keys.add(k);
            byKey.put(k,s);
        }

        List<String> importedIds=new ArrayList<>();
        int added=0,replaced=0,reused=0;

        for(Song in:parsed){
            String k=dupKey(in);
            if(keys.contains(k)){
                Song old=byKey.get(k);
                if(replaceDuplicates.isChecked()){
                    in.id=old.id;
                    AppStore.upsertSong(this,in);
                    importedIds.add(in.id);
                    replaced++;
                }else{
                    importedIds.add(old.id);
                    reused++;
                }
            }else{
                AppStore.upsertSong(this,in);
                keys.add(k);
                byKey.put(k,in);
                importedIds.add(in.id);
                added++;
            }
        }

        int playlistAdds=0;
        if(targetSetlist!=null){
            Set<String> already=new HashSet<>(targetSetlist.songIds);
            for(String id:importedIds){
                if(!already.contains(id)){
                    targetSetlist.songIds.add(id);
                    already.add(id);
                    playlistAdds++;
                }
            }
            AppStore.upsertSetlist(this,targetSetlist);
        }else if(createSetlist.isChecked()&&!importedIds.isEmpty()){
            SetListModel sl=new SetListModel();
            String n=setlistName.getText().toString().trim();
            sl.name=n.isEmpty()?"Import Google":n;
            sl.songIds.addAll(importedIds);
            AppStore.upsertSetlist(this,sl);
            playlistAdds=importedIds.size();
        }

        String destination=targetSetlist!=null
            ? "\n\n"+playlistAdds+" morceau(x) ajouté(s) à « "+targetSetlist.name+" »."
            : (createSetlist.isChecked() ? "\n\nSetlist créée avec "+playlistAdds+" morceau(x)." : "");

        new AlertDialog.Builder(this)
            .setTitle("Import terminé")
            .setMessage(
                parsed.size()+" morceau(x) détecté(s)\n"+
                added+" nouveau(x)\n"+
                reused+" déjà présent(s) réutilisé(s)\n"+
                replaced+" remplacé(s)"+
                destination
            )
            .setPositiveButton("OK",(d,w)->finish()).show();
    }

    private String dupKey(Song s){
        return (s.title.trim()+"|"+s.artist.trim()).toLowerCase(Locale.ROOT);
    }
}
