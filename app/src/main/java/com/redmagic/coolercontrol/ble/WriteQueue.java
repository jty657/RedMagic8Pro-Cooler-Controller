package com.redmagic.coolercontrol.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

public class WriteQueue {
    
    public static class WriteOp {
        public final BluetoothGattCharacteristic characteristic;
        public final byte[] data;
        public final String label;
        
        public WriteOp(BluetoothGattCharacteristic characteristic, byte[] data, String label) {
            this.characteristic = characteristic;
            this.data = data;
            this.label = label;
        }
    }
    
    public interface StatusListener {
        void onStatus(String message);
    }
    
    private final Queue<WriteOp> queue = new ArrayDeque<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private boolean writing = false;
    private WriteOp activeWrite;
    private StatusListener statusListener;
    
    public void setGatt(BluetoothGatt gatt) {
        this.gatt = gatt;
    }
    
    public void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
    }
    
    public void enqueue(WriteOp op) {
        synchronized (queue) {
            queue.offer(op);
        }
        pump();
    }
    
    public void clear() {
        synchronized (queue) {
            queue.clear();
        }
        writing = false;
        activeWrite = null;
    }
    
    public void pump() {
        if (writing || gatt == null) {
            return;
        }
        
        WriteOp next;
        synchronized (queue) {
            next = queue.poll();
        }
        
        if (next == null) {
            return;
        }
        
        BluetoothGattCharacteristic characteristic = next.characteristic;
        int props = characteristic.getProperties();
        int writeType;
        
        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        } else {
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        }
        
        writing = true;
        activeWrite = next;
        
        boolean started;
        if (Build.VERSION.SDK_INT >= 33) {
            int result = gatt.writeCharacteristic(characteristic, next.data, writeType);
            started = (result == BluetoothGatt.GATT_SUCCESS);
        } else {
            characteristic.setWriteType(writeType);
            characteristic.setValue(next.data);
            started = gatt.writeCharacteristic(characteristic);
        }
        
        if (!started) {
            writing = false;
            activeWrite = null;
            setStatus("写入启动失败 [" + next.label + "]: " + hex(next.data));
            mainHandler.postDelayed(this::pump, 80);
            return;
        }
        
        // For write-without-response, simulate completion
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            mainHandler.postDelayed(() -> {
                writing = false;
                activeWrite = null;
                pump();
            }, 45);
        }
    }
    
    public void onCharacteristicWriteComplete(BluetoothGattCharacteristic characteristic, int status) {
        if (activeWrite != null && characteristic.getUuid().equals(activeWrite.characteristic.getUuid())) {
            writing = false;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setStatus(activeWrite.label + " 写入失败: " + status);
            }
            activeWrite = null;
            mainHandler.post(this::pump);
        }
    }
    
    private void setStatus(String msg) {
        if (statusListener != null) {
            statusListener.onStatus(msg);
        }
    }
    
    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format(Locale.US, "%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }
}
