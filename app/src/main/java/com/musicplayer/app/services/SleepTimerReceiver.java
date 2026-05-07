package com.musicplayer.app.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SleepTimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, MusicService.class);
        serviceIntent.setAction(MusicService.ACTION_PAUSE);
        context.startService(serviceIntent);
    }
}
