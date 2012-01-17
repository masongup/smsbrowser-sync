package com.mason.smssync.receiver;

import com.mason.smssync.SMSSyncAppConfigActivity;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SMSSyncService extends IntentService
{
	public SMSSyncService()
	{
		super("SMSSyncThread");
	}

	@Override
	protected void onHandleIntent(Intent incomingIntent)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String ipAddress = prefs.getString(SMSSyncAppConfigActivity.pSyncIPAddr, "0.0.0.0");
		String password = prefs.getString(SMSSyncAppConfigActivity.pSyncPassword, "");
	}

}