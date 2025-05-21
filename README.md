# Byte4ge Mobile SMS Gateway

An Android application that serves as an SMS gateway, enabling automated sending of SMS messages from a remote API.

## Features

- Runs as a foreground service to ensure reliable operation
- Periodically fetches pending SMS messages from a server API
- Sends SMS messages using the device's cellular connection
- Updates the server with delivery status
- Works in emulator mode (simulates SMS sending for testing)
- User-friendly notifications showing service status

## Technical Details

This application connects to a remote API endpoint to fetch pending SMS messages that need to be sent. It runs as a foreground service to ensure Android doesn't kill the process during operation, providing reliability for SMS delivery.

### Architecture

- Foreground Android Service (SMSGatewayService)
- OkHttp for API communication
- Android SMS Manager for message delivery
- Notification system to show current status

### API Endpoints

The app connects to the following endpoints:
- `https://byte4ge.com/admin/API/mobileSMSgateway/v1/get_sms.php` - To fetch pending SMS
- `https://byte4ge.com/admin/API/mobileSMSgateway/v1/update_sms_status.php` - To update SMS status

### API Response and Request Formats

#### Incoming SMS Response Format

The server returns SMS data in one of the following formats:

1. JSON Array (multiple messages):
```json
[
  {
    "id": "123",
    "phone_number": "+1234567890",
    "message": "Hello, this is a test message",
    "status": "pending"
  },
  {
    "id": "124",
    "phone_number": "+0987654321",
    "message": "Another test message",
    "status": "pending"
  }
]
```

2. JSON Object (single message):
```json
{
  "id": "123",
  "phone_number": "+1234567890",
  "message": "Hello, this is a test message",
  "status": "pending"
}
```

#### Status Update Request Format

When updating SMS status, the app sends the following form data:
```
id: [SMS ID]
status: [success/pending]
```

## Requirements

- Android device with SMS capabilities
- Internet connection to fetch pending messages
- SMS Permission granted to the app

## Installation

1. Clone the repository
2. Open in Android Studio
3. Build and install the Byte4ge Mobile SMS Gateway on your Android device

## Usage

1. Launch the Byte4ge Mobile SMS Gateway app
2. The service will start automatically
3. The app will check for pending SMS messages every 5 seconds
4. Messages will be sent automatically and status updated to the server

## Permissions Required

- `SEND_SMS` - For sending SMS messages
- `INTERNET` - For API communication
- `FOREGROUND_SERVICE` - For running as a foreground service

## Development

### Emulator Mode

The application automatically detects when it's running in an emulator and switches to simulation mode, which logs SMS details without actually sending messages.

### Debugging

Check logcat with tag "SMSGatewayService" for detailed logs of operation. 