package com.ece.uop.labqr;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class TimerExpireReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        String course = intent.getStringExtra("course");
        String semester = intent.getStringExtra("semester");
        String academicYear = intent.getStringExtra("academicYear");

        if (course == null || semester == null || academicYear == null) return;

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("Μαθήματα")
                .child(semester)
                .child(course)
                .child(academicYear)
                .child("Διακόπτης");

        ref.setValue("OFF");

        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("autoSwitchOffHappened", true).apply();
        prefs.edit().putString("autoSwitchOffCourse", course).apply();
        prefs.edit().putBoolean("appWasOpenWhenExpired", false).apply();
        showNotification(context, course);
    }

    private void showNotification(Context context, String course) {

        String channelId = "auto_off_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Αυτόματες Ειδοποιήσεις OFF",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Ο διακόπτης έκλεισε αυτόματα")
                .setContentText("Ο χρονοδιακόπτης για το μάθημα \"" + course + "\" έκλεισε αυτόματα επειδή έληξε ο χρόνος.")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        managerCompat.notify(course.hashCode(), builder.build());
    }

}
