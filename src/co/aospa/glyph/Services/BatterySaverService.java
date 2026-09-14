package co.aospa.glyph.Services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import co.aospa.glyph.Manager.StatusManager;
import co.aospa.glyph.Manager.SettingsManager;
import co.aospa.glyph.Utils.ServiceUtils;

public class BatterySaverService extends Service {

    PowerManager pm;

    boolean trackBatterySaver = false;
    boolean systemSaverOn = false;
    boolean restrictionActive = false;

    private final BroadcastReceiver powerSaveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction())) {
                updateStatus();
            }
        }
    };

    private void updateMainSwitch() {
        Intent intent = new Intent("co.aospa.glyph.UPDATE_MAIN_SWITCH");
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void updateStatus() {
        systemSaverOn = pm.isPowerSaveMode();
        restrictionActive = trackBatterySaver && systemSaverOn;
        Log.d("GlyphBatterySaver", "Battery saver: " + restrictionActive);
        StatusManager.setBatterySavingActive(restrictionActive);
        updateMainSwitch();
    }

    @Override
    public void onCreate() {
        pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        registerReceiver(powerSaveReceiver,
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        updateStatus();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() != null && "co.aospa.glyph.UPDATE_BATTERY_SAVER".equals(intent.getAction())) {
            trackBatterySaver = intent.getBooleanExtra("status", false);
        }
        updateStatus();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(powerSaveReceiver);
        StatusManager.setBatterySavingActive(false);
        Log.d("GlyphBatterySaver", "Battery saver stopping");
        updateMainSwitch();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
