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
    private Runnable currentAnimation;
    private boolean isAnimationRunning = false;
    
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
        stopAnimation(); // Stop any running animation first
        for (int i = 0; i < LED_COUNT; i++) {
            sendPerPixelColor(i, 0, 0, 0);
        }
    }
    
    public void chaseAnimation() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int ledIndex = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                // Turn off all first
                for (int j = 0; j < LED_COUNT; j++) {
                    if (j != ledIndex) {
                        sendPerPixelColor(j, 0, 0, 0);
                    }
                }
                // Turn on current LED
                sendPerPixelColor(ledIndex, 32, 0, 0);
                
                ledIndex = (ledIndex + 1) % LED_COUNT;
                mainHandler.postDelayed(this, 300);
            }
        };
        
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Stop any running animation
     */
    public void stopAnimation() {
        isAnimationRunning = false;
        // Remove all pending callbacks and messages from the handler
        mainHandler.removeCallbacksAndMessages(null);
        currentAnimation = null;
        // Clear WriteQueue to stop any pending LED writes
        writeQueue.clear();
    }
    
    /**
     * Rainbow chase effect - colorful LED moving around
     */
    public void rainbowChase() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int position = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    int hue = (position + i * 22) % 360; // 22 degrees per LED
                    int[] rgb = hsvToRgb(hue, 1.0f, 0.3f); // Medium brightness
                    sendPerPixelColor(i, rgb[0], rgb[1], rgb[2]);
                }
                
                position = (position + 10) % 360;
                mainHandler.postDelayed(this, 50); // 20 FPS
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Rainbow cycle - all LEDs show rainbow gradient
     */
    public void rainbowCycle() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int offset = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    int hue = (offset + i * (360 / LED_COUNT)) % 360;
                    int[] rgb = hsvToRgb(hue, 1.0f, 0.3f);
                    sendPerPixelColor(i, rgb[0], rgb[1], rgb[2]);
                }
                
                offset = (offset + 5) % 360;
                mainHandler.postDelayed(this, 50);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Breathing effect - smooth brightness fade in/out
     */
    public void breathingEffect(int r, int g, int b) {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            float brightness = 0.0f;
            boolean increasing = true;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                // Update brightness
                if (increasing) {
                    brightness += 0.02f;
                    if (brightness >= 1.0f) {
                        brightness = 1.0f;
                        increasing = false;
                    }
                } else {
                    brightness -= 0.02f;
                    if (brightness <= 0.0f) {
                        brightness = 0.0f;
                        increasing = true;
                    }
                }
                
                // Apply to all LEDs
                int br = (int) (r * brightness);
                int bg = (int) (g * brightness);
                int bb = (int) (b * brightness);
                
                for (int i = 0; i < LED_COUNT; i++) {
                    sendPerPixelColor(i, br, bg, bb);
                }
                
                mainHandler.postDelayed(this, 30);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Wave effect - color wave propagating
     */
    public void waveEffect() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int wavePosition = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    // Calculate wave intensity for this LED
                    double distance = Math.abs(i - wavePosition);
                    double intensity = Math.max(0, 1.0 - (distance / 8.0));
                    
                    int hue = (wavePosition * 30) % 360;
                    int[] rgb = hsvToRgb(hue, 1.0f, (float) (intensity * 0.4f));
                    sendPerPixelColor(i, rgb[0], rgb[1], rgb[2]);
                }
                
                wavePosition = (wavePosition + 1) % LED_COUNT;
                mainHandler.postDelayed(this, 80);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Meteor effect - shooting star with trail
     */
    public void meteorEffect() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int meteorPos = 0;
            final int trailLength = 5;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                // Clear all
                for (int i = 0; i < LED_COUNT; i++) {
                    sendPerPixelColor(i, 0, 0, 0);
                }
                
                // Draw meteor with trail
                for (int i = 0; i < trailLength; i++) {
                    int pos = (meteorPos - i + LED_COUNT) % LED_COUNT;
                    float intensity = 1.0f - (i / (float) trailLength);
                    int brightness = (int) (80 * intensity);
                    sendPerPixelColor(pos, brightness, brightness, brightness / 2);
                }
                
                meteorPos = (meteorPos + 1) % LED_COUNT;
                mainHandler.postDelayed(this, 60);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Strobe effect - rapid flashing
     */
    public void strobeEffect(int r, int g, int b) {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            boolean on = false;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    if (on) {
                        sendPerPixelColor(i, r, g, b);
                    } else {
                        sendPerPixelColor(i, 0, 0, 0);
                    }
                }
                
                on = !on;
                mainHandler.postDelayed(this, 100);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Theater chase effect - alternating groups
     */
    public void theaterChase(int r, int g, int b) {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int step = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    if ((i + step) % 3 == 0) {
                        sendPerPixelColor(i, r, g, b);
                    } else {
                        sendPerPixelColor(i, 0, 0, 0);
                    }
                }
                
                step = (step + 1) % 3;
                mainHandler.postDelayed(this, 200);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Color wipe - fill LEDs one by one
     */
    public void colorWipe(int r, int g, int b) {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int index = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                sendPerPixelColor(index, r, g, b);
                
                index++;
                if (index < LED_COUNT) {
                    mainHandler.postDelayed(this, 100);
                } else {
                    isAnimationRunning = false;
                }
            }
        };
        
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Convert HSV to RGB
     * @param h Hue (0-360)
     * @param s Saturation (0-1)
     * @param v Value/Brightness (0-1)
     * @return RGB array [r, g, b] (0-255)
     */
    private int[] hsvToRgb(int h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs(((h / 60.0f) % 2) - 1));
        float m = v - c;
        
        float r, g, b;
        
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        
        return new int[]{
            (int) ((r + m) * 255),
            (int) ((g + m) * 255),
            (int) ((b + m) * 255)
        };
    }
}
