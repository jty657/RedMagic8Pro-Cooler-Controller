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
    
    /**
     * 彩虹跑马灯效果
     * 16个LED显示完整的彩虹色谱，并循环滚动
     */
    public void rainbowMarquee() {
        final int cycles = 3; // 循环3圈
        final int delayPerStep = 100; // 每步100ms
        
        for (int cycle = 0; cycle < cycles * LED_COUNT; cycle++) {
            final int offset = cycle;
            mainHandler.postDelayed(() -> {
                for (int i = 0; i < LED_COUNT; i++) {
                    // 计算当前LED的彩虹色相位
                    float hue = ((i + offset) % LED_COUNT) * 360f / LED_COUNT;
                    int[] rgb = hsvToRgb(hue, 1.0f, 0.3f); // 饱和度1.0，亮度0.3
                    sendPerPixelColor(i, rgb[0], rgb[1], rgb[2]);
                }
            }, cycle * delayPerStep);
        }
        
        // 动画结束后保持最后一帧
    }
    
    /**
     * 呼吸灯效果 - 所有LED同时呼吸
     * @param r 红色分量
     * @param g 绿色分量
     * @param b 蓝色分量
     */
    public void breathingEffect(int r, int g, int b) {
        final int steps = 20; // 呼吸周期分20步
        final int delayPerStep = 50;
        final int cycles = 3; // 呼吸3次
        
        for (int cycle = 0; cycle < cycles; cycle++) {
            // 渐亮
            for (int step = 0; step <= steps; step++) {
                final float brightness = step / (float) steps;
                final int totalDelay = (cycle * steps * 2 + step) * delayPerStep;
                
                mainHandler.postDelayed(() -> {
                    int br = (int) (r * brightness);
                    int bg = (int) (g * brightness);
                    int bb = (int) (b * brightness);
                    for (int i = 0; i < LED_COUNT; i++) {
                        sendPerPixelColor(i, br, bg, bb);
                    }
                }, totalDelay);
            }
            
            // 渐暗
            for (int step = steps; step >= 0; step--) {
                final float brightness = step / (float) steps;
                final int totalDelay = (cycle * steps * 2 + (steps * 2 - step)) * delayPerStep;
                
                mainHandler.postDelayed(() -> {
                    int br = (int) (r * brightness);
                    int bg = (int) (g * brightness);
                    int bb = (int) (b * brightness);
                    for (int i = 0; i < LED_COUNT; i++) {
                        sendPerPixelColor(i, br, bg, bb);
                    }
                }, totalDelay);
            }
        }
    }
    
    /**
     * 流星效果 - 一个亮点拖着渐暗的尾巴移动
     */
    public void meteorEffect() {
        final int tailLength = 6; // 尾巴长度
        final int cycles = 2; // 循环2圈
        final int delayPerStep = 80;
        
        for (int cycle = 0; cycle < cycles * LED_COUNT; cycle++) {
            final int headPos = cycle;
            
            mainHandler.postDelayed(() -> {
                for (int i = 0; i < LED_COUNT; i++) {
                    int distance = (headPos - i + LED_COUNT) % LED_COUNT;
                    
                    if (distance == 0) {
                        // 头部：亮白色
                        sendPerPixelColor(i, 64, 64, 64);
                    } else if (distance < tailLength) {
                        // 尾巴：渐暗的蓝色
                        int brightness = (tailLength - distance) * 10;
                        sendPerPixelColor(i, 0, brightness / 2, brightness);
                    } else {
                        // 其他：熄灭
                        sendPerPixelColor(i, 0, 0, 0);
                    }
                }
            }, cycle * delayPerStep);
        }
        
        // 动画结束后熄灭所有
        mainHandler.postDelayed(this::turnOffAll, cycles * LED_COUNT * delayPerStep);
    }
    
    /**
     * 波浪效果 - 彩虹色波浪扩散
     */
    public void waveEffect() {
        final int cycles = 30; // 30帧动画
        final int delayPerStep = 80;
        
        for (int frame = 0; frame < cycles; frame++) {
            final float phase = frame * 0.3f;
            
            mainHandler.postDelayed(() -> {
                for (int i = 0; i < LED_COUNT; i++) {
                    // 正弦波计算亮度和色相
                    float angle = (i * 22.5f + phase * 30f) % 360f; // 每个LED间隔22.5度
                    float brightness = (float) ((Math.sin(Math.toRadians(angle)) + 1) / 2); // 0-1
                    float hue = (phase * 20f) % 360f; // 色相随时间变化
                    
                    int[] rgb = hsvToRgb(hue, 1.0f, brightness * 0.4f);
                    sendPerPixelColor(i, rgb[0], rgb[1], rgb[2]);
                }
            }, frame * delayPerStep);
        }
    }
    
    /**
     * 闪烁效果 - 随机LED闪烁
     */
    public void sparkleEffect() {
        final int duration = 5000; // 持续5秒
        final int sparkleInterval = 150; // 每150ms闪烁一次
        final int sparkleCount = duration / sparkleInterval;
        
        for (int i = 0; i < sparkleCount; i++) {
            mainHandler.postDelayed(() -> {
                // 熄灭所有
                for (int j = 0; j < LED_COUNT; j++) {
                    sendPerPixelColor(j, 0, 0, 0);
                }
                
                // 随机点亮3-5个LED
                int count = 3 + (int) (Math.random() * 3);
                for (int k = 0; k < count; k++) {
                    int ledIndex = (int) (Math.random() * LED_COUNT);
                    int[] rgb = hsvToRgb((float) (Math.random() * 360), 1.0f, 0.5f);
                    sendPerPixelColor(ledIndex, rgb[0], rgb[1], rgb[2]);
                }
            }, i * sparkleInterval);
        }
        
        // 结束后熄灭
        mainHandler.postDelayed(this::turnOffAll, duration);
    }
    
    /**
     * 对向奔跑效果 - 两个光点从两端向中间奔跑并交错
     */
    public void dualChaseEffect() {
        final int cycles = 2;
        final int delayPerStep = 100;
        final int halfCount = LED_COUNT / 2;
        
        for (int cycle = 0; cycle < cycles; cycle++) {
            for (int step = 0; step <= halfCount; step++) {
                final int pos1 = step;
                final int pos2 = LED_COUNT - 1 - step;
                final int totalDelay = (cycle * halfCount + step) * delayPerStep;
                
                mainHandler.postDelayed(() -> {
                    // 清空所有
                    for (int i = 0; i < LED_COUNT; i++) {
                        sendPerPixelColor(i, 0, 0, 0);
                    }
                    // 点亮两个光点
                    sendPerPixelColor(pos1, 64, 0, 0); // 红色
                    if (pos1 != pos2) {
                        sendPerPixelColor(pos2, 0, 0, 64); // 蓝色
                    }
                }, totalDelay);
            }
        }
        
        mainHandler.postDelayed(this::turnOffAll, cycles * halfCount * delayPerStep);
    }
    
    /**
     * HSV转RGB
     * @param hue 色相 0-360
     * @param saturation 饱和度 0-1
     * @param value 明度 0-1
     * @return RGB数组 [r, g, b] 0-255
     */
    private int[] hsvToRgb(float hue, float saturation, float value) {
        int h = (int) (hue / 60f);
        float f = hue / 60f - h;
        float p = value * (1 - saturation);
        float q = value * (1 - f * saturation);
        float t = value * (1 - (1 - f) * saturation);
        
        float r = 0, g = 0, b = 0;
        switch (h) {
            case 0:
            case 6:
                r = value;
                g = t;
                b = p;
                break;
            case 1:
                r = q;
                g = value;
                b = p;
                break;
            case 2:
                r = p;
                g = value;
                b = t;
                break;
            case 3:
                r = p;
                g = q;
                b = value;
                break;
            case 4:
                r = t;
                g = p;
                b = value;
                break;
            case 5:
                r = value;
                g = p;
                b = q;
                break;
            default:
                r = g = b = 0;
        }
        
        return new int[]{
            (int) (r * 255),
            (int) (g * 255),
            (int) (b * 255)
        };
    }
}
