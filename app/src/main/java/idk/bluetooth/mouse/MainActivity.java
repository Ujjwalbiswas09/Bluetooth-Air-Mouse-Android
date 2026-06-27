package idk.bluetooth.mouse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements HidManager.ConnectionListener, SensorEventListener {

    private static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 101;
    private HidManager hidManager;
    private ArrayAdapter<BluetoothDevice> deviceAdapter;
    private byte dx = 0;
    private byte dy = 0;
    private byte dz = 0;
    public static final long UPDATE_INTERVAL = 50;


    private ListView deviceListView;
    private View refreshButton;

    private boolean gyroMode = false;
    private float lastX = 0;
    private float lastY = 0;
    private float lastZ = 0;

    private long lastUpdate = 0;
    private long startOfTrack = 0;
    private boolean isOnWaiting = false;
    private boolean isConnected = false;
    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private GyroMouseConverter gyroConverter;
    private boolean hasAllPermissions = false;
    private final static int BLUETOOTH_REQUEST_CODE = 111;
    private boolean isPadView = false;
    private byte leftClick = 0;
    private byte rightClick = 0;
    private byte backClick = 0;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_connect);
        checkAndRequestBluetoothPermissions();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            gyroConverter = new GyroMouseConverter();
        }
        if (gyroSensor == null) {
            Toast.makeText(this, "Gyroscope sensor not available", Toast.LENGTH_SHORT).show();
        }
        if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Intent intent = new Intent(this, BridgeService.class);//Empty Service to Make It Running While Minimizing...
        bindService(intent, connection, BIND_AUTO_CREATE);
        startForegroundService(intent);
    }
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BridgeService.Bridge binder = (BridgeService.Bridge) service;
            bridgeService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bridgeService = null;
        }
    };
    private BridgeService bridgeService;

    @Override
    protected void onResume() {
        super.onResume();
        if (gyroSensor != null) {
            sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        }

        if(!hidManager.isBluetoothEnabled()){
            hidManager = new HidManager(this);
            if(hidManager.isBluetoothEnabled()) {
                hidManager.setConnectionListener(this);
                hidManager.init();
                createConnectionView();
            }
        }else {
            refreshDevices();
        }
        View decorView = getWindow().getDecorView();
        decorView.setBackgroundColor(R.color.bg_dark);
        decorView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Insets systemBarsInsets = insets.getInsets(WindowInsets.Type.navigationBars());
                    v.setPadding(0, 0, 0, systemBarsInsets.bottom);
                    return insets;
                }
                return null;
            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gyroSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void createConnectionView(){
        isPadView = false;
        setContentView(R.layout.fragment_connect);
        deviceListView = findViewById(R.id.lv_devices);
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1){

            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView textView = null;
                if(convertView == null) {
                    textView = new TextView(MainActivity.this);
                }else {
                    textView = (TextView) convertView;
                }
                BluetoothDevice device = (BluetoothDevice) getItem(position);
                if(device != null) {
                    textView.setText(device.getName());
                }
                textView.setPadding(16,16,16,16);
                textView.setTextSize(16);
                textView.setTextColor(getResources().getColor(R.color.white));
                return textView;
            }
        };
        deviceListView.setAdapter(deviceAdapter);
        refreshButton = findViewById(R.id.btn_refresh);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshDevices();
            }
        });
        refreshDevices();
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = (BluetoothDevice) deviceAdapter.getItem(position);
                tryToConnect(device);
            }
        });
        refreshDevices();
    }
    private void handleSeekBar(){
        SeekBar seekBar = findViewById(R.id.gyroSensitivity);
        if(gyroMode && gyroSensor != null) {
            seekBar.setVisibility(View.VISIBLE);
            seekBar.setProgress((int)(gyroConverter.SENSITIVITY / 5));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if(fromUser){
                        gyroConverter.SENSITIVITY = (progress + 1) * 5;
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }else{
            seekBar.setVisibility(View.GONE);
        }
    }

    private void createMousePad(BluetoothDevice device){
        isPadView = true;
        setContentView(R.layout.fragment_mouse_pad);
        TextView txtConnectedDevice = findViewById(R.id.txtConnectedDevice);
        TextView txtGyroStatus = findViewById(R.id.txtGyroStatus);
        Button btnLeftClick = findViewById(R.id.btnLeftClick);
        Button btnRightClick = findViewById(R.id.btnRightClick);
        txtConnectedDevice.setText("Trying Connected to: "+device.getName());
        txtGyroStatus.setText("Gyroscope Mouse: "+(gyroMode ? "ON" : "OFF"));
        View touchPadView = findViewById(R.id.touchpadView);
        View scrollBarView = findViewById(R.id.scrollBarView);
        LinearLayout gyroToggle = findViewById(R.id.btnGyroToggle);
        handleSeekBar();
        gyroToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gyroMode = !gyroMode;
                txtGyroStatus.setText("Gyroscope Mouse: "+(gyroMode ? "ON" : "OFF"));
                handleSeekBar();
            }
        });

        btnLeftClick.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction()==MotionEvent.ACTION_DOWN){
                    leftClick = 0x1;
                    updateMouseEvent(true);
                }else if(event.getAction()==MotionEvent.ACTION_UP) {
                    if(leftClick==0x1) {
                        leftClick = 0;
                        updateMouseEvent(true);
                    }
                }
                return true;
            }
        });
        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction()==MotionEvent.ACTION_DOWN){
                    backClick = 0x8;
                    updateMouseEvent(true);
                }else if(event.getAction()==MotionEvent.ACTION_UP) {
                    backClick = 0;
                    updateMouseEvent(true);
                }
                return true;
            }
        });

        btnRightClick.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction()==MotionEvent.ACTION_DOWN){
                    rightClick= 0x2;
                    updateMouseEvent(true);
                }else if(event.getAction()==MotionEvent.ACTION_UP) {
                    rightClick = 0;
                    updateMouseEvent(true);
                }
                return true;
            }
        });
        touchPadView.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    if(gyroMode){
                        leftClick = 0x1;
                        updateMouseEvent(true);
                        return true;
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                    startOfTrack = System.currentTimeMillis();
                    return true;
                }else if(event.getAction() == MotionEvent.ACTION_MOVE && !gyroMode){
                    float fdx = (event.getX() - lastX);
                    float fdy = (event.getY() - lastY);
                    lastX = event.getX();
                    lastY = event.getY();
                    int minMaxX = Math.min(Math.max((int) fdx, -126), 126);
                    int minMaxY = Math.min(Math.max((int) fdy, -126), 126);
                    dx = (byte) minMaxX;
                    dy = (byte) minMaxY;
                    updateMouseEvent(false);
                }else if(event.getAction() == MotionEvent.ACTION_UP){
                    if(gyroMode && (leftClick ==0x1)){
                        leftClick = 0;
                        updateMouseEvent(true);
                        return true;
                    }
                    long currentTime = System.currentTimeMillis();
                    if(currentTime - startOfTrack < 100){
                        leftClick = 0x1;
                        updateMouseEvent(true);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (leftClick == 0x1){
                                leftClick = 0;
                                updateMouseEvent(true);
                                }
                            }
                        },50);
                    }
                }
                return true;
            }
        });
        scrollBarView.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    lastZ = event.getY();
                }else {
                    float dz = (event.getY() - lastZ);
                    if(Math.abs(dz) > 50){
                        lastZ = event.getY();
                        if(dz > 0){
                            MainActivity.this.dz = (byte)1;
                        }else {
                            MainActivity.this.dz = (byte)-1;
                        }
                        updateMouseEvent(false);
                    }else {
                        dz = 0;
                    }


                }
                return true;
            }
        });

    }


    private void updateMouseEvent(boolean isUrgent){
        long currentTime = System.currentTimeMillis();
        long deltaStamp = currentTime - lastUpdate;
        long drift = Math.abs(UPDATE_INTERVAL-deltaStamp);
        if(deltaStamp > UPDATE_INTERVAL || drift > 20 || isUrgent) {
            lastUpdate = currentTime;
            hidManager.sentData(new byte[]{((byte)(leftClick|rightClick|backClick)), dx, dy, dz});
            dx = 0;
            dy = 0;
            dz = 0;
        }
    }
    private void refreshDevices(){
        if(hidManager != null && hidManager.isBluetoothEnabled()) {
            List<BluetoothDevice> pairedDevices = new ArrayList<>(hidManager.getPairedDevices());
            deviceAdapter.clear();
            deviceAdapter.addAll(pairedDevices);
            deviceAdapter.notifyDataSetChanged();
        }
    }

    private void checkAndRequestBluetoothPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            requestPermissions(permissionsNeeded.toArray(new String[0]), BLUETOOTH_PERMISSION_REQUEST_CODE);
        } else {
            hasAllPermissions = true;
            hidManager = new HidManager(this);
            if(hidManager.isBluetoothEnabled()) {
                hidManager.init();
                hidManager.setConnectionListener(this);
                createConnectionView();
            }else if(hidManager.isSupportBluetooth()){
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, BLUETOOTH_REQUEST_CODE);
            }else {
                Toast.makeText(this, "Bluetooth is not enabled. Enable Bluetooth", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onConnect(HidManager manager) {
        isOnWaiting = false;
        isConnected = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
             TextView txtConnectedDevice = findViewById(R.id.txtConnectedDevice);
             if(txtConnectedDevice == null){
                 createMousePad(manager.getConnectedHost());
                 txtConnectedDevice = findViewById(R.id.txtConnectedDevice);
             }
             txtConnectedDevice.setText("Connected to: "+manager.getConnectedHost().getName());

            }
        });
    }
    private void tryToConnect(BluetoothDevice device){
        createMousePad(device);
        hidManager.connectTo(device);
        isOnWaiting = true;
        isConnected = false;
    }

    @Override
    public void onDisconnect(HidManager manager) {
        if(isOnWaiting){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Failed To Connect", Toast.LENGTH_SHORT).show();
                }
            });
        }
        isOnWaiting = false;
        isConnected = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                createConnectionView();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if(isOnWaiting) {
            return;
        }else
        if(isConnected){
           createDisconnectDialog();
        }else {
            super.onBackPressed();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE && gyroMode){
            byte[] data = gyroConverter.convertGyroToMouse(event);
            dx = data[0];
            dy = data[1];
            updateMouseEvent(false);
        }
    }
    private void createDisconnectDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(R.layout.disconnect_dialog);
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnDisconnect = dialog.findViewById(R.id.btn_disconnect);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hidManager.disconnect();
                dialog.dismiss();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == BLUETOOTH_REQUEST_CODE) {
            if(resultCode == RESULT_OK) {
                hidManager = new HidManager(this);
                hidManager.setConnectionListener(this);
                hidManager.init();
                createConnectionView();
            }
        }
    }
}
