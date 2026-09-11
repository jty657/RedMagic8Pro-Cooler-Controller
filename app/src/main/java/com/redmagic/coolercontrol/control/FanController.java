package com.redmagic.coolercontrol.control;

import android.bluetooth.BluetoothGattCharacteristic;
import android.widget.Toast;

import com.redmagic.coolercontrol.ble.BleManager;
import com.redmagic.coolercontrol.ble.WriteQueue;

public class FanController {
    
    private final BleManager bleManager;
    private final WriteQueue writeQueue;
    
    public FanController(BleManager bleManager, WriteQueue writeQueue) {
        this.bleManager = bleManager;
        this.writeQueue = writeQueue;
    }
    
    public void setManualFanLevel(int level) {
        if (level < 1 || level > 10) {
            return;
        }
        
        BluetoothGattCharacteristic c1011 = bleManager.getC1011();
        BluetoothGattCharacteristic c1012 = bleManager.getC1012();
        BluetoothGattCharacteristic c1017 = bleManager.getC1017();
        BluetoothGattCharacteristic c1018 = bleManager.getC1018();
        
        if (c1011 == null || c1012 == null || c1017 == null || c1018 == null) {
            return;
        }
        
        byte fanValue = (byte) (0x28 + 4 * (level - 1));
        
        writeQueue.enqueue(new WriteQueue.WriteOp(c1011, new byte[]{0x02}, "风扇:1011=02"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1018, new byte[]{0x00}, "风扇:1018=00"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1017, new byte[]{0x00}, "风扇:1017=00"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1012, new byte[]{fanValue}, "风扇:1012=" + String.format("%02X", fanValue & 0xFF)));
    }
    
    public void setSmartMode() {
        BluetoothGattCharacteristic c1011 = bleManager.getC1011();
        BluetoothGattCharacteristic c1017 = bleManager.getC1017();
        BluetoothGattCharacteristic c1018 = bleManager.getC1018();
        
        if (c1011 == null || c1017 == null || c1018 == null) {
            return;
        }
        
        writeQueue.enqueue(new WriteQueue.WriteOp(c1011, new byte[]{0x02}, "智能:1011=02"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1017, new byte[]{0x00}, "智能:1017=00"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1018, new byte[]{0x01}, "智能:1018=01"));
    }
    
    public void setBoostMode() {
        BluetoothGattCharacteristic c1011 = bleManager.getC1011();
        BluetoothGattCharacteristic c1012 = bleManager.getC1012();
        BluetoothGattCharacteristic c1017 = bleManager.getC1017();
        BluetoothGattCharacteristic c1018 = bleManager.getC1018();
        
        if (c1011 == null || c1012 == null || c1017 == null || c1018 == null) {
            return;
        }
        
        writeQueue.enqueue(new WriteQueue.WriteOp(c1011, new byte[]{0x02}, "破坏神:1011=02"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1018, new byte[]{0x00}, "破坏神:1018=00"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1012, new byte[]{0x50}, "破坏神:1012=50"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1017, new byte[]{0x01}, "破坏神:1017=01"));
    }
    
    public void powerOff() {
        BluetoothGattCharacteristic c1011 = bleManager.getC1011();
        BluetoothGattCharacteristic c1012 = bleManager.getC1012();
        BluetoothGattCharacteristic c1017 = bleManager.getC1017();
        BluetoothGattCharacteristic c1018 = bleManager.getC1018();
        
        if (c1011 == null || c1012 == null || c1017 == null || c1018 == null) {
            return;
        }
        
        writeQueue.enqueue(new WriteQueue.WriteOp(c1011, new byte[]{0x02}, "关闭:1011=02"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1018, new byte[]{0x00}, "关闭:1018=00"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1012, new byte[]{0x00}, "关闭:1012=00"));
        writeQueue.enqueue(new WriteQueue.WriteOp(c1017, new byte[]{0x00}, "关闭:1017=00"));
    }
}
