package com.b4g.smsgateway_app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SMSGatewayService extends Service {
    private static final String TAG = "SMSGatewayService";
    private static final String CHANNEL_ID = "SMSGatewayChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String API_URL = "https://byte4ge.com/admin/API/mobileSMSgateway/v1/get_sms.php";
    private static final long FETCH_INTERVAL = 5000; // 5 seconds

    private OkHttpClient client;
    private Handler handler;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        client = new OkHttpClient();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            startForeground(NOTIFICATION_ID, createNotification("SMS Gateway Service Running"));
            isRunning = true;
            startFetching();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SMS Gateway Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("SMS Gateway Service Channel");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String message) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SMS Gateway")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void startFetching() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    fetchAndProcessSMS();
                    handler.postDelayed(this, FETCH_INTERVAL);
                }
            }
        }, FETCH_INTERVAL);
    }

    private void fetchAndProcessSMS() {
        Log.d(TAG, "Fetching SMS data from API");
        updateNotification("Checking for pending SMS...");

        Request request = new Request.Builder()
                .url(API_URL)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API request failed: " + e.getMessage());
                updateNotification("Failed to connect to SMS gateway");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "API responded with error: " + response.code());
                    updateNotification("Server responded with error");
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    Log.d(TAG, "API Response: " + responseBody);

                    JSONObject smsData = new JSONObject(responseBody);
                    processSMSMessage(smsData);

                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error: " + e.getMessage());
                    updateNotification("Error parsing SMS data");
                }
            }
        });
    }

    private void processSMSMessage(JSONObject smsData) throws JSONException {
        String id = smsData.getString("id");
        String phoneNumber = smsData.getString("phone_number");
        String message = smsData.getString("message");
        String status = smsData.getString("status");

        Log.d(TAG, "Processing SMS ID: " + id + " | Status: " + status);

        if ("pending".equalsIgnoreCase(status)) {
            Log.d(TAG, "Sending SMS to: " + phoneNumber);
            sendSMS(phoneNumber, message, id);
        } else {
            Log.d(TAG, "SMS already processed, status: " + status);
            updateNotification("No pending SMS to send");
        }
    }

    private void sendSMS(String phoneNumber, String message, String smsId) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);

            Log.d(TAG, "SMS successfully sent to " + phoneNumber);
            updateNotification("SMS sent to " + phoneNumber);
            // Here you would typically call your API to update status to "sent"
            // updateSmsStatus(smsId, "sent");

        } catch (Exception e) {
            Log.e(TAG, "SMS sending failed: " + e.getMessage());
            updateNotification("Failed to send SMS");
            // Here you would typically call your API to update status to "failed"
            // updateSmsStatus(smsId, "failed");
        }
    }

    private void updateNotification(String message) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification(message));
    }
} 