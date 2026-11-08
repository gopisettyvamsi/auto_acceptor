package com.edvenswa.autoaccept;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Fires daily (scheduled in MainActivity.scheduleSummary) and posts the end-of-day summary. */
public class SummaryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        SharedPreferences sp = c.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean("summary", true)) return;
        String text = MainActivity.dailySummary(sp);
        if (text == null) return; // nothing happened today
        PendingIntent open = PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = MainActivity.noteBuilder(c).setSmallIcon(R.drawable.ic_trending_up)
            .setContentTitle(I18n.t(sp, "Today's summary")).setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(open).build();
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(2, n);
    }
}
