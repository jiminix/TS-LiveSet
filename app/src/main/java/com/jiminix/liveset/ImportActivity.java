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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
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
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.StringReader;

public class ImportActivity extends AppCompatActivity {
    // V0.23 numbered DOCX parser
    // V0.23 rebuild
    // V0.23 rebuild 2
    // V0.62 compact lyrics on every import
    private static final int PICK_FILE = 42;
    private EditText source;
    private TextView preview;
    private CheckBox createSetlist;
    private CheckBox replaceDuplicates;
    private EditText setlistName;
    private Button importButton;
    private String targetSetlistId;
    private SetListModel targetSetlist;
    private boolean tsSongbookDetected = false;
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
        Ui.compactHeaderTitle(title,this);
        Ui.compactHeaderButton(back,this,46);
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
                String name=fileName(uri).toLowerCase(Locale.ROOT);
                String mime=getContentResolver().getType(uri);
                boolean docx=name.endsWith(".docx") ||
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime);

                if(docx){
                    List<Song> direct=parseTs2026DocxTable(uri);
                    if(direct!=null && !direct.isEmpty()){
                        parsed.clear();
                        parsed.addAll(direct);
                        tsSongbookDetected=true;
                        replaceDuplicates.setChecked(true);
                        if(targetSetlist==null) setlistName.setText("PLAYLIST 2026");
                        source.setText("T S. 2026.docx\n\nTableau Word reconnu directement.\n"+parsed.size()+" morceaux prêts à être importés.");
                        renderPreview();
                        return;
                    }

                    List<Song> numbered=parseNumberedSongDocx(uri);
                    if(numbered!=null && numbered.size()>=5){
                        parsed.clear();
                        parsed.addAll(numbered);
                        tsSongbookDetected=true;
                        replaceDuplicates.setChecked(true);
                        source.setText(fileName(uri)+"\n\nDocument numéroté reconnu directement.\n"+parsed.size()+" morceaux avec paroles prêts à être importés.");
                        renderPreview();
                        return;
                    }
                }

                String txt=readUri(uri);
                if(txt==null || txt.trim().isEmpty()){
                    throw new Exception("Le document sélectionné ne fournit aucun texte lisible.");
                }
                source.setText(txt);
                analyse();
                if(parsed.isEmpty()){
                    new AlertDialog.Builder(this)
                        .setTitle("0 morceau détecté")
                        .setMessage("Le fichier a bien été lu mais aucun morceau n’a été reconnu.")
                        .setPositiveButton("OK",null).show();
                }
            }catch(Exception e){
                new AlertDialog.Builder(this)
                    .setTitle("Import impossible")
                    .setMessage(e.getMessage()==null ? e.getClass().getSimpleName() : e.getMessage())
                    .setPositiveButton("OK",null).show();
            }
        }
    }

    private static final String W_NS="http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private List<Song> parseTs2026DocxTable(Uri uri) throws Exception {
        String xml=readDocxXml(uri);
        if(xml==null || xml.isEmpty())return null;

        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc=factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList tables=doc.getElementsByTagNameNS(W_NS,"tbl");

        Element target=null;
        List<Element> rows=null;
        for(int t=0;t<tables.getLength();t++){
            Element table=(Element)tables.item(t);
            List<Element> candidateRows=directChildren(table,"tr");
            if(candidateRows.isEmpty())continue;
            String first=rowText(candidateRows.get(0));
            if(normalizeSongTitle(first).contains("PLAYLIST 2026")){
                target=table;
                rows=candidateRows;
                break;
            }
        }
        if(target==null || rows==null || rows.size()<3)return null;

        // Row 1 contains PLAYLIST 2026, spread over the first two cells.
        List<Song> songs=new ArrayList<>();
        List<Element> playlistCells=directChildren(rows.get(1),"tc");
        int cellsToRead=Math.min(2,playlistCells.size());
        for(int ci=0;ci<cellsToRead;ci++){
            for(String line:cellParagraphs(playlistCells.get(ci))){
                String cleaned=line.trim();
                if(cleaned.isEmpty())continue;
                String title=parsePlaylistTitle(cleaned);
                if(title.isEmpty())continue;
                Song s=new Song();
                s.title=title;
                s.bpm=extractPlaylistBpm(cleaned);
                songs.add(s);
            }
        }

        if(songs.size()<20 || songs.size()>40)return null;

        // Match each playlist title to its actual title row in the same Word table.
        Map<Integer,Integer> songToRow=new HashMap<>();
        Set<Integer> usedRows=new HashSet<>();

        for(int si=0;si<songs.size();si++){
            double best=0.0;
            int bestRow=-1;
            for(int ri=2;ri<rows.size();ri++){
                if(usedRows.contains(ri))continue;
                List<Element> cells=directChildren(rows.get(ri),"tc");
                if(cells.isEmpty())continue;
                String candidate=cellText(cells.get(0)).trim();
                if(candidate.isEmpty() || candidate.length()>100)continue;

                double score=titleScore(songs.get(si).title,candidate);
                if(score>best){
                    best=score;
                    bestRow=ri;
                }
            }
            if(bestRow>=0 && best>=0.55){
                songToRow.put(si,bestRow);
                usedRows.add(bestRow);
            }
        }

        for(int si=0;si<songs.size();si++){
            Integer titleRow=songToRow.get(si);
            if(titleRow==null)continue;

            int bodyRow=titleRow+1;

            // Some songs have the title duplicated on two consecutive rows.
            while(bodyRow<rows.size()){
                List<Element> cells=directChildren(rows.get(bodyRow),"tc");
                if(cells.isEmpty()){bodyRow++;continue;}
                String first=cellText(cells.get(0)).trim();
                if(first.isEmpty())break;
                if(first.length()<=100 && titleScore(songs.get(si).title,first)>=0.80){
                    bodyRow++;
                    continue;
                }
                break;
            }

            if(bodyRow>=rows.size())continue;
            List<Element> bodyCells=directChildren(rows.get(bodyRow),"tc");
            if(bodyCells.isEmpty())continue;

            List<String> parts=new ArrayList<>();
            int bodyCellCount=Math.min(2,bodyCells.size());
            for(int ci=0;ci<bodyCellCount;ci++){
                String part=cellText(bodyCells.get(ci)).trim();
                if(part.isEmpty())continue;
                if(part.contains("⇻") || part.contains("🔺"))continue;
                boolean duplicate=false;
                for(String old:parts){
                    if(compact(old).equals(compact(part))){duplicate=true;break;}
                }
                if(!duplicate)parts.add(part);
            }
            songs.get(si).lyrics=String.join("\n\n",parts).trim();
        }

        return songs;
    }

    private List<Song> parseNumberedSongDocx(Uri uri) throws Exception {
        String xml=readDocxXml(uri);
        if(xml==null || xml.isEmpty())return null;

        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc=factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList ps=doc.getElementsByTagNameNS(W_NS,"p");

        Pattern headingPattern=Pattern.compile("^\\s*(\\d{1,2})\\s*(?:[.\\-]|\\s)\\s*(.+?)\\s*$");
        List<Integer> headingPara=new ArrayList<>();
        List<Integer> headingNum=new ArrayList<>();
        List<String> headingTitle=new ArrayList<>();

        int expected=1;
        boolean sequenceStarted=false;

        for(int i=0;i<ps.getLength();i++){
            Element p=(Element)ps.item(i);
            String text=paragraphText(p).trim().replaceAll("\\s+"," ");
            if(text.isEmpty())continue;

            Matcher m=headingPattern.matcher(text);
            if(!m.matches())continue;

            int n;
            try{ n=Integer.parseInt(m.group(1)); }catch(Exception e){ continue; }
            String title=m.group(2).trim();
            if(title.length()<3)continue;

            if(!sequenceStarted){
                if(n!=1)continue;
                sequenceStarted=true;
                expected=1;
            }

            if(n==expected){
                headingPara.add(i);
                headingNum.add(n);
                headingTitle.add(title);
                expected++;
            }
        }

        if(headingPara.size()<5)return null;

        List<Song> songs=new ArrayList<>();
        for(int h=0;h<headingPara.size();h++){
            int from=headingPara.get(h)+1;
            int to=(h+1<headingPara.size())?headingPara.get(h+1):ps.getLength();

            Song s=new Song();
            s.title=headingTitle.get(h)
                .replaceFirst("(?i)\\s+(?:INF|INTRO|FAIRE|EN\\s+DO|93)\\b.*$","")
                .trim();

            StringBuilder body=new StringBuilder();
            for(int i=from;i<to;i++){
                Element p=(Element)ps.item(i);
                String line=paragraphText(p).trim();
                if(line.isEmpty()){
                    if(body.length()>0 && body.charAt(body.length()-1)!='\n')body.append('\n');
                    continue;
                }

                // Ignore page-list/navigation artefacts, but keep musical notes and lyrics.
                if(line.matches("^\\d{1,2}$"))continue;

                if(body.length()>0)body.append('\n');
                body.append(line);
            }
            s.lyrics=body.toString().trim();
            songs.add(s);
        }

        return songs;
    }

    private String readDocxXml(Uri uri) throws Exception {
        try(InputStream in=getContentResolver().openInputStream(uri); ZipInputStream zip=new ZipInputStream(in)){
            ZipEntry e;
            while((e=zip.getNextEntry())!=null){
                if("word/document.xml".equals(e.getName())){
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    byte[] buf=new byte[8192];
                    int n;
                    while((n=zip.read(buf))>0)out.write(buf,0,n);
                    return out.toString("UTF-8");
                }
            }
        }
        return null;
    }

    private List<Element> directChildren(Element parent,String localName){
        List<Element> out=new ArrayList<>();
        Node child=parent.getFirstChild();
        while(child!=null){
            if(child.getNodeType()==Node.ELEMENT_NODE &&
               W_NS.equals(child.getNamespaceURI()) &&
               localName.equals(child.getLocalName())){
                out.add((Element)child);
            }
            child=child.getNextSibling();
        }
        return out;
    }

    private List<String> cellParagraphs(Element cell){
        List<String> lines=new ArrayList<>();
        NodeList ps=cell.getElementsByTagNameNS(W_NS,"p");
        for(int i=0;i<ps.getLength();i++){
            Element p=(Element)ps.item(i);
            String t=paragraphText(p).trim();
            if(!t.isEmpty())lines.add(t);
        }
        return lines;
    }

    private String paragraphText(Element p){
        StringBuilder sb=new StringBuilder();
        NodeList texts=p.getElementsByTagNameNS(W_NS,"t");
        for(int i=0;i<texts.getLength();i++)sb.append(texts.item(i).getTextContent());
        return sb.toString();
    }

    private String cellText(Element cell){
        List<String> lines=cellParagraphs(cell);
        return String.join("\n",lines);
    }

    private String rowText(Element row){
        StringBuilder sb=new StringBuilder();
        for(Element cell:directChildren(row,"tc")){
            if(sb.length()>0)sb.append(" ");
            sb.append(cellText(cell));
        }
        return sb.toString();
    }

    private String compact(String s){
        return s==null ? "" : s.replaceAll("\\s+"," ").trim();
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
        tsSongbookDetected=false;
        String raw=source.getText().toString().replace("\r\n","\n").replace('\r','\n').trim();
        if(raw.isEmpty()){renderPreview();return;}

        if(parseTs2026Songbook(raw)){
            tsSongbookDetected=true;
            replaceDuplicates.setChecked(true);
            if(targetSetlist==null) setlistName.setText("PLAYLIST 2026");
            renderPreview();
            return;
        }

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

    private boolean parseTs2026Songbook(String raw){
        String[] lines=raw.split("\n",-1);
        int playlist=-1;
        for(int i=0;i<lines.length;i++){
            String t=stripHeading(lines[i]);
            if("PLAYLIST 2026".equalsIgnoreCase(t)){ playlist=i; break; }
        }
        if(playlist<0)return false;

        List<Song> songs=new ArrayList<>();
        for(int i=playlist+1;i<lines.length;i++){
            String t=lines[i].trim();
            if(t.startsWith("# "))break;
            if(t.isEmpty())continue;

            if(songs.size()>=5 && t.length()==1 && "PLAYLIST2025".contains(t.toUpperCase(Locale.ROOT))) break;

            String title=parsePlaylistTitle(t);
            if(title.isEmpty())continue;

            Song s=new Song();
            s.title=title;
            s.bpm=extractPlaylistBpm(t);
            songs.add(s);

            if(songs.size()>=80)break;
        }

        // This document's PLAYLIST 2026 is a real setlist; reject accidental matches.
        if(songs.size()<10 || songs.size()>60)return false;

        List<Integer> matchLines=new ArrayList<>();
        List<Integer> matchSongs=new ArrayList<>();
        Set<Integer> assigned=new HashSet<>();

        for(int i=playlist+1;i<lines.length;i++){
            String rawLine=lines[i].trim();
            if(!rawLine.startsWith("# "))continue;
            String heading=stripHeading(rawLine);
            if(heading.isEmpty())continue;

            double best=0.0;
            int bestSong=-1;
            for(int s=0;s<songs.size();s++){
                if(assigned.contains(s))continue;
                double score=titleScore(songs.get(s).title,heading);
                if(score>best){best=score;bestSong=s;}
            }

            if(bestSong>=0 && best>=0.55){
                assigned.add(bestSong);
                matchLines.add(i);
                matchSongs.add(bestSong);
            }
        }

        for(int m=0;m<matchLines.size();m++){
            int from=matchLines.get(m)+1;
            int to=(m+1<matchLines.size())?matchLines.get(m+1):lines.length;
            String lyrics=cleanSongBody(lines,from,to);
            songs.get(matchSongs.get(m)).lyrics=lyrics;
        }

        parsed.addAll(songs);
        return true;
    }

    private String stripHeading(String line){
        String t=line==null?"":line.trim();
        while(t.startsWith("#"))t=t.substring(1).trim();
        return t;
    }

    private String parsePlaylistTitle(String line){
        String x=line.trim().replaceFirst("^\\d+\\.\\s*","");
        int guitar=x.indexOf("🎸");
        int keys=x.indexOf("🎹");
        int cut=-1;
        if(guitar>=0)cut=guitar;
        if(keys>=0&&(cut<0||keys<cut))cut=keys;
        if(cut>=0)x=x.substring(0,cut);

        x=x.replaceFirst("\\s+\\.\\..*$","");
        x=x.replaceFirst("\\s+\\d{2,3}(?:\\D.*)?$","");
        return x.trim();
    }

    private String extractPlaylistBpm(String line){
        Matcher m=Pattern.compile("(?<!\\d)(\\d{2,3})(?!\\d)").matcher(line);
        String found="";
        while(m.find()){
            try{
                int n=Integer.parseInt(m.group(1));
                if(n>=60&&n<=220)found=String.valueOf(n);
            }catch(Exception ignored){}
        }
        return found;
    }

    private String normalizeSongTitle(String value){
        String n=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","");
        n=n.toUpperCase(Locale.ROOT)
            .replace('’','\'')
            .replace("MEDDLEY","MIX")
            .replace("MEDLEY","MIX")
            .replace("JAIL HOUND DOG","HOUND DOG");
        n=n.replaceAll("[^A-Z0-9]+"," ").trim().replaceAll("\\s+"," ");
        n=n.replaceAll("\\b\\d{1,3}\\b"," ").replaceAll("\\s+"," ").trim();
        return n;
    }

    private double titleScore(String expected,String candidate){
        String a=normalizeSongTitle(expected);
        String b=normalizeSongTitle(candidate);
        if(a.isEmpty()||b.isEmpty())return 0.0;
        if(a.equals(b))return 1.0;
        if(a.contains(b)||b.contains(a)){
            double ratio=(double)Math.min(a.length(),b.length())/(double)Math.max(a.length(),b.length());
            return Math.min(1.0,ratio+0.15);
        }

        Set<String> aa=new HashSet<>(Arrays.asList(a.split(" ")));
        Set<String> bb=new HashSet<>(Arrays.asList(b.split(" ")));
        Set<String> both=new HashSet<>(aa);
        both.retainAll(bb);
        if(aa.isEmpty()||bb.isEmpty())return 0.0;
        return (2.0*both.size())/(aa.size()+bb.size());
    }

    private String cleanSongBody(String[] lines,int from,int to){
        List<String> body=new ArrayList<>();
        for(int i=from;i<to;i++){
            String t=lines[i].trim();

            // Page navigation in this songbook: 01 / ⇻ / vertical title / 🔺.
            if(t.contains("⇻")){
                if(!body.isEmpty() && body.get(body.size()-1).matches("\\d{1,2}")) body.remove(body.size()-1);
                break;
            }

            if(t.startsWith("# "))t=stripHeading(t);
            body.add(t);
        }

        while(!body.isEmpty()&&body.get(body.size()-1).trim().isEmpty())body.remove(body.size()-1);
        while(!body.isEmpty()&&body.get(0).trim().isEmpty())body.remove(0);

        StringBuilder out=new StringBuilder();
        for(String line:body){
            if(out.length()>0)out.append('\n');
            out.append(line);
        }
        return out.toString().trim();
    }

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
        if(tsSongbookDetected){
            sb.append("✅ PLAYLIST 2026 reconnue\n");
            sb.append(parsed.size()).append(" morceaux trouvés dans la playlist du document.\n");
            if(targetSetlist!=null) sb.append("La setlist ouverte sera remise dans cet ordre.\n");
            sb.append("\n");
        }else{
            sb.append(parsed.size()).append(" morceau").append(parsed.size()>1?"x":"").append(" détecté").append(parsed.size()>1?"s":"").append(" :\n\n");
        }
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
            Toast.makeText(this,"Aucun morceau détecté.",Toast.LENGTH_LONG).show();
            return;
        }

        importButton.setEnabled(false);

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
            in.lyrics=compactLyrics(in.lyrics);
            String k=dupKey(in);
            if(keys.contains(k)){
                Song old=byKey.get(k);
                if(replaceDuplicates.isChecked() || tsSongbookDetected){
                    preserveExistingData(in,old);
                    in.lyrics=compactLyrics(in.lyrics);
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
        String destinationName="";
        String destinationId=null;

        if(targetSetlist!=null){
            if(tsSongbookDetected){
                targetSetlist.songIds.clear();
                targetSetlist.songIds.addAll(importedIds);
                playlistAdds=importedIds.size();
            }else{
                Set<String> already=new HashSet<>(targetSetlist.songIds);
                for(String id:importedIds){
                    if(!already.contains(id)){
                        targetSetlist.songIds.add(id);
                        already.add(id);
                        playlistAdds++;
                    }
                }
            }
            AppStore.upsertSetlist(this,targetSetlist);
            destinationName=targetSetlist.name;
            destinationId=targetSetlist.id;
        }else if(createSetlist.isChecked()&&!importedIds.isEmpty()){
            SetListModel sl=new SetListModel();
            String n=setlistName.getText().toString().trim();
            sl.name=n.isEmpty()?"Import Google":n;
            sl.songIds.addAll(importedIds);
            AppStore.upsertSetlist(this,sl);
            playlistAdds=importedIds.size();
            destinationName=sl.name;
            destinationId=sl.id;
        }

        String msg=parsed.size()+" détecté(s) · "+added+" nouveau(x) · "+reused+" réutilisé(s)";
        if(replaced>0) msg+=" · "+replaced+" remplacé(s)";
        if(!destinationName.isEmpty()) msg+="\n"+playlistAdds+" dans « "+destinationName+" »";
        Toast.makeText(this,msg,Toast.LENGTH_LONG).show();

        if(targetSetlist!=null){
            setResult(RESULT_OK);
            finish();
        }else if(destinationId!=null){
            Intent i=new Intent(this,SetlistActivity.class);
            i.putExtra("setlist_id",destinationId);
            startActivity(i);
            finish();
        }else{
            finish();
        }
    }

    private String compactLyrics(String raw){
        if(raw==null)return "";
        String normalized=raw.replace("\r\n","\n").replace('\r','\n').replace('\u00A0',' ');
        StringBuilder out=new StringBuilder();
        for(String line:normalized.split("\n",-1)){
            String cleaned=line.replaceAll("[\\t ]+$","");
            if(cleaned.trim().isEmpty())continue;
            if(out.length()>0)out.append('\n');
            out.append(cleaned);
        }
        return out.toString().trim();
    }

    private void preserveExistingData(Song incoming,Song old){
        if(incoming==null || old==null)return;
        if(incoming.title==null || incoming.title.trim().isEmpty()) incoming.title=old.title;
        if(incoming.artist==null || incoming.artist.trim().isEmpty()) incoming.artist=old.artist;
        if(incoming.lyrics==null || incoming.lyrics.trim().isEmpty()) incoming.lyrics=old.lyrics;
        if(incoming.key==null || incoming.key.trim().isEmpty()) incoming.key=old.key;
        if(incoming.bpm==null || incoming.bpm.trim().isEmpty()) incoming.bpm=old.bpm;
        if(incoming.tuning==null || incoming.tuning.trim().isEmpty()) incoming.tuning=old.tuning;
        if(incoming.capo==null || incoming.capo.trim().isEmpty()) incoming.capo=old.capo;
        if(incoming.duration==null || incoming.duration.trim().isEmpty()) incoming.duration=old.duration;
        if(incoming.singer==null || incoming.singer.trim().isEmpty()) incoming.singer=old.singer;
        if(incoming.guitar==null || incoming.guitar.trim().isEmpty()) incoming.guitar=old.guitar;
        if(incoming.notes==null || incoming.notes.trim().isEmpty()) incoming.notes=old.notes;
        if(incoming.mediaUrl==null || incoming.mediaUrl.trim().isEmpty()) incoming.mediaUrl=old.mediaUrl;
    }

    private String dupKey(Song s){
        return (s.title.trim()+"|"+s.artist.trim()).toLowerCase(Locale.ROOT);
    }
}
