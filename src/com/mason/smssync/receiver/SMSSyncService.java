package com.mason.smssync.receiver;

//import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import android.support.v4.content.LocalBroadcastManager;
import android.text.format.Time;

import com.mason.smssync.SMSSyncAppConfigActivity;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import android.provider.ContactsContract.PhoneLookup;

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
		String baseIPAddress = prefs.getString(SMSSyncAppConfigActivity.pSyncIPAddr, "");
		String[] ipComponents = baseIPAddress.split(":");
		if (ipComponents.length != 2)
			return;
		byte[] password = prefs.getString(SMSSyncAppConfigActivity.pSyncPassword, "").getBytes();
		byte[] salt = { (byte)0x3b, (byte)0x58, (byte)0x3a, (byte)0x8c, (byte)0x49, (byte)0xd3, (byte)0x21, (byte)0x88 };
		byte[] finalPass = new byte[password.length + salt.length];
		System.arraycopy(password, 0, finalPass, 0, password.length);
		System.arraycopy(salt, 0, finalPass, password.length, salt.length);
		
		try
		{
			//generate the key for the encryption
			MessageDigest md5er = MessageDigest.getInstance("MD5");
			md5er.update(finalPass);
			byte[] keyBytes = md5er.digest();
			SecretKeySpec syncKey = new SecretKeySpec(keyBytes, "AES");
			
			//create the cipher objects and get the IV
			Cipher sendCipher = Cipher.getInstance("AES/CFB8/NoPadding");
			Cipher recCipher = Cipher.getInstance("AES/CFB8/NoPadding");
			sendCipher.init(Cipher.ENCRYPT_MODE, syncKey);
			IvParameterSpec decryptIv = new IvParameterSpec(sendCipher.getIV());
			recCipher.init(Cipher.DECRYPT_MODE, syncKey, decryptIv);
			byte[] sendiv = sendCipher.getIV();
			
			//open the comm socket and transmit the IV
			InetSocketAddress serverAddrPort = new InetSocketAddress(ipComponents[0], Integer.parseInt(ipComponents[1]));
			Socket syncSocket = new Socket();
			syncSocket.setSoTimeout(5000);
			syncSocket.connect(serverAddrPort, 500);
			OutputStream syncOut = syncSocket.getOutputStream();
			syncOut.write(sendiv);
			syncOut.flush();
			
			//create the output stream and transmit the timestamp
			//note that the remote computer will not acknowledge anything until a proper timestamp is
			//decoded and verified, which proves that it is talking to this app with the correct password
			Time currentTime = new Time();
			currentTime.setToNow();
			String timeString = currentTime.format3339(false);
			byte[] timeClearData = timeString.getBytes();
			byte[] timeCipherData = sendCipher.doFinal(timeClearData);
			OutputStream outStr = syncSocket.getOutputStream();
			outStr.write(timeCipherData);
			
			//set up the input stream, then receive and decode the last message timestamp and range-check it
			byte[] readData = new byte[23];
			int readLength = 0;
			InputStream inStr = syncSocket.getInputStream();
			readLength = inStr.read(readData, 0, 23);
			byte[] clearData = recCipher.doFinal(readData, 0, readLength);
			String receivedTimestamp = new String(clearData);
			Time rxTimestampTime = new Time();
			rxTimestampTime.parse3339(receivedTimestamp);
			
			//time to actually retrieve a list of the smses, then format, encrypt, and transmit.
			ContentResolver myResolver = getContentResolver();
			Cursor smsQueryResults = myResolver.query(
					Uri.parse( "content://sms/" ),
					new String[] { "date", "address", "type", "body" }, 
					"date > ?", 
					new String[] { Long.toString(rxTimestampTime.toMillis(false)) }, 
					"date");
			
			StringBuilder smsListString = new StringBuilder();
			smsQueryResults.moveToFirst();
			int totalMessagesWritten = 0;
			
			while(!smsQueryResults.isAfterLast())
			{
				smsListString.append(smsQueryResults.getString(0)).append('\t');
				
				Cursor contactResult = myResolver.query(
						Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(smsQueryResults.getString(1))), 
						new String[] {PhoneLookup.DISPLAY_NAME},
						null, null, null);
				if (contactResult.moveToFirst())
					smsListString.append(contactResult.getString(0)).append('\t');
				else
					smsListString.append(smsQueryResults.getString(1)).append('\t');
				
				smsListString.append(smsQueryResults.getString(1)).append('\t');
				smsListString.append(smsQueryResults.getString(2)).append('\t');
				smsListString.append(smsQueryResults.getString(3)).append('\r');
				smsQueryResults.moveToNext();
				totalMessagesWritten++;
				if (smsListString.length() > 4500)  //limit the size of a sync packet to 5000 bytes;
					break;							//if there's more than that, we'll sync again later
			}
			smsListString.append(totalMessagesWritten);
			
			String returnString = smsListString.toString();
			byte[] returnClearData = returnString.getBytes();
			byte[] returnCipherData = sendCipher.doFinal(returnClearData);
			outStr.write(returnCipherData);
			
			byte[] returnMessageNumber = new byte[2];
			readLength = inStr.read(returnMessageNumber, 0, 2);
			if (readLength == 2)
			{
				byte[] readNumberClear = recCipher.doFinal(returnMessageNumber);
				int readNumReturned = readNumberClear[0] & 0xFF + ((readNumberClear[1] & 0xFF) << 8);
				if (readNumReturned == totalMessagesWritten)
				{
					SharedPreferences.Editor prefEditor = prefs.edit();
					prefEditor.putString(SMSSyncAppConfigActivity.pLastSyncTime, timeString);
					prefEditor.commit();
					LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(SMSSyncAppConfigActivity.ACTION_UPDATETIME));
				}
			}
			
			syncSocket.close();
		}
		catch (Exception e)
		{
			Log.d("SyncService", e.getMessage());
		}
	}

}