package org.gnarf.bigbrother.gps;

import android.location.*;

public interface LocIF
{
    public void onStateChange(String prov, int state);
    public void onLocation(String prov, Location loc, String adress,
			   int bat_level, boolean charger);
    public void onError(String err);
    public void onHistory(int count);
}
