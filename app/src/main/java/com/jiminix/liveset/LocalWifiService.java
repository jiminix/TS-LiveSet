package com.jiminix.liveset;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.content.pm.ServiceInfo;

/** Foreground host: keeps the local read-only viewer available while Chrome is open. */
public class LocalWifiService extends Service {
    private static final String CHANNEL="liveset_local_wifi";
    public static final String ACTION_STOP="com.jiminix.liveset.LOCAL_STOP";
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"LiveSet Wi-Fi local",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){
            LocalWifiServer.stop();stopForeground(true);stopSelf();return START_NOT_STICKY;
        }
        Intent launch=new Intent(this,HomePlaylistsActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("TS Playlist · Viewer local")
            .setContentText("Wi-Fi local actif · http://127.0.0.1:8765")
            .setContentIntent(pi).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(8765,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(8765,notification);
        if(!LocalWifiServer.start(this)){stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    @Override public void onDestroy(){LocalWifiServer.stop();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
