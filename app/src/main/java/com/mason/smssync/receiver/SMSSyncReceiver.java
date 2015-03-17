package com.mason.smssync.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SMSSyncReceiver extends BroadcastReceiver 
{
	public static final String ACTION_SYNCSMS = "com.mason.smssync.SYNCSMS";

	@Override
	public void onReceive(Context recContext, Intent incomingIntent) 
	{
		Intent outgoingIntent = new Intent(recContext, SMSSyncService.class);
		recContext.startService(outgoingIntent);
	}
	
}