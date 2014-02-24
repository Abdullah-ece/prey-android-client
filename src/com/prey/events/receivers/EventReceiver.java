package com.prey.events.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.prey.PreyConfig;
import com.prey.events.Event;
import com.prey.events.factories.EventFactory;
import com.prey.events.manager.EventManagerRunner;
import com.prey.events.manager.SignalFlareRunner;
 

public class EventReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		PreyConfig config=PreyConfig.getPreyConfig(context);
		if (config.isThisDeviceAlreadyRegisteredWithPrey(true)){
			if( PreyConfig.VERSION_V2.equals(config.getVersion())){
				Event event=EventFactory.getEvent(context, intent);
				new Thread(new EventManagerRunner(context,event)).start();
				new Thread(new SignalFlareRunner(context,event)).start();
			}
		}	
	}
	
}
