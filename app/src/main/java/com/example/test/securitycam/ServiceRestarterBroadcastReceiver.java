package com.example.test.securitycam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ServiceRestarterBroadcastReceiver extends BroadcastReceiver {

    public static final int RESTARTING = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent restartIntent = new Intent(context, CameraService.class);
        restartIntent.putExtra("resultCode", RESTARTING);
        //context.startService(restartIntent);
    }
}
