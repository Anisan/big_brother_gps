package org.gnarf.bigbrother.gps;

import java.util.*;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;

import org.gnarf.bigbrother.gps.*;

public class GPSBooter extends BroadcastReceiver
{
    public static final String TAG = "GPSBooter";
    
    @Override public void onReceive(Context ctx, Intent i) 
    {
	Preferences prefs = new Preferences(ctx);
	prefs.load();
	
	if (prefs.start_on_boot) {
	    Intent svc_i = new Intent(ctx, GPS.class);
	    ComponentName svc = ctx.startService(svc_i);
	    if (svc == null)
		System.out.println("BigBrotherGPS: Failed starting service");
	    else
		System.out.println("BigBrotherGPS: Started service");

	} else
	    System.out.println("BigBrotherGPS: Not starting on boot");
    }

}
