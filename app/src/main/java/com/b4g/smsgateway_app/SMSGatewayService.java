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
    // Map to track SMS broadcasts by ID
    private java.util.Map<String, android.content.BroadcastReceiver> sentReceivers = new java.util.HashMap<>();
    // Add a map to track delivery receivers
    private java.util.Map<String, android.content.BroadcastReceiver> deliveryReceivers = new java.util.HashMap<>();

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
            updateSmsStatus(smsId, "failed");
            return;
        }
        
        if (message == null || message.isEmpty()) {
            Log.e(TAG, "Empty message");
            updateNotification("Error: Empty message");
            updateSmsStatus(smsId, "failed");
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
                updateSmsStatus(smsId, "failed");
                return;
            }
            
            // Update status to sending immediately
            updateSmsStatus(smsId, "sending");
            
            // Create unique actions for this SMS
            final String SENT_ACTION = "SMS_SENT_" + smsId;
            final String DELIVERED_ACTION = "SMS_DELIVERED_" + smsId;
            
            Intent sentIntent = new Intent(SENT_ACTION);
            sentIntent.putExtra("sms_id", smsId);
            
            Intent deliveredIntent = new Intent(DELIVERED_ACTION);
            deliveredIntent.putExtra("sms_id", smsId);
            
            // Create the pending intents with proper flags
            int flags = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            
            PendingIntent sentPI = PendingIntent.getBroadcast(
                getApplicationContext(), 
                (int)(System.currentTimeMillis() % Integer.MAX_VALUE), // Unique request code
                sentIntent, 
                flags
            );
            
            PendingIntent deliveredPI = PendingIntent.getBroadcast(
                getApplicationContext(),
                (int)((System.currentTimeMillis() + 1) % Integer.MAX_VALUE), // Different unique request code
                deliveredIntent,
                flags
            );
            
            // Register BroadcastReceivers before sending SMS
            registerSentStatusReceiver(SENT_ACTION, smsId);
            registerDeliveryStatusReceiver(DELIVERED_ACTION, smsId);
            
            try {
                // Log all parameters before sending SMS
                Log.d(TAG, "Sending SMS - Phone: " + phoneNumber + ", ID: " + smsId + ", Message length: " + message.length());
                
                // For short messages, use the standard method
                if (message.length() <= 160) {
                    smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI);
                } else {
                    // For longer messages, divide into parts
                    ArrayList<String> parts = smsManager.divideMessage(message);
                    ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                    ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();
                    
                    for (int i = 0; i < parts.size(); i++) {
                        sentIntents.add(sentPI);
                        deliveredIntents.add(deliveredPI);
                    }
                    
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveredIntents);
                }
                
                Log.d(TAG, "SMS sending initiated to " + phoneNumber + " with ID: " + smsId);
                updateNotification("Sending SMS to " + phoneNumber);
                
            } catch (Exception e) {
                Log.e(TAG, "Error sending SMS: " + e.getMessage(), e);
                updateNotification("Failed to send SMS: " + e.getMessage());
                updateSmsStatus(smsId, "failed");
                
                // Cleanup in case of exception
                cleanup(smsId);
            }

        } catch (SecurityException se) {
            Log.e(TAG, "SMS permission denied: " + se.getMessage(), se);
            updateNotification("Error: SMS permission denied");
            updateSmsStatus(smsId, "failed");
        } catch (Exception e) {
            Log.e(TAG, "SMS sending failed: " + e.getMessage(), e);
            updateNotification("Failed to send SMS: " + e.getMessage());
            updateSmsStatus(smsId, "failed");
        }
    }
    
    private void cleanup(String smsId) {
        try {
            android.content.BroadcastReceiver sentReceiver = sentReceivers.get(smsId);
            if (sentReceiver != null) {
                try {
                    getApplicationContext().unregisterReceiver(sentReceiver);
                    sentReceivers.remove(smsId);
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering sent receiver: " + e.getMessage(), e);
                }
            }
            
            android.content.BroadcastReceiver deliveryReceiver = deliveryReceivers.get(smsId);
            if (deliveryReceiver != null) {
                try {
                    getApplicationContext().unregisterReceiver(deliveryReceiver);
                    deliveryReceivers.remove(smsId);
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering delivery receiver: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage(), e);
        }
    }
    
    private void registerDeliveryStatusReceiver(String action, final String smsId) {
        try {
            Log.d(TAG, "Registering delivery receiver for SMS " + smsId + " with action: " + action);
            
            // Create the receiver
            android.content.BroadcastReceiver deliveryReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        // Log receipt of broadcast
                        Log.d(TAG, "Received SMS delivery status for ID: " + smsId + ", result code: " + getResultCode());
                        
                        // Unregister the receiver
                        try {
                            context.unregisterReceiver(this);
                            deliveryReceivers.remove(smsId);
                            Log.d(TAG, "Unregistered delivery receiver for SMS " + smsId);
                        } catch (Exception e) {
                            Log.e(TAG, "Error unregistering delivery receiver: " + e.getMessage(), e);
                        }
                        
                        // Process the result
                        switch (getResultCode()) {
                            case android.app.Activity.RESULT_OK:
                                Log.d(TAG, "SMS successfully delivered to recipient, ID: " + smsId);
                                updateNotification("SMS delivered successfully");
                                updateSmsStatus(smsId, "delivered");
                                break;
                                
                            default:
                                Log.e(TAG, "SMS delivery failed for ID: " + smsId + ", Code: " + getResultCode());
                                updateNotification("SMS failed to deliver to recipient");
                                updateSmsStatus(smsId, "delivery_failed");
                                break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in SMS delivery receiver: " + e.getMessage(), e);
                    }
                }
            };
            
            // Store the receiver
            deliveryReceivers.put(smsId, deliveryReceiver);
            
            // Register the receiver
            getApplicationContext().registerReceiver(
                deliveryReceiver,
                new android.content.IntentFilter(action)
            );
            
        } catch (Exception e) {
            Log.e(TAG, "Error registering SMS delivery receiver: " + e.getMessage(), e);
        }
    }
    
    private void registerSentStatusReceiver(String action, final String smsId) {
        try {
            Log.d(TAG, "Registering sent receiver for SMS " + smsId + " with action: " + action);
            
            // Create the receiver
            android.content.BroadcastReceiver sentReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        // Log receipt of broadcast
                        Log.d(TAG, "Received SMS sent status for ID: " + smsId + ", result code: " + getResultCode());
                        
                        // Unregister the receiver
                        try {
                            context.unregisterReceiver(this);
                            sentReceivers.remove(smsId);
                            Log.d(TAG, "Unregistered sent receiver for SMS " + smsId);
                        } catch (Exception e) {
                            Log.e(TAG, "Error unregistering sent receiver: " + e.getMessage(), e);
                        }
                        
                        // Process the result
                        switch (getResultCode()) {
                            case android.app.Activity.RESULT_OK:
                                Log.d(TAG, "SMS successfully sent to carrier, ID: " + smsId);
                                updateNotification("SMS sent to carrier");
                                updateSmsStatus(smsId, "sent_to_carrier");
                                break;
                                
                            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                                Log.e(TAG, "Generic failure in sending SMS, ID: " + smsId);
                                updateNotification("Failed to send SMS: Generic failure");
                                updateSmsStatus(smsId, "failed");
                                
                                // Also clean up delivery receiver since SMS won't be delivered
                                android.content.BroadcastReceiver deliveryReceiver = deliveryReceivers.get(smsId);
                                if (deliveryReceiver != null) {
                                    try {
                                        context.unregisterReceiver(deliveryReceiver);
                                        deliveryReceivers.remove(smsId);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error unregistering delivery receiver: " + e.getMessage(), e);
                                    }
                                }
                                break;
                                
                            case SmsManager.RESULT_ERROR_NO_SERVICE:
                                Log.e(TAG, "No service for sending SMS, ID: " + smsId);
                                updateNotification("Failed to send SMS: No service");
                                updateSmsStatus(smsId, "failed");
                                cleanup(smsId);
                                break;
                                
                            case SmsManager.RESULT_ERROR_NULL_PDU:
                                Log.e(TAG, "Null PDU in sending SMS, ID: " + smsId);
                                updateNotification("Failed to send SMS: Null PDU");
                                updateSmsStatus(smsId, "failed");
                                cleanup(smsId);
                                break;
                                
                            case SmsManager.RESULT_ERROR_RADIO_OFF:
                                Log.e(TAG, "Radio off error in sending SMS, ID: " + smsId);
                                updateNotification("Failed to send SMS: Radio off");
                                updateSmsStatus(smsId, "failed");
                                cleanup(smsId);
                                break;
                                
                            default:
                                Log.e(TAG, "Unknown error in sending SMS, ID: " + smsId + ", Code: " + getResultCode());
                                updateNotification("Failed to send SMS: Unknown error");
                                updateSmsStatus(smsId, "failed");
                                cleanup(smsId);
                                break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in SMS sent receiver: " + e.getMessage(), e);
                        updateSmsStatus(smsId, "failed");
                        cleanup(smsId);
                    }
                }
            };
            
            // Store the receiver
            sentReceivers.put(smsId, sentReceiver);
            
            // Register the receiver
            getApplicationContext().registerReceiver(
                sentReceiver,
                new android.content.IntentFilter(action)
            );
            
            // Set a timeout for receivers
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (sentReceivers.containsKey(smsId) || deliveryReceivers.containsKey(smsId)) {
                        Log.d(TAG, "Timeout for SMS " + smsId + ". Marking as unknown status.");
                        updateSmsStatus(smsId, "unknown");
                        cleanup(smsId);
                    }
                }
            }, 60000); // 60 seconds timeout
            
        } catch (Exception e) {
            Log.e(TAG, "Error registering SMS sent receiver: " + e.getMessage(), e);
            updateSmsStatus(smsId, "failed");
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