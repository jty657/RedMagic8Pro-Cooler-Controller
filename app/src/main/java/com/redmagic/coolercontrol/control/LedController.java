package com.redmagic.coolercontrol.control;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Looper;

import com.redmagic.coolercontrol.ble.BleManager;
import com.redmagic.coolercontrol.ble.WriteQueue;

public class LedController {
    
    private static final int LED_COUNT = 16;
    
    private final BleManager bleManager;
    private final WriteQueue writeQueue;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    public LedController(BleManager bleManager, WriteQueue writeQueue) {
        this.bleManager = bleManager;
        this.writeQueue = writeQueue;
    }
    
    public void sendNativeLight(int mode, int r, int g, int b) {
        BluetoothGattCharacteristic c1013 = bleManager.getC1013();
        if (c1013 == null) {
            return;
        }
        
        byte[] data = new byte[]{
            (byte) mode,
            (byte) (r & 0xFF),
            (byte) (g & 0xFF),
            (byte) (b & 0xFF)
        };
        
        writeQueue.enqueue(new WriteQueue.WriteOp(c1013, data, "原生灯光模式" + mode));
    }
    
    public void sendPerPixelColor(int ledIndex, int r, int g, int b) {
        BluetoothGattCharacteristic c1013 = bleManager.getC1013();
        if (c1013 == null || ledIndex < 0 || ledIndex >= LED_COUNT) {
            return;
        }
        
        byte[] stageData = new byte[]{
            (byte) 0xF0,
            (byte) ledIndex,
            (byte) (r & 0xFF),
            (byte) (g & 0xFF)
        };
        
        byte[] commitData = new byte[]{
            (byte) 0xF1,
            (byte) ledIndex,
            (byte) (b & 0xFF),
            (byte) 0x00
        };
        
        writeQueue.enqueue(new WriteQueue.WriteOp(c1013, stageData, "逐灯F0[" + ledIndex + "]"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1013, commitData, "逐灯F1[" + ledIndex + "]"));
    }
    
    public void turnOffAll() {
        for (int i = 0; i < LED_COUNT; i++) {
            sendPerPixelColor(i, 0, 0, 0);
        }
    }
    
    public void chaseAnimation() {
        for (int i = 0; i < LED_COUNT; i++) {
            final int ledIndex = i;
            mainHandler.postDelayed(() -> {
                // Turn off all first
                for (int j = 0; j < LED_COUNT; j++) {
                    if (j != ledIndex) {
                        sendPerPixelColor(j, 0, 0, 0);
                    }
                }
                // Turn on current LED
                sendPerPixelColor(ledIndex, 32, 0, 0);
            }, i * 300L);
        }
        
        // Turn off all at the end
        mainHandler.postDelayed(this::turnOffAll, LED_COUNT * 300L);
    }
}
