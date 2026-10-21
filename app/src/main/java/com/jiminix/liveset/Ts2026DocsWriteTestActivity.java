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

/**
 * Explicit OAuth authorization + append-only write probe on a USER-SUPPLIED COPY.
 * Never targets the source document; no automatic sync, reordering or deletion.
 */
public class Ts2026DocsWriteTestActivity extends Activity {
    private static final int AUTH_REQUEST=2906;
    private EditText copyId;
    private TextView status;
    private String token;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
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
        layout.addView(copyId,new LinearLayout.LayoutParams(-1,-2));
        Button authorize=new Button(this);authorize.setText("Autoriser Google Docs");
        authorize.setOnClickListener(v->authorize());layout.addView(authorize);
        Button write=new Button(this);write.setText("Écrire une ligne TEST dans la copie");
        write.setOnClickListener(v->confirmWrite());layout.addView(write);
        Button index=new Button(this);
        index.setText("Exporter la liste TS 2026 vers la COPIE");
        index.setOnClickListener(v->confirmIndexExport());
        layout.addView(index);
        status=new TextView(this);status.setTextColor(Color.WHITE);
        status.setText("Non connecté. La connexion nécessite la configuration OAuth Android du projet dans Google Cloud.");
        layout.addView(status);
        setContentView(layout);
    }
    private String id(){
        String value=copyId.getText().toString().trim();
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("/document/d/([a-zA-Z0-9_-]+)").matcher(value);
        if(m.find())value=m.group(1);
        return value;
    }
    private boolean validCopy(){
        String id=id();
        if(id.equals(Ts2026GoogleDocsSource.DOCUMENT_ID)){
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
                if(target.equals(Ts2026GoogleDocsSource.DOCUMENT_ID))
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
                if(destination.equals(Ts2026GoogleDocsSource.DOCUMENT_ID))
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

}
