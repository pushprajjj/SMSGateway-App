package com.b4g.smsgateway_app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SMSGatewayService extends Service {
    private static final String TAG = "SMSGatewayService";
    private static final String CHANNEL_ID = "SMSGatewayChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String API_URL = "https://byte4ge.com/admin/API/mobileSMSgateway/v1/get_sms.php";
    private static final String UPDATE_API_URL = "https://byte4ge.com/admin/API/mobileSMSgateway/v1/update_sms_status.php";
    private static final long FETCH_INTERVAL = 5000; // 5 seconds

    private OkHttpClient client;
    private Handler handler;
    private boolean isRunning = false;
    private boolean isEmulator = false;
    private NotificationManager notificationManager;
    private PendingIntent pendingIntent;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate called");
        
        try {
            // Initialize OkHttpClient with longer timeouts
            client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
            
            // Create handler on main thread
            handler = new Handler(Looper.getMainLooper());
            
            // Check if running on emulator
            isEmulator = checkIsEmulator();
            Log.d(TAG, "Running on emulator: " + isEmulator);
            
            // Create notification channel
            createNotificationChannel();
            
            // Create pending intent for notification
            Intent notificationIntent = new Intent(this, MainActivity.class);
            pendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
            );
            
            // Get notification manager
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            showToast("Error initializing service: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand called");
        
        try {
            // Create and show the notification
            Notification notification = createNotification("SMS Gateway Service Starting...");
            startForeground(NOTIFICATION_ID, notification);
            
            // Start the periodic job if not already running
            if (!isRunning) {
                isRunning = true;
                Log.d(TAG, "Starting fetch timer");
                
                if (isEmulator) {
                    updateNotification("Running in emulator mode - SMS sending will be simulated");
                    showToast("Running in emulator mode - SMS will be simulated");
                }
                
                // Start the first fetch after a short delay
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startFetchingJob();
                    }
                }, 1000);
            }
            
            // Return sticky to restart if killed
            return START_STICKY;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand: " + e.getMessage(), e);
            showToast("Error starting service: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy called");
        isRunning = false;
        
        // Remove all pending callbacks
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        
        super.onDestroy();
    }

    private boolean checkIsEmulator() {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "SMS Gateway Service",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("SMS Gateway Service Channel");
                
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                    Log.d(TAG, "Notification channel created");
                } else {
                    Log.e(TAG, "NotificationManager is null");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating notification channel: " + e.getMessage(), e);
            }
        }
    }

    private Notification createNotification(String message) {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("SMS Gateway")
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true);
                    
            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent);
            }
            
            return builder.build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification: " + e.getMessage(), e);
            // Create a minimal notification in case of error
            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("SMS Gateway")
                    .setContentText("Service running")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
        }
    }

    private void updateNotification(String message) {
        try {
            if (notificationManager != null) {
                Notification notification = createNotification(message);
                notificationManager.notify(NOTIFICATION_ID, notification);
                Log.d(TAG, "Notification updated: " + message);
            } else {
                Log.e(TAG, "NotificationManager is null when updating notification");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating notification: " + e.getMessage(), e);
        }
    }

    private void startFetchingJob() {
        if (!isRunning) {
            Log.d(TAG, "Service not running, won't start fetch job");
            return;
        }
        
        Log.d(TAG, "Starting fetch job");
        updateNotification("Checking for pending SMS...");
        
        try {
            fetchAndProcessSMS();
        } catch (Exception e) {
            Log.e(TAG, "Error in fetch job: " + e.getMessage(), e);
            updateNotification("Error fetching SMS: " + e.getMessage());
        }
        
        // Schedule next execution
        if (isRunning && handler != null) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startFetchingJob();
                }
            }, FETCH_INTERVAL);
        }
    }

    private void fetchAndProcessSMS() {
        Log.d(TAG, "Fetching SMS data from API");
        
        if (client == null) {
            Log.e(TAG, "OkHttpClient is null");
            updateNotification("Error: Network client not initialized");
            return;
        }
        
        Request request = new Request.Builder()
                .url(API_URL)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API request failed: " + e.getMessage(), e);
                updateNotification("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "API responded with error: " + response.code());
                        updateNotification("Server error: " + response.code());
                        return;
                    }

                    String responseBody = "";
                    if (response.body() != null) {
                        responseBody = response.body().string();
                        Log.d(TAG, "API Response: " + responseBody);
                    } else {
                        Log.e(TAG, "Response body is null");
                        updateNotification("Error: Empty response from server");
                        return;
                    }

                    if (responseBody.isEmpty()) {
                        Log.e(TAG, "Response body is empty");
                        updateNotification("Error: Empty response from server");
                        return;
                    }

                    try {
                        // First check if it's a JSON array
                        if (responseBody.trim().startsWith("[")) {
                            // Handle array of messages
                            JSONArray messagesArray = new JSONArray(responseBody);
                            Log.d(TAG, "Processing " + messagesArray.length() + " SMS messages");
                            updateNotification("Processing " + messagesArray.length() + " SMS messages");
                            
                            int processedCount = 0;
                            for (int i = 0; i < messagesArray.length(); i++) {
                                JSONObject smsData = messagesArray.getJSONObject(i);
                                if (processSMSMessage(smsData)) {
                                    processedCount++;
                                }
                            }
                            
                            if (processedCount > 0) {
                                updateNotification("Processed " + processedCount + " of " + messagesArray.length() + " messages");
                            } else {
                                updateNotification("No pending SMS to send");
                            }
                        } else {
                            // Handle single message
                            JSONObject smsData = new JSONObject(responseBody);
                            processSMSMessage(smsData);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error: " + e.getMessage(), e);
                        updateNotification("Error parsing response: " + e.getMessage());
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error processing response: " + e.getMessage(), e);
                    updateNotification("Error processing response: " + e.getMessage());
                }
            }
        });
    }

    private boolean processSMSMessage(JSONObject smsData) {
        try {
            // Check if object contains required fields
            if (!smsData.has("id") || !smsData.has("phone_number") || 
                !smsData.has("message") || !smsData.has("status")) {
                Log.e(TAG, "Missing required fields in response: " + smsData.toString());
                updateNotification("Error: Invalid response format");
                return false;
            }
            
            String id = smsData.getString("id");
            String phoneNumber = smsData.getString("phone_number");
            String message = smsData.getString("message");
            String status = smsData.getString("status");

            Log.d(TAG, "Processing SMS ID: " + id + " | Status: " + status);

            if ("pending".equalsIgnoreCase(status)) {
                Log.d(TAG, "Found pending SMS to: " + phoneNumber);
                sendSMS(phoneNumber, message, id);
                return true;
            } else {
                Log.d(TAG, "SMS already processed, status: " + status);
                updateNotification("No pending SMS to send");
                return false;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error processing SMS data: " + e.getMessage(), e);
            updateNotification("Error processing SMS data");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Unknown error processing SMS: " + e.getMessage(), e);
            updateNotification("Unknown error processing SMS");
            return false;
        }
    }

    private void sendSMS(String phoneNumber, String message, String smsId) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Log.e(TAG, "Invalid phone number");
            updateNotification("Error: Invalid phone number");
            return;
        }
        
        if (message == null || message.isEmpty()) {
            Log.e(TAG, "Empty message");
            updateNotification("Error: Empty message");
            return;
        }
        
        try {
            if (isEmulator) {
                // Simulate sending SMS in emulator
                Log.d(TAG, "EMULATOR MODE: Simulated SMS to " + phoneNumber + ": " + message);
                updateNotification("SIMULATED: SMS sent to " + phoneNumber);
                // Update status on server
                updateSmsStatus(smsId, "success");
                return;
            }
            
            // Use the SMS manager to send the SMS
            SmsManager smsManager = SmsManager.getDefault();
            if (smsManager == null) {
                Log.e(TAG, "SmsManager is null");
                updateNotification("Error: SMS service not available");
                updateSmsStatus(smsId, "pending");
                return;
            }
            
            // Create sent and delivery intents with specific flags to prevent SMS from appearing in the SMS app
            PendingIntent sentIntent = null;
            PendingIntent deliveryIntent = null;
            
            if (message.length() > 160) {
                // For long messages, split them into parts
                ArrayList<String> messageParts = smsManager.divideMessage(message);
                ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                ArrayList<PendingIntent> deliveryIntents = new ArrayList<>();
                
                for (int i = 0; i < messageParts.size(); i++) {
                    sentIntents.add(sentIntent);
                    deliveryIntents.add(deliveryIntent);
                }
                
                // Send as multipart message with SKIP_STORAGE flag
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    smsManager.sendMultipartTextMessage(
                        phoneNumber, 
                        null, 
                        messageParts, 
                        sentIntents, 
                        deliveryIntents, 
                        0, 
                        true, // hideFromDefaultSmsApp
                        0x10 // Use the integer value for SKIP_STORAGE_FLAG (16 in decimal)
                    );
                } else {
                    // For older Android versions
                    smsManager.sendMultipartTextMessage(
                        phoneNumber, 
                        null, 
                        messageParts, 
                        sentIntents, 
                        deliveryIntents
                    );
                }
            } else {
                // For regular length messages
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Use the newer API with SKIP_STORAGE_FLAG on Android R+
                    smsManager.sendTextMessage(
                        phoneNumber, 
                        null, 
                        message, 
                        sentIntent, 
                        deliveryIntent, 
                        0, 
                        true, // hideFromDefaultSmsApp
                        0x10 // Use the integer value for SKIP_STORAGE_FLAG (16 in decimal)
                    );
                } else {
                    // Use the standard method for older Android versions
                    smsManager.sendTextMessage(
                        phoneNumber, 
                        null, 
                        message, 
                        sentIntent, 
                        deliveryIntent
                    );
                }
            }
            
            Log.d(TAG, "SMS successfully sent to " + phoneNumber + " (hidden from SMS app)");
            updateNotification("SMS sent to " + phoneNumber + " (hidden)");
            
            // Update status on server
            updateSmsStatus(smsId, "success");

        } catch (SecurityException se) {
            Log.e(TAG, "SMS permission denied: " + se.getMessage(), se);
            updateNotification("Error: SMS permission denied");
            updateSmsStatus(smsId, "pending");
        } catch (Exception e) {
            Log.e(TAG, "SMS sending failed: " + e.getMessage(), e);
            updateNotification("Failed to send SMS: " + e.getMessage());
            updateSmsStatus(smsId, "pending");
        }
    }
    
    private void updateSmsStatus(final String smsId, final String status) {
        if (smsId == null || smsId.isEmpty()) {
            Log.e(TAG, "Invalid SMS ID for status update");
            return;
        }
        
        if (client == null) {
            Log.e(TAG, "OkHttpClient is null for status update");
            return;
        }
        
        try {
            Log.d(TAG, "Updating SMS ID: " + smsId + " with status: " + status);
            
            // Create request body with form data
            RequestBody formBody = new FormBody.Builder()
                    .add("id", smsId)
                    .add("status", status)
                    .build();
            
            // Build the request
            Request request = new Request.Builder()
                    .url(UPDATE_API_URL)
                    .post(formBody)
                    .build();
            
            // Execute the request asynchronously
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to update SMS status: " + e.getMessage(), e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "Server error when updating SMS status: " + response.code());
                            return;
                        }
                        
                        String responseBody = "";
                        if (response.body() != null) {
                            responseBody = response.body().string();
                            Log.d(TAG, "Status update response: " + responseBody);
                        }
                        
                        Log.d(TAG, "Successfully updated SMS ID: " + smsId + " to status: " + status);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing status update response: " + e.getMessage(), e);
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating SMS status: " + e.getMessage(), e);
        }
    }
    
    private void showToast(final String message) {
        try {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error showing toast: " + e.getMessage(), e);
        }
    }
} 