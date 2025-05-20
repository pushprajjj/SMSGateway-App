package com.b4g.smsgateway_app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "SMSGatewayApp";
    private static final String API_URL = "https://byte4ge.com/admin/API/mobileSMSgateway/v1/get_sms.php";

    private OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "Application started");

        // Check and request permissions
        if (!hasRequiredPermissions()) {
            requestSmsPermissions();
        } else {
            fetchAndProcessSMS();
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
                fetchAndProcessSMS();
            } else {
                showToast("SMS permissions are required for this app to function");
                Log.e(TAG, "Required permissions were denied");
            }
        }
    }

    private void fetchAndProcessSMS() {
        Log.d(TAG, "Fetching SMS data from API");
        showToast("Fetching pending SMS...");

        Request request = new Request.Builder()
                .url(API_URL)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API request failed: " + e.getMessage());
                runOnUiThread(() -> showToast("Failed to connect to SMS gateway"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "API responded with error: " + response.code());
                    runOnUiThread(() -> showToast("Server responded with error"));
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    Log.d(TAG, "API Response: " + responseBody);

                    JSONObject smsData = new JSONObject(responseBody);
                    processSMSMessage(smsData);

                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error: " + e.getMessage());
                    runOnUiThread(() -> showToast("Error parsing SMS data"));
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
            runOnUiThread(() -> showToast("No pending SMS to send"));
        }
    }

    private void sendSMS(String phoneNumber, String message, String smsId) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);

            Log.d(TAG, "SMS successfully sent to " + phoneNumber);
            runOnUiThread(() -> {
                showToast("SMS sent to " + phoneNumber);
                // Here you would typically call your API to update status to "sent"
                // updateSmsStatus(smsId, "sent");
            });

        } catch (Exception e) {
            Log.e(TAG, "SMS sending failed: " + e.getMessage());
            runOnUiThread(() -> {
                showToast("Failed to send SMS");
                // Here you would typically call your API to update status to "failed"
                // updateSmsStatus(smsId, "failed");
            });
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Uncomment and implement when you have the update status API endpoint
    /*
    private void updateSmsStatus(String smsId, String status) {
        // Implement API call to update SMS status
    }
    */
}