package org.gnarf.bigbrother.gps;
import android.location.*;

import android.os.Binder;

import org.gnarf.bigbrother.gps.*;

/* This class is a RPC binder for the GPS class. It is instantiated in
 * the GPS class and returned in onBind() */
public class LocBinder extends Binder
{
    private GPS gps;

    LocBinder(GPS gps)
    {
	this.gps = gps;
    }


    public void setCallback(LocIF the_if)
    {
	this.gps.rpc_if = the_if;
    }

    public Location getLocation()
    {
	return this.gps.location;
    }

    public int getBattery()
    {
	return this.gps.bat_level;
    }

    public boolean getCharger()
    {
	return this.gps.charger;
    }


    public void updatePrefs()
    {
	System.out.println("Doing updatePrefs");
	this.gps.reloadPrefs();
    }

    public void triggerUpdate()
    {
	this.gps.triggerUpdate();
    }
}
