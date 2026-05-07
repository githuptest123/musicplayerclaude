package com.musicplayer.app.services;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

public class HeadsetReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (action.equals(android.content.Intent.ACTION_HEADSET_PLUG)) {
            int state = intent.getIntExtra("state", -1);
            if (state == 0) {
                // Headphones unplugged - pause playback
                Intent serviceIntent = new Intent(context, MusicService.class);
                serviceIntent.setAction(MusicService.ACTION_PAUSE);
                context.startService(serviceIntent);
            }
        }
    }
}
