package com.redmagic.coolercontrol.ble;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class BleManager {
    
    private static final UUID UUID_1011 = uuid16(0x1011);
    private static final UUID UUID_1012 = uuid16(0x1012);
    private static final UUID UUID_1013 = uuid16(0x1013);
    private static final UUID UUID_1017 = uuid16(0x1017);
    private static final UUID UUID_1018 = uuid16(0x1018);
    
    public interface StatusListener {
        void onStatus(String message);
        void onCharacteristicsReady();
    }
    
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final WriteQueue writeQueue;
    
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private final Set<String> seenAddresses = new HashSet<>();
    private boolean scanning = false;
    
    private BluetoothGattCharacteristic c1011;
    private BluetoothGattCharacteristic c1012;
    private BluetoothGattCharacteristic c1013;
    private BluetoothGattCharacteristic c1017;
    private BluetoothGattCharacteristic c1018;
    
    private StatusListener statusListener;
    
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String address = device.getAddress();
            if (seenAddresses.add(address)) {
                devices.add(device);
                mainHandler.post(() -> 
                    setStatus("已发现 " + devices.size() + " 个 BLE 设备…")
                );
            }
        }
        
        @Override
        public void onScanFailed(int errorCode) {
            mainHandler.post(() -> setStatus("扫描失败，错误码 " + errorCode));
        }
    };
    
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED && statusCode == BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post(() -> setStatus("已连接，正在发现服务…"));
                try {
                    g.discoverServices();
                } catch (SecurityException e) {
                    mainHandler.post(() -> setStatus("发现服务权限不足"));
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                clearCharacteristics();
                writeQueue.clear();
                mainHandler.post(() -> setStatus("已断开"));
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt g, int statusCode) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post(() -> setStatus("服务发现失败: " + statusCode));
                return;
            }
            
            clearCharacteristics();
            
            for (BluetoothGattService service : g.getServices()) {
                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                    if (isWritable(c)) {
                        UUID uuid = c.getUuid();
                        if (matches16(uuid, 0x1011)) c1011 = c;
                        else if (matches16(uuid, 0x1012)) c1012 = c;
                        else if (matches16(uuid, 0x1013)) c1013 = c;
                        else if (matches16(uuid, 0x1017)) c1017 = c;
                        else if (matches16(uuid, 0x1018)) c1018 = c;
                    }
                }
            }
            
            int count = 0;
            if (c1011 != null) count++;
            if (c1012 != null) count++;
            if (c1013 != null) count++;
            if (c1017 != null) count++;
            if (c1018 != null) count++;
            
            final int foundCount = count;
            mainHandler.post(() -> {
                setStatus("就绪：已发现原生控制特征 " + foundCount + "/5\\n" +
                        "1011=" + yesNo(c1011) + " 1012=" + yesNo(c1012) + " 1013=" + yesNo(c1013) +
                        " 1017=" + yesNo(c1017) + " 1018=" + yesNo(c1018));
                if (statusListener != null) {
                    statusListener.onCharacteristicsReady();
                }
            });
        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            writeQueue.onCharacteristicWriteComplete(c, status);
        }
    };
    
    public BleManager(Context context, WriteQueue writeQueue) {
        this.context = context;
        this.writeQueue = writeQueue;
        
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.adapter = manager != null ? manager.getAdapter() : null;
        this.scanner = adapter != null ? adapter.getBluetoothLeScanner() : null;
    }
    
    public void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
    }
    
    public BluetoothGattCharacteristic getC1011() { return c1011; }
    public BluetoothGattCharacteristic getC1012() { return c1012; }
    public BluetoothGattCharacteristic getC1013() { return c1013; }
    public BluetoothGattCharacteristic getC1017() { return c1017; }
    public BluetoothGattCharacteristic getC1018() { return c1018; }
    
    public void startScan() {
        if (adapter == null || !adapter.isEnabled()) {
            setStatus("请先打开蓝牙");
            return;
        }
        
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            setStatus("BLE 扫描器不可用");
            return;
        }
        
        devices.clear();
        seenAddresses.clear();
        scanning = true;
        setStatus("正在扫描附近 BLE 设备…");
        
        try {
            scanner.startScan(scanCallback);
        } catch (SecurityException e) {
            setStatus("扫描权限不足");
            return;
        }
        
        mainHandler.postDelayed(() -> {
            stopScan();
            showDeviceSelectionDialog();
        }, 6000);
    }
    
    private void stopScan() {
        if (scanning && scanner != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException e) {
                // Ignore
            }
        }
        scanning = false;
    }
    
    private void showDeviceSelectionDialog() {
        if (devices.isEmpty()) {
            setStatus("没有扫到设备，请靠近散热器后重试");
            return;
        }
        
        String[] labels = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            BluetoothDevice d = devices.get(i);
            String name = getDeviceName(d);
            labels[i] = (name == null || name.isEmpty() ? "未命名 BLE" : name) + "\\n" + d.getAddress();
        }
        
        new AlertDialog.Builder(context)
                .setTitle("选择散热器")
                .setItems(labels, (dialog, which) -> connect(devices.get(which)))
                .setNegativeButton("取消", null)
                .show();
    }
    
    private String getDeviceName(BluetoothDevice device) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && 
                context.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != 0) {
                return null;
            }
            return device.getName();
        } catch (SecurityException e) {
            return null;
        }
    }
    
    private void connect(BluetoothDevice device) {
        disconnect();
        String name = getDeviceName(device);
        setStatus("正在连接 " + (name != null ? name : device.getAddress()) + "…");
        
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            writeQueue.setGatt(gatt);
        } catch (SecurityException e) {
            setStatus("连接权限不足");
        }
    }
    
    public void disconnect() {
        stopScan();
        BluetoothGatt oldGatt = gatt;
        gatt = null;
        clearCharacteristics();
        writeQueue.clear();
        
        if (oldGatt != null) {
            try {
                oldGatt.disconnect();
                oldGatt.close();
            } catch (SecurityException e) {
                // Ignore
            }
        }
        setStatus("未连接");
    }
    
    private void clearCharacteristics() {
        c1011 = null;
        c1012 = null;
        c1013 = null;
        c1017 = null;
        c1018 = null;
    }
    
    private void setStatus(String message) {
        if (statusListener != null) {
            statusListener.onStatus(message);
        }
    }
    
    private static UUID uuid16(int value) {
        return UUID.fromString(String.format(Locale.US, "0000%04x-0000-1000-8000-00805f9b34fb", value & 0xFFFF));
    }
    
    private static boolean isWritable(BluetoothGattCharacteristic c) {
        int props = c.getProperties();
        return (props & (BluetoothGattCharacteristic.PROPERTY_WRITE | 
                         BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }
    
    private static boolean matches16(UUID uuid, int shortUuid) {
        String s = uuid.toString().toLowerCase(Locale.US);
        String needle = String.format(Locale.US, "0000%04x-", shortUuid & 0xFFFF);
        return s.startsWith(needle);
    }
    
    private static String yesNo(Object obj) {
        return obj == null ? "×" : "✓";
    }
}
