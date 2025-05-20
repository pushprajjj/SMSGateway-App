package com.b4g.smsgateway_app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private static final String TAG = "SMSGatewayApp";

    private TextView statusText;
    private TextView logText;
    private Button startStopButton;
    private boolean isServiceRunning = false;
    private boolean isEmulator = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            // Initialize UI components
            statusText = findViewById(R.id.statusText);
            logText = findViewById(R.id.logText);
            startStopButton = findViewById(R.id.fetchButton);
            startStopButton.setText("Start Service");

            // Check if running on emulator
            isEmulator = isEmulator();
            Log.d(TAG, "Running on emulator: " + isEmulator);
            
            if (isEmulator) {
                appendLog("Detected emulator environment - SMS sending will be simulated");
            }

            // Set up button click listener
            startStopButton.setOnClickListener(v -> {
                if (hasRequiredPermissions()) {
                    toggleService();
                } else {
                    requestPermissions();
                }
            });

            // Check permissions on startup
            if (!hasRequiredPermissions()) {
                requestPermissions();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing activity: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasRequiredPermissions() {
        boolean hasBasicPermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
                
        boolean hasNotificationPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        
        return hasBasicPermissions && hasNotificationPermission;
    }

    private void requestPermissions() {
        try {
            List<String> permissionsToRequest = new ArrayList<>();
            
            // Add basic permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.SEND_SMS);
            }
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE);
            }
            
            // Request basic permissions if needed
            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(
                    this, 
                    permissionsToRequest.toArray(new String[0]), 
                    PERMISSION_REQUEST_CODE
                );
                return;
            }
            
            // Request notification permission separately for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    );
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting permissions: " + e.getMessage(), e);
            updateStatus("Error requesting permissions: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        try {
            if (requestCode == PERMISSION_REQUEST_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    updateStatus("SMS permissions granted");
                    
                    // Check notification permission for Android 13+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            requestNotificationPermission();
                        } else {
                            updateStatus("All permissions granted. Ready to start service.");
                        }
                    } else {
                        updateStatus("All permissions granted. Ready to start service.");
                    }
                } else {
                    updateStatus("SMS permissions are required for this app to function");
                }
            } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    updateStatus("All permissions granted. Ready to start service.");
                } else {
                    updateStatus("Notification permission denied. Service may not work properly.");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in permission result: " + e.getMessage(), e);
        }
    }
    
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                NOTIFICATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    private void toggleService() {
        if (!isServiceRunning) {
            startService();
        } else {
            stopService();
        }
    }

    private void startService() {
        try {
            Intent serviceIntent = new Intent(this, SMSGatewayService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            isServiceRunning = true;
            startStopButton.setText("Stop Service");
            
            if (isEmulator) {
                updateStatus("Service started in simulation mode (emulator detected)");
            } else {
                updateStatus("Service started. Running in background.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting service: " + e.getMessage(), e);
            updateStatus("Error starting service: " + e.getMessage());
        }
    }

    private void stopService() {
        try {
            Intent serviceIntent = new Intent(this, SMSGatewayService.class);
            stopService(serviceIntent);
            isServiceRunning = false;
            startStopButton.setText("Start Service");
            updateStatus("Service stopped.");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping service: " + e.getMessage(), e);
            updateStatus("Error stopping service: " + e.getMessage());
        }
    }

    private void updateStatus(String message) {
        try {
            if (statusText != null) {
                statusText.setText(message);
                appendLog(message);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating status: " + e.getMessage(), e);
        }
    }

    private void appendLog(String message) {
        try {
            if (logText != null && logText.getLayout() != null) {
                String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date());
                String logMessage = timestamp + " - " + message + "\n";
                logText.append(logMessage);
                
                // Scroll to bottom
                int scrollAmount = logText.getLayout().getLineTop(logText.getLineCount()) - logText.getHeight();
                if (scrollAmount > 0) {
                    logText.scrollTo(0, scrollAmount);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error appending to log: " + e.getMessage(), e);
        }
    }
    
    /**
     * Detects if the app is running in an emulator.
     */
    private boolean isEmulator() {
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
}