/**************************************************************************************************
 * Copyright (C) 2010 Sense Observation Systems, Rotterdam, the Netherlands. All rights reserved. *
 *************************************************************************************************/
package nl.sense_os.service.phonestate;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;

import nl.sense_os.service.R;
import nl.sense_os.service.constants.SenseDataTypes;
import nl.sense_os.service.constants.SensePrefs;
import nl.sense_os.service.constants.SensePrefs.Main.PhoneState;
import nl.sense_os.service.constants.SensorData.DataPoint;
import nl.sense_os.service.constants.SensorData.SensorNames;
import nl.sense_os.service.provider.SNTP;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SensePhoneState extends PhoneStateListener {

	private static final String TAG = "Sense PhoneStateListener";

	private class OutgoingCallReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String outgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
			if (null == outgoingNumber) {
				Log.w(TAG, "Did not get outgoing number!");
			}

			try {
				JSONObject json = new JSONObject();
				json.put("state", "dialing");
				if (null != outgoingNumber) {
					json.put("outgoingNumber", outgoingNumber);
				}
				sendDataPoint(SensorNames.CALL_STATE, json.toString(), SenseDataTypes.JSON);
			} catch (JSONException e) {
				Log.w(TAG, "Failed to create data point for outgoing call. " + e);
			}
		}
	}

	private final Context context;
	private BroadcastReceiver outgoingCallReceiver;
	private final TelephonyManager telMgr;
	private Timer transmitTimer = new Timer();
	private boolean lastMsgIndicatorState;
	private boolean msgIndicatorUpdated = false;
	private String lastServiceState;
	private String lastSignalStrength;
	private String lastDataConnectionState;
	private String lastIp;
	private int previousConnectionType = -2; // used to detect changes in connection type

	public SensePhoneState(Context context) {
		super();
		this.context = context;
		telMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
	}

	/**
	 * Transmits the latest phone state data in a new Thread.
	 */
	private void transmitLatestState() {

		if (null != lastIp || null != lastDataConnectionState || true == msgIndicatorUpdated
				|| null != lastServiceState || null != lastSignalStrength) {
			// Log.v(TAG, "Transmit the latest phone state...");
		}

		// IP address
		if (null != lastIp) {
			// Log.d(TAG, "Transmit IP address...");
			sendDataPoint(SensorNames.IP_ADDRESS, lastIp, SenseDataTypes.STRING);
			lastIp = null;
		}

		// data connection state
		if (null != lastDataConnectionState) {
			// Log.d(TAG, "Transmit data connection state...");
			sendDataPoint(SensorNames.DATA_CONN, lastDataConnectionState, SenseDataTypes.STRING);
			lastDataConnectionState = null;
		}

		// message waiting indicator
		if (msgIndicatorUpdated) {
			// Log.d(TAG, "Transmit unread messages indicator...");
			sendDataPoint(SensorNames.UNREAD_MSG, lastMsgIndicatorState, SenseDataTypes.BOOL);
			msgIndicatorUpdated = false;
		}

		// service state
		if (null != lastServiceState) {
			// Log.d(TAG, "Transmit service state...");
			sendDataPoint(SensorNames.SERVICE_STATE, lastServiceState, SenseDataTypes.JSON);
			lastServiceState = null;
		}

		// signal strength
		if (null != lastSignalStrength) {
			// Log.d(TAG, "Transmit signal strength...");
			sendDataPoint(SensorNames.SIGNAL_STRENGTH, lastSignalStrength, SenseDataTypes.JSON);
			lastSignalStrength = null;
		}
	}

	private void sendDataPoint(String sensorName, Object value, String dataType) {
		Intent intent = new Intent(context.getString(R.string.action_sense_new_data));
		intent.putExtra(DataPoint.SENSOR_NAME, sensorName);
		intent.putExtra(DataPoint.DATA_TYPE, dataType);
		if (dataType.equals(SenseDataTypes.BOOL)) {
			intent.putExtra(DataPoint.VALUE, (Boolean) value);
		} else if (dataType.equals(SenseDataTypes.FLOAT)) {
			intent.putExtra(DataPoint.VALUE, (Float) value);
		} else if (dataType.equals(SenseDataTypes.INT)) {
			intent.putExtra(DataPoint.VALUE, (Integer) value);
		} else if (dataType.equals(SenseDataTypes.JSON)) {
			intent.putExtra(DataPoint.VALUE, (String) value);
		} else if (dataType.equals(SenseDataTypes.STRING)) {
			intent.putExtra(DataPoint.VALUE, (String) value);
		} else {
			Log.w(TAG, "Error sending data point: unexpected data type! '" + dataType + "'");
		}
		intent.putExtra(DataPoint.TIMESTAMP, SNTP.getInstance().getTime());
		context.startService(intent);
	}

	/**
	 * Starts sensing and schedules periodic transmission of the phone state.
	 */
	public void startSensing(final int interval) {

		SharedPreferences mainPrefs = context.getSharedPreferences(SensePrefs.MAIN_PREFS,
				Context.MODE_PRIVATE);

		// listen to events as defined in the preferences
		int events = 0;
		if (mainPrefs.getBoolean(PhoneState.CALL_STATE, true)) {
			events |= PhoneStateListener.LISTEN_CALL_STATE;

			// listen to outgoing calls
			outgoingCallReceiver = new OutgoingCallReceiver();
			context.registerReceiver(outgoingCallReceiver, new IntentFilter(
					Intent.ACTION_NEW_OUTGOING_CALL));
		}
		if (mainPrefs.getBoolean(PhoneState.DATA_CONNECTION, true)) {
			events |= PhoneStateListener.LISTEN_DATA_CONNECTION_STATE;
		}
		if (mainPrefs.getBoolean(PhoneState.UNREAD_MSG, true)) {
			events |= PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR;
		}
		if (mainPrefs.getBoolean(PhoneState.SERVICE_STATE, true)) {
			events |= PhoneStateListener.LISTEN_SERVICE_STATE;
		}
		if (mainPrefs.getBoolean(PhoneState.SIGNAL_STRENGTH, true)) {
			events |= PhoneStateListener.LISTEN_SIGNAL_STRENGTHS;
		}

		if (0 != events) {
			// start listening to the phone state
			telMgr.listen(this, events);

			if (null != transmitTimer) {
				transmitTimer.cancel();
			}
			transmitTimer = new Timer();

			transmitTimer.scheduleAtFixedRate(new TimerTask() {

				@Override
				public void run() {
					transmitLatestState();
				}
			}, interval, interval);
		} else {
			Log.w(TAG, "Phone state sensor is started but is not registered for any events");
		}
	}

	/**
	 * Stops listening and stops transmission of the phone state.
	 */
	public void stopSensing() {

		telMgr.listen(this, PhoneStateListener.LISTEN_NONE);

		if (null != transmitTimer) {
			transmitTimer.cancel();
		}

		// clean up outgoing call receiver
		if (null != outgoingCallReceiver) {
			try {
				context.unregisterReceiver(outgoingCallReceiver);
			} catch (IllegalArgumentException e) {
				// ignore
			}
		}
	}

	@Override
	public void onCallStateChanged(int state, String incomingNumber) {

		JSONObject json = new JSONObject();
		try {
			switch (state) {
			case TelephonyManager.CALL_STATE_IDLE:
				json.put("state", "idle");
				break;
			case TelephonyManager.CALL_STATE_OFFHOOK:
				json.put("state", "calling");
				break;
			case TelephonyManager.CALL_STATE_RINGING:
				json.put("state", "ringing");
				json.put("incomingNumber", incomingNumber);
				break;
			default:
				Log.e(TAG, "Unexpected call state: " + state);
				return;
			}
		} catch (JSONException e) {
			Log.e(TAG, "JSONException in onCallChanged", e);
			return;
		}

		// immediately send data point
		sendDataPoint(SensorNames.CALL_STATE, json.toString(), SenseDataTypes.JSON);
	}

	@Override
	public void onCellLocationChanged(CellLocation location) {
		// TODO: Catch listen cell location!
	}

	@Override
	public void onDataActivity(int direction) {
		// not used to prevent a loop
	}

	@Override
	public void onDataConnectionStateChanged(int state) {
		// Log.v(TAG, "Connection state changed...");

		String strState = "";
		switch (state) {
		case TelephonyManager.DATA_CONNECTED:
			// send the URL on which the phone can be reached
			String ip = "";
			try {
				Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
				while (nis.hasMoreElements()) {
					NetworkInterface ni = nis.nextElement();
					Enumeration<InetAddress> iis = ni.getInetAddresses();
					while (iis.hasMoreElements()) {
						InetAddress ia = iis.nextElement();
						if (ni.getDisplayName().equalsIgnoreCase("rmnet0")) {
							ip = ia.getHostAddress();
						}
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Error getting my own IP:", e);
			}
			if (ip.length() > 1) {
				lastIp = ip;
			}

			strState = "connected";

			break;
		case TelephonyManager.DATA_CONNECTING:
			strState = "connecting";
			break;
		case TelephonyManager.DATA_DISCONNECTED:
			strState = "disconnected";
			break;
		case TelephonyManager.DATA_SUSPENDED:
			strState = "suspended";
			break;
		default:
			Log.e(TAG, "Unexpected data connection state: " + state);
			return;
		}

		lastDataConnectionState = strState;

		// check network type
		ConnectivityManager connectivityManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo active = connectivityManager.getActiveNetworkInfo();
		String typeName;
		int type = -1;
		if (active == null)
			typeName = "none";
		else {
			typeName = active.getTypeName();
			type = active.getType();
		}

		// only send changes. Note that this method is also called when another part of the state
		// changed.
		if (previousConnectionType != type) {
			previousConnectionType = type;

			// send data point immediately
			sendDataPoint(SensorNames.CONN_TYPE, typeName, SenseDataTypes.STRING);
		}
	}

	@Override
	public void onMessageWaitingIndicatorChanged(boolean unreadMsgs) {
		// Log.v(TAG, "Message waiting changed...");
		lastMsgIndicatorState = unreadMsgs;
		msgIndicatorUpdated = true;
	}

	@Override
	public void onServiceStateChanged(ServiceState serviceState) {

		JSONObject json = new JSONObject();
		try {
			switch (serviceState.getState()) {
			case ServiceState.STATE_EMERGENCY_ONLY:
				json.put("state", "emergency calls only");
				break;
			case ServiceState.STATE_IN_SERVICE:
				json.put("state", "in service");
				String number = ((TelephonyManager) context
						.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
				json.put("phone number", number);
				break;
			case ServiceState.STATE_OUT_OF_SERVICE:
				json.put("state", "out of service");
				break;
			case ServiceState.STATE_POWER_OFF:
				json.put("state", "power off");
				break;
			}

			json.put("manualSet", serviceState.getIsManualSelection() ? true : false);

		} catch (JSONException e) {
			Log.e(TAG, "JSONException in onServiceStateChanged", e);
			return;
		}

		lastServiceState = json.toString();
	}

	@Override
	public void onSignalStrengthsChanged(SignalStrength signalStrength) {

		JSONObject json = new JSONObject();
		try {
			if (signalStrength.isGsm()) {
				json.put("GSM signal strength", signalStrength.getGsmSignalStrength());
				json.put("GSM bit error rate", signalStrength.getGsmBitErrorRate());
			} else {
				json.put("CDMA dBm", signalStrength.getCdmaDbm());
				json.put("EVDO dBm", signalStrength.getEvdoDbm());
			}
		} catch (JSONException e) {
			Log.e(TAG, "JSONException in onSignalStrengthsChanged", e);
			return;
		}

		lastSignalStrength = json.toString();
	}
}
