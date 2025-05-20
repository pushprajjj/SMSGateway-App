package com.b4g.smsgateway_app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "SMSGatewayApp";

    private TextView statusText;
    private TextView logText;
    private Button startStopButton;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        startStopButton = findViewById(R.id.fetchButton);
        startStopButton.setText("Start Service");

        // Set up button click listener
        startStopButton.setOnClickListener(v -> {
            if (hasRequiredPermissions()) {
                toggleService();
            } else {
                requestSmsPermissions();
            }
        });

        // Check permissions on startup
        if (!hasRequiredPermissions()) {
            requestSmsPermissions();
        }
    }

    private boolean hasRequiredPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_PHONE_STATE
                },
                PERMISSION_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateStatus("Permissions granted. Ready to start service.");
            } else {
                updateStatus("SMS permissions are required for this app to function");
            }
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
        Intent serviceIntent = new Intent(this, SMSGatewayService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        isServiceRunning = true;
        startStopButton.setText("Stop Service");
        updateStatus("Service started. Running in background.");
    }

    private void stopService() {
        Intent serviceIntent = new Intent(this, SMSGatewayService.class);
        stopService(serviceIntent);
        isServiceRunning = false;
        startStopButton.setText("Start Service");
        updateStatus("Service stopped.");
    }

    private void updateStatus(String message) {
        statusText.setText(message);
        appendLog(message);
    }

    private void appendLog(String message) {
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
}