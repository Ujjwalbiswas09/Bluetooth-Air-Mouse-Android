# Bluetooth Air Mouse (Android)

Turn your Android smartphone into a wireless physical air mouse. This application utilizes your device's built-in gyroscope to capture hand movements and transmits them as mouse movement reports to a host computer (PC, Mac, Smart TV, etc.) via Bluetooth using the Android **`BluetoothHidDevice` API**. 

Since it behaves as a standard Bluetooth HID (Human Interface Device), **no companion server or receiver software is required on the host device.**

---

## 🚀 Key Features

* **Zero-Server Setup**: Works plug-and-play with any host device that supports Bluetooth mice (Windows, macOS, Linux, Android, iOS, Smart TVs).
* **Dynamic Low-Pass Filtering (LPF)**: Uses an adaptive Exponential Moving Average (EMA) filter. It applies heavy smoothing during slow movements to eliminate hand tremors and jitter, while automatically scaling down smoothing (reducing latency to zero) during fast flicks.
* **Continuous Soft Deadzone**: Eliminates the sudden cursor jumps common in air mice by mapping movement continuously starting from zero when exiting the deadzone.
* **Sub-Pixel Resolution Accumulator**: Retains fractional movements across sensor frames, allowing for precise, pixel-by-pixel accuracy when moving slowly.
* **5-Button Mouse Support**: Map inputs to Left Click, Right Click, Middle Click (Scroll Click), Back, and Forward buttons.

---

## 🛠️ How It Works (Technical Overview)

### 1. Sensor Data Capture
The app listens to Android's gyroscope (`Sensor.TYPE_GYROSCOPE`):
* **Yaw (Z-axis)** is mapped to **Horizontal movement ($dX$)**.
* **Pitch (X-axis)** is mapped to **Vertical movement ($dY$)**.

### 2. Signal Processing Pipeline
```
 [ Raw Gyro values ]
         │
         ▼
 [ Dynamic LPF (EMA) ] ──► Dynamically adjusts smoothing alpha based on movement speed
         │
         ▼
 [ Soft Deadzone ] ────► Subtracts deadzone limit to ensure smooth start (no speed jumps)
         │
         ▼
 [ Sensitivity & Accel ] ► Scales inputs and applies non-linear acceleration curves
         │
         ▼
 [ Fractional Accumulator]► Preserves sub-pixel movements across frames
         │
         ▼
 [ HID Report Packager ] ─► Clamps values to standard signed bytes and sends via Bluetooth
```

---

## 📋 Bluetooth HID Report Descriptor

The application registers itself with the host using a custom 5-button mouse HID descriptor.

```java
private static final byte[] MOUSE_REPORT_DESCRIPTOR = new byte[]{
        (byte) 0x05, (byte) 0x01, // Usage Page (Generic Desktop)
        (byte) 0x09, (byte) 0x02, // Usage (Mouse)
        (byte) 0xA1, (byte) 0x01, // Collection (Application)
        (byte) 0x09, (byte) 0x01, // Usage (Pointer)
        (byte) 0xA1, (byte) 0x00, // Collection (Physical)
        
        // Buttons (5 buttons: Left, Right, Middle, Back, Forward)
        (byte) 0x05, (byte) 0x09, // Usage Page (Button)
        (byte) 0x19, (byte) 0x01, // Usage Minimum (1)
        (byte) 0x29, (byte) 0x05, // Usage Maximum (5)
        (byte) 0x15, (byte) 0x00, // Logical Minimum (0)
        (byte) 0x25, (byte) 0x01, // Logical Maximum (1)
        (byte) 0x95, (byte) 0x05, // Report Count (5)
        (byte) 0x75, (byte) 0x01, // Report Size (1)
        (byte) 0x81, (byte) 0x02, // Input (Data, Var, Abs)
        
        // Padding (3 bits to align to 1 byte)
        (byte) 0x95, (byte) 0x01, // Report Count (1)
        (byte) 0x75, (byte) 0x03, // Report Size (3)
        (byte) 0x81, (byte) 0x03, // Input (Cnst, Var, Abs)
        
        // X, Y movement (-127 to 127)
        (byte) 0x05, (byte) 0x01, // Usage Page (Generic Desktop)
        (byte) 0x09, (byte) 0x30, // Usage (X)
        (byte) 0x09, (byte) 0x31, // Usage (Y)
        (byte) 0x15, (byte) 0x81, // Logical Minimum (-127)
        (byte) 0x25, (byte) 0x7F, // Logical Maximum (127)
        (byte) 0x75, (byte) 0x08, // Report Size (8)
        (byte) 0x95, (byte) 0x02, // Report Count (2)
        (byte) 0x81, (byte) 0x06, // Input (Data, Var, Rel)
        
        // Scroll wheel (-127 to 127)
        (byte) 0x09, (byte) 0x38, // Usage (Wheel)
        (byte) 0x15, (byte) 0x81, // Logical Minimum (-127)
        (byte) 0x25, (byte) 0x7F, // Logical Maximum (127)
        (byte) 0x75, (byte) 0x08, // Report Size (8)
        (byte) 0x95, (byte) 0x01, // Report Count (1)
        (byte) 0x81, (byte) 0x06, // Input (Data, Var, Rel)
        
        (byte) 0xC0,              // End Collection
        (byte) 0xC0               // End Collection
};
```

### HID Report Format (4 Bytes)
| Byte | Bit Range | Description | Value Range |
| :--- | :--- | :--- | :--- |
| **0** | Bits 0-4 | Button Mask (Left=`0x01`, Right=`0x02`, Middle=`0x04`, Back=`0x08`, Forward=`0x10`) | Binary flags |
| **0** | Bits 5-7 | Padding | Always `0` |
| **1** | Full byte | X displacement ($dX$) | `-127` to `127` |
| **2** | Full byte | Y displacement ($dY$) | `-127` to `127` |
| **3** | Full byte | Scroll Wheel displacement | `-127` to `127` |

---

## ⚙️ Setup and Installation

### Requirements
* Android 9.0 (API Level 28) or higher (required for `BluetoothHidDevice`).
* A device equipped with a gyroscope sensor.

### Permissions
The application requires the following permissions declared in the `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<!-- Required for Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

### Steps to Use
1. **Enable Bluetooth**: Open the app and grant Bluetooth permissions.
2. **Pair with Host**: Go to your computer's Bluetooth settings and pair with your Android device (the phone will show up as a standard input mouse).
3. **Control**: Hold the phone in your hand and tilt/rotate it to move the cursor on your computer.

---

## 📄 License
This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
