package nl.sense_os.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import nl.sense_os.app.SenseSettings;

public class SenseBootRx extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean autostart = prefs.getBoolean(SenseSettings.PREF_AUTOSTART, false);
        
        if (true == autostart) {
            Intent startService = new Intent("nl.sense_os.service.ISenseService");
            context.startService(startService);
        }
    }
}