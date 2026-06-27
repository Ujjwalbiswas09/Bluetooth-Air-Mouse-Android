package idk.bluetooth.mouse;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class BridgeService extends Service {

    private static final String CHANNEL_ID = "HidMouseServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private final IBinder binder = new Bridge();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    public class Bridge extends Binder {
        public BridgeService getService() {
            return BridgeService.this;
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Air Mouse Active")
                    .setContentText("Streaming gyroscope data via Bluetooth HID...")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .build();
        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }
    private void createNotificationChannel() {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Air Mouse Background Processing",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopForeground(true);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }
}
