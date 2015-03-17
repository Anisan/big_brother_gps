package org.gnarf.android;

import android.content.Context;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;

public class Helper
{
    static public void ok_dialog(Context ctx, String title, String text) {
	AlertDialog.Builder ad;
	ad = new AlertDialog.Builder(ctx);
	ad.setTitle(title);
	ad.setMessage(text);
	ad.setPositiveButton("Ok", null);
	ad.show();
    }
}
