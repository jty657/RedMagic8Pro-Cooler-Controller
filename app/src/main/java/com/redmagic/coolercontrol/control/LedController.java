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
    
    // ============ NEW EFFECTS (10-30) ============
    
    /**
     * Fire effect - flickering red/orange flames
     */
    public void fireEffect() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    int r = 200 + (int)(Math.random() * 55); // 200-255
                    int g = 60 + (int)(Math.random() * 25);   // 60-85
                    int b = 0;
                    sendPerPixelColor(i, r, g, b);
                }
                
                mainHandler.postDelayed(this, 80);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Water effect - blue wave from center
     */
    public void waterEffect() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int wavePos = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                int center = LED_COUNT / 2;
                for (int i = 0; i < LED_COUNT; i++) {
                    int dist = Math.abs(i - center);
                    int phase = (wavePos + dist * 40) % 360;
                    float brightness = (float)(Math.sin(Math.toRadians(phase)) + 1) / 2;
                    int blue = (int)(brightness * 100);
                    sendPerPixelColor(i, 0, blue / 3, blue);
                }
                
                wavePos = (wavePos + 15) % 360;
                mainHandler.postDelayed(this, 50);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Sunrise effect - deep blue to yellow/orange
     */
    public void sunriseEffect() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int step = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                float progress = (step % 200) / 200.0f;
                int r = (int)(progress * 255);
                int g = (int)(progress * 180);
                int b = (int)((1 - progress) * 100);
                
                for (int i = 0; i < LED_COUNT; i++) {
                    sendPerPixelColor(i, r, g, b);
                }
                
                step++;
                mainHandler.postDelayed(this, 40);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Sunset effect - orange/red to deep purple
     */
    public void sunsetEffect() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int step = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                float progress = (step % 200) / 200.0f;
                int r = (int)((1 - progress * 0.5) * 200);
                int g = (int)((1 - progress) * 100);
                int b = (int)(progress * 80);
                
                for (int i = 0; i < LED_COUNT; i++) {
                    sendPerPixelColor(i, r, g, b);
                }
                
                step++;
                mainHandler.postDelayed(this, 40);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Aurora borealis - flowing green/purple waves
     */
    public void auraBorealis() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int offset = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    float wave1 = (float)Math.sin(Math.toRadians((i * 30 + offset) % 360));
                    float wave2 = (float)Math.sin(Math.toRadians((i * 20 + offset * 1.5) % 360));
                    int g = (int)((wave1 + 1) * 40);
                    int b = (int)((wave2 + 1) * 30);
                    sendPerPixelColor(i, 10, g, b);
                }
                
                offset = (offset + 5) % 360;
                mainHandler.postDelayed(this, 60);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Candle flicker - warm yellow gentle flicker
     */
    public void candleFlicker() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    int flicker = 40 + (int)(Math.random() * 30);
                    sendPerPixelColor(i, flicker, flicker - 10, 0);
                }
                
                mainHandler.postDelayed(this, 100);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Dual chase - two dots moving from ends to center
     */
    public void dualChase() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int pos = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    sendPerPixelColor(i, 0, 0, 0);
                }
                
                int pos1 = pos % LED_COUNT;
                int pos2 = (LED_COUNT - 1 - pos) % LED_COUNT;
                sendPerPixelColor(pos1, 0, 50, 0);
                sendPerPixelColor(pos2, 50, 0, 0);
                
                pos++;
                mainHandler.postDelayed(this, 100);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Ping pong - single dot bouncing back and forth
     */
    public void pingPong() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int pos = 0;
            int direction = 1;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    sendPerPixelColor(i, 0, 0, 0);
                }
                
                sendPerPixelColor(pos, 50, 50, 0);
                
                pos += direction;
                if (pos >= LED_COUNT - 1 || pos <= 0) {
                    direction = -direction;
                }
                
                mainHandler.postDelayed(this, 80);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Spiral - rotating spiral pattern
     */
    public void spiral() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int offset = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    int hue = ((i * 60 + offset) % 360);
                    float brightness = (float)Math.sin(Math.toRadians(hue)) * 0.5f + 0.5f;
                    int[] rgb = hsvToRgb(hue, 1.0f, brightness * 0.3f);
                    sendPerPixelColor(i, rgb[0], rgb[1], rgb[2]);
                }
                
                offset = (offset + 10) % 360;
                mainHandler.postDelayed(this, 50);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Random blink - multiple LEDs randomly lighting up
     */
    public void randomBlink() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    if (Math.random() < 0.3) {
                        int hue = (int)(Math.random() * 360);
                        int[] rgb = hsvToRgb(hue, 1.0f, 0.4f);
                        sendPerPixelColor(i, rgb[0], rgb[1], rgb[2]);
                    } else {
                        sendPerPixelColor(i, 0, 0, 0);
                    }
                }
                
                mainHandler.postDelayed(this, 150);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Snake - moving with trailing tail
     */
    public void snake() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int headPos = 0;
            final int tailLength = 4;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    sendPerPixelColor(i, 0, 0, 0);
                }
                
                for (int i = 0; i < tailLength; i++) {
                    int pos = (headPos - i + LED_COUNT) % LED_COUNT;
                    float intensity = 1.0f - (i / (float)tailLength);
                    int brightness = (int)(intensity * 60);
                    sendPerPixelColor(pos, 0, brightness, 0);
                }
                
                headPos = (headPos + 1) % LED_COUNT;
                mainHandler.postDelayed(this, 100);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Scanner (KITT) - back and forth with fade trail
     */
    public void scanner() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int pos = 0;
            int direction = 1;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    int dist = Math.abs(i - pos);
                    int brightness = Math.max(0, 80 - dist * 20);
                    sendPerPixelColor(i, brightness, 0, 0);
                }
                
                pos += direction;
                if (pos >= LED_COUNT - 1 || pos <= 0) {
                    direction = -direction;
                }
                
                mainHandler.postDelayed(this, 60);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Comet - long trailing tail
     */
    public void comet() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int cometPos = 0;
            final int cometLength = 8;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    sendPerPixelColor(i, 0, 0, 0);
                }
                
                for (int i = 0; i < cometLength; i++) {
                    int pos = (cometPos - i + LED_COUNT) % LED_COUNT;
                    float intensity = 1.0f - (i / (float)cometLength);
                    int brightness = (int)(intensity * 100);
                    sendPerPixelColor(pos, brightness, brightness, brightness);
                }
                
                cometPos = (cometPos + 1) % LED_COUNT;
                mainHandler.postDelayed(this, 50);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Color fade - smooth RGB transition
     */
    public void colorFade(int r1, int g1, int b1, int r2, int g2, int b2) {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int step = 0;
            boolean forward = true;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                float progress = (step % 100) / 100.0f;
                if (!forward) progress = 1.0f - progress;
                
                int r = (int)(r1 + (r2 - r1) * progress);
                int g = (int)(g1 + (g2 - g1) * progress);
                int b = (int)(b1 + (b2 - b1) * progress);
                
                for (int i = 0; i < LED_COUNT; i++) {
                    sendPerPixelColor(i, r, g, b);
                }
                
                step++;
                if (step >= 100) {
                    step = 0;
                    forward = !forward;
                }
                
                mainHandler.postDelayed(this, 50);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Rainbow fade - all LEDs same color cycling through rainbow
     */
    public void rainbowFade() {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int hue = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                int[] rgb = hsvToRgb(hue, 1.0f, 0.3f);
                for (int i = 0; i < LED_COUNT; i++) {
                    sendPerPixelColor(i, rgb[0], rgb[1], rgb[2]);
                }
                
                hue = (hue + 3) % 360;
                mainHandler.postDelayed(this, 40);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Twinkle - each LED independently random brightness
     */
    public void twinkle(int r, int g, int b) {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    if (Math.random() < 0.05) { // 5% chance to toggle
                        if (Math.random() < 0.5) {
                            sendPerPixelColor(i, r, g, b);
                        } else {
                            sendPerPixelColor(i, 0, 0, 0);
                        }
                    }
                }
                
                mainHandler.postDelayed(this, 100);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Sparkle - dark background with random bright spots
     */
    public void sparkle(int r, int g, int b) {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                // Dim background
                for (int i = 0; i < LED_COUNT; i++) {
                    sendPerPixelColor(i, r/8, g/8, b/8);
                }
                
                // Random bright sparkles
                int numSparkles = 2 + (int)(Math.random() * 3);
                for (int i = 0; i < numSparkles; i++) {
                    int pos = (int)(Math.random() * LED_COUNT);
                    sendPerPixelColor(pos, r, g, b);
                }
                
                mainHandler.postDelayed(this, 100);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Pulse - brightness wave from center outward
     */
    public void pulse(int r, int g, int b) {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int pulsePos = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                int center = LED_COUNT / 2;
                for (int i = 0; i < LED_COUNT; i++) {
                    int dist = Math.abs(i - center);
                    int effectiveDist = (pulsePos - dist + LED_COUNT) % LED_COUNT;
                    float brightness = effectiveDist < 3 ? (1.0f - effectiveDist / 3.0f) : 0;
                    sendPerPixelColor(i, (int)(r * brightness), (int)(g * brightness), (int)(b * brightness));
                }
                
                pulsePos = (pulsePos + 1) % LED_COUNT;
                mainHandler.postDelayed(this, 80);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Half and half - left/right different colors
     */
    public void halfAndHalf(int r1, int g1, int b1, int r2, int g2, int b2) {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            boolean swap = false;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                int half = LED_COUNT / 2;
                for (int i = 0; i < LED_COUNT; i++) {
                    if ((i < half) != swap) {
                        sendPerPixelColor(i, r1, g1, b1);
                    } else {
                        sendPerPixelColor(i, r2, g2, b2);
                    }
                }
                
                swap = !swap;
                mainHandler.postDelayed(this, 500);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Alternate - odd/even LEDs alternating
     */
    public void alternate(int r, int g, int b) {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            boolean oddOn = true;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    if ((i % 2 == 0) == oddOn) {
                        sendPerPixelColor(i, r, g, b);
                    } else {
                        sendPerPixelColor(i, 0, 0, 0);
                    }
                }
                
                oddOn = !oddOn;
                mainHandler.postDelayed(this, 300);
            }
        };
        mainHandler.post(currentAnimation);
    }
    
    /**
     * Loading - progress bar style filling and repeating
     */
    public void loading(int r, int g, int b) {
        stopAnimation();
        isAnimationRunning = true;
        
        currentAnimation = new Runnable() {
            int fillPos = 0;
            
            @Override
            public void run() {
                if (!isAnimationRunning) return;
                
                for (int i = 0; i < LED_COUNT; i++) {
                    if (i <= fillPos) {
                        sendPerPixelColor(i, r, g, b);
                    } else {
                        sendPerPixelColor(i, 0, 0, 0);
                    }
                }
                
                fillPos++;
                if (fillPos >= LED_COUNT) {
                    fillPos = 0;
                }
                
                mainHandler.postDelayed(this, 100);
            }
        };
        mainHandler.post(currentAnimation);
    }
}
