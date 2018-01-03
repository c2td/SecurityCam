package com.example.test.securitycam;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

/**
 * A service for sending broadcasts to camera app
 */

public class CameraService extends Service {

    /* IntentService is auto threaded and it moves to onDestroy
       quickly after onHandleIntent, so use Service instead
     */

    public static final String NOTIFICATION = ".CameraService.receiver";
    public static final int BROADCAST_DELAY = 20_000;
    private Handler mHandler = new Handler();
    private Timer mTimer;

    private void sendBroadcastToActivity(int result) {
        Intent intent = new Intent(NOTIFICATION);
        intent.putExtra("resultCode", result);
        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null && intent.getIntExtra("resultCode", 0) == ServiceRestarterBroadcastReceiver.RESTARTING) {
            Log.d("--","Service Restarted");
        } else {
            Log.d("--","Service started");
        }

        mTimer = new Timer();

        // schedule tasks

        if (intent != null && intent.getIntExtra("resultCode", 0) == ServiceRestarterBroadcastReceiver.RESTARTING) {
            mTimer.schedule(new OpenCameraAppTask(), 2000);
        } else {
            //mTimer.schedule(new SendBroadcastTask(), BROADCAST_DELAY);
            mTimer.scheduleAtFixedRate(new SendBroadcastTask(), 0, BROADCAST_DELAY);
        }

        return START_STICKY;
    }

    class OpenCameraAppTask extends TimerTask {

        // TODO also investigate JobScheduler

        @Override
        public void run() {
            // run on another thread
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d("--","OpenCameraAppTask");
                    startCameraActivity();
                }
            });
        }
    }

    private void startCameraActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // TODO is this best practice?
        startActivity(intent);
        mTimer.schedule(new SendBroadcastTask(), BROADCAST_DELAY);
    }

    class SendBroadcastTask extends TimerTask {

        // TODO also investigate JobScheduler

        @Override
        public void run() {
            // run on another thread
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d("--","SendBroadcastTask");
                    sendBroadcastToActivity(1);
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("--","--Service destroyed");
        mTimer = null;
        Intent broadcastIntent = new Intent("ServiceRestarter");
        broadcastIntent.putExtra("resultCode", 2);
        sendBroadcast(broadcastIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


}
