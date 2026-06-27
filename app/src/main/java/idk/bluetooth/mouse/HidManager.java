package idk.bluetooth.mouse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
@SuppressLint("MissingPermission")
public class HidManager extends BluetoothHidDevice.Callback implements BluetoothProfile.ServiceListener {
    private Context targetedActivity;
    private BluetoothAdapter bluetoothAdapter;
    private boolean isBluetoothEnabled;
    private BluetoothHidDevice virtualDevice;
    private boolean isReadyToConnect;
    private boolean isConnected;
    private BluetoothDevice connectedHost;
    private ConnectionListener connectionListener;
    private boolean supportBluetooth;
    public HidManager(Context activity) {
        targetedActivity = activity;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
          return;
        }
        supportBluetooth = true;
        isBluetoothEnabled = bluetoothAdapter.isEnabled();
        System.out.println("bluetooth: "+isBluetoothEnabled);
    }
    public boolean isSupportBluetooth() {
        return supportBluetooth;
    }
    @SuppressLint("MissingPermission")
    public void init(){
        bluetoothAdapter.getProfileProxy(targetedActivity,this, BluetoothProfile.HID_DEVICE);
    }
    public Set<BluetoothDevice> getPairedDevices(){
        return bluetoothAdapter.getBondedDevices();
    }
    public boolean isBluetoothEnabled(){
        return isBluetoothEnabled;
    }

    public void setConnectionListener(ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    public ConnectionListener getConnectionListener() {
        return connectionListener;
    }

    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        if (profile == BluetoothProfile.HID_DEVICE) {
            virtualDevice = (BluetoothHidDevice) proxy;
        }
        registerApp();
    }
    @SuppressLint("MissingPermission")
    private void registerApp(){
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "Air Mouse",
                "Android Air Mouse",
                "AirMouse Co",
                BluetoothHidDevice.SUBCLASS1_MOUSE,
                MOUSE_REPORT_DESCRIPTOR
        );

        virtualDevice.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(), this);
    }

    @Override
    public void onServiceDisconnected(int profile) {
        if (profile == BluetoothProfile.HID_DEVICE) {
            virtualDevice = null;
        }
    }

    private static final byte[] MOUSE_REPORT_DESCRIPTOR = new byte[]{
            (byte) 0x05, (byte) 0x01,
            (byte) 0x09, (byte) 0x02,
            (byte) 0xA1, (byte) 0x01,
            (byte) 0x09, (byte) 0x01,
            (byte) 0xA1, (byte) 0x00,
            // Buttons (3 buttons)
            // Buttons (5 buttons: Left, Right, Middle, Back, Forward)
            (byte) 0x05, (byte) 0x09, // Usage Page (Button)
            (byte) 0x19, (byte) 0x01, // Usage Minimum (1)
            (byte) 0x29, (byte) 0x05, // Usage Maximum (5) <-- Changed to 5 buttons
            (byte) 0x15, (byte) 0x00, // Logical Minimum (0)
            (byte) 0x25, (byte) 0x01, // Logical Maximum (1)
            (byte) 0x95, (byte) 0x05, // Report Count (5)  <-- Changed to 5
            (byte) 0x75, (byte) 0x01, // Report Size (1)
            (byte) 0x81, (byte) 0x02, // Input (Data, Var, Abs)
            // Padding (5 bits)
            (byte) 0x95, (byte) 0x01,
            (byte) 0x75, (byte) 0x05,
            (byte) 0x81, (byte) 0x03,
            // X, Y movement (-127 to
            (byte) 0x05, (byte) 0x01,
            (byte) 0x09, (byte) 0x30,
            (byte) 0x09, (byte) 0x31,
            (byte) 0x15, (byte) 0x81,
            (byte) 0x25, (byte) 0x7F,
            (byte) 0x75, (byte) 0x08,
            (byte) 0x95, (byte) 0x02,
            (byte) 0x81, (byte) 0x06,
            // Scroll wheel (-127 to 1
            (byte) 0x09, (byte) 0x38,
            (byte) 0x15, (byte) 0x81,
            (byte) 0x25, (byte) 0x7F,
            (byte) 0x75, (byte) 0x08,
            (byte) 0x95, (byte) 0x01,
            (byte) 0x81, (byte) 0x06,
            (byte) 0xC0,
            (byte) 0xC0
    };

    @Override
    public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
        isReadyToConnect = registered;
    }



    @Override
    public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
        if(virtualDevice != null) {
            virtualDevice.replyReport(device, type, id, new byte[]{0,0,0,0});
        }
    }

    @Override
    public void onConnectionStateChanged(BluetoothDevice device, int state) {

        if(state == BluetoothProfile.STATE_CONNECTED) {
            connectedHost = device;
            isConnected = true;
            if(connectionListener != null) {
                sentData(new byte[]{0,1,-1,0});
                connectionListener.onConnect(this);
            }
        }else if (state == BluetoothProfile.STATE_DISCONNECTED) {
           isConnected = false;
            if(connectionListener != null) {
              connectionListener.onDisconnect(this);
            }
        }

    }
    @Override public void onSetReport(BluetoothDevice d, byte t, byte id, byte[] data) {}
    @Override public void onInterruptData(BluetoothDevice d, byte id, byte[] data) {}

    public BluetoothDevice getConnectedHost() {
        return connectedHost;
    }

    public BluetoothHidDevice getHIDVirtualDevice() {
        return virtualDevice;
    }
    public void sentData(byte[] data){
        if(virtualDevice != null && isConnected) {
            virtualDevice.sendReport(connectedHost,0,data);
        }
    }
    public void connectTo(BluetoothDevice device){
        if(virtualDevice != null) {
            virtualDevice.connect(device);
        }
    }
    public void disconnect(){
        if(virtualDevice != null && isConnected && connectedHost != null) {
            virtualDevice.disconnect(connectedHost);
        }
    }

    public static interface ConnectionListener{
        void onConnect(HidManager manager);
        void onDisconnect(HidManager manager);
    }

    public boolean isReadyToConnect() {
        return isReadyToConnect;
    }

    public boolean isConnected() {
        return isConnected;
    }
}
