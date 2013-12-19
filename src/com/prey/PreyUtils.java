/*******************************************************************************
 * Created by Carlos Yaconi
 * Copyright 2012 Fork Ltd. All rights reserved.
 * License: GPLv3
 * Full license at "/LICENSE"
 ******************************************************************************/
package com.prey;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;

public class PreyUtils {
	
	public static String getDeviceType(Activity act){
		return getDeviceType(act.getApplicationContext());
	}
	
	public static String getDeviceType(Context ctx){
		if (isTablet(ctx))
			return "Tablet";
		else
			return "Phone";
	}
	
	public static boolean isTablet(Context ctx) {
	    try {
	        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
	        float screenWidth  = dm.widthPixels / dm.xdpi;
	        float screenHeight = dm.heightPixels / dm.ydpi;
	        double size = Math.sqrt(Math.pow(screenWidth, 2)+Math.pow(screenHeight, 2));
	        return size >= 6;
	    } catch(Throwable t) {
	        return false;
	    }
	}
	
	public static boolean supportSMS(Context ctx){
		//Froyo or above!!
		TelephonyManager telephonyManager1 = (TelephonyManager)ctx.getSystemService(Context.TELEPHONY_SERVICE);
        boolean isPhone = !(telephonyManager1.getPhoneType()==TelephonyManager.PHONE_TYPE_NONE);
        boolean featureTelephony = ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
		return isPhone && featureTelephony;
	}

	public static String randomAlphaNumeric(int length){
		StringBuffer buffer = new StringBuffer();
		String characters = "abcdefghijklmnopqrstuvwxyz0123456789";
		int charactersLength = characters.length();

		for (int i = 0; i < length; i++) {
			double index = Math.random() * charactersLength;
			buffer.append(characters.charAt((int) index));
		}
	    return buffer.toString();
	}
}
