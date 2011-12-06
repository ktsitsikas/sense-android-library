/**************************************************************************************************
 * Copyright (C) 2010 Sense Observation Systems, Rotterdam, the Netherlands. All rights reserved. *
 *************************************************************************************************/
package nl.sense_os.service.deviceprox;

import android.content.Context;
import android.content.SharedPreferences;

import nl.sense_os.service.constants.SensePrefs;
import nl.sense_os.service.constants.SensePrefs.Main.DevProx;

public class DeviceProximity {

    private Context context;
    private boolean isScanEnabled = false;
    private BluetoothDeviceProximity bluetoothDP;
    private WIFIDeviceProximity wifiDP;
    private boolean isBtEnabled;
    private boolean isWifiEnabled;
    private int scanInterval = 0;

    public DeviceProximity(Context context) {
        this.context = context;
        bluetoothDP = new BluetoothDeviceProximity(context);
        wifiDP = new WIFIDeviceProximity(context);
    }

    public boolean getScanEnabled() {
        return isScanEnabled;
    }

    public int getScanInterval() {
        return scanInterval;
    }

    public void setScanInterval(int scanInterval) {
        this.scanInterval = scanInterval;
    }

    public void startEnvironmentScanning(int interval) {
        scanInterval = interval;
        isScanEnabled = true;

        final SharedPreferences mainPrefs = context.getSharedPreferences(SensePrefs.MAIN_PREFS,
                Context.MODE_PRIVATE);

        isBtEnabled = mainPrefs.getBoolean(DevProx.BLUETOOTH, true);
        if (isBtEnabled) {
            bluetoothDP.startEnvironmentScanning(interval);
        }

        isWifiEnabled = mainPrefs.getBoolean(DevProx.WIFI, true);
        if (isWifiEnabled) {
            wifiDP.startEnvironmentScanning(interval);
        }
    }

    public void stopEnvironmentScanning() {
        isScanEnabled = false;
        if (isBtEnabled) {
            bluetoothDP.stopEnvironmentScanning();
        }
        if (isWifiEnabled) {
            wifiDP.stopEnvironmentScanning();
        }
    }
}
