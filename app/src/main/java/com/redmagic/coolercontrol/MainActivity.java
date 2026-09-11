package com.redmagic.coolercontrol;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.redmagic.coolercontrol.ble.BleManager;
import com.redmagic.coolercontrol.ble.WriteQueue;
import com.redmagic.coolercontrol.control.FanController;
import com.redmagic.coolercontrol.control.LedController;

public class MainActivity extends Activity {
    
    private static final int LED_COUNT = 16;
    private static final int REQ_PERMISSIONS = 1001;
    
    private WriteQueue writeQueue;
    private BleManager bleManager;
    private FanController fanController;
    private LedController ledController;
    
    private TextView statusText;
    private TextView fanLevelLabel;
    private SeekBar fanSeek;
    private TextView selectedLabel;
    private Button[] ledButtons = new Button[LED_COUNT];
    private SeekBar redSeek, greenSeek, blueSeek;
    private TextView redValue, greenValue, blueValue;
    private TextView logView;
    private ScrollView logScrollView;
    private final StringBuilder logBuffer = new StringBuilder();
    
    private int selectedLed = 0;
    private int red = 32;
    private int green = 0;
    private int blue = 0;
    private int fanLevel = 1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        writeQueue = new WriteQueue();
        bleManager = new BleManager(this, writeQueue);
        fanController = new FanController(bleManager, writeQueue);
        ledController = new LedController(bleManager, writeQueue);
        
        writeQueue.setStatusListener(message -> {
            setStatus(message);
            addLog("[WriteQueue] " + message);
        });
        bleManager.setStatusListener(new BleManager.StatusListener() {
            @Override
            public void onStatus(String message) {
                setStatus(message);
                addLog("[BLE] " + message);
            }
            
            @Override
            public void onCharacteristicsReady() {
                // Characteristics are ready
            }
        });
        
        setContentView(buildUi());
        requestNeededPermissions();
    }
    
    @Override
    protected void onDestroy() {
        bleManager.disconnect();
        super.onDestroy();
    }
    
    private View buildUi() {
        int p = dp(16);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(p, dp(12), p, dp(28));
        mainLayout.setBackgroundColor(Color.rgb(250, 250, 250));
        scrollView.addView(mainLayout);
        
        // Title
        mainLayout.addView(text("红魔 8 Pro · 逐灯/原生控制测试器 V5", 22, true));
        
        // Hint
        TextView hint = text("适用于 8.4.7 PerPixel F0/F1 COMPAT V5 固件。1013 始终保持原厂 4 字节；逐灯使用 F0/F1 双包，RGB 已实机确认。9种灯效+全面日志！", 14, false);
        hint.setPadding(0, dp(4), 0, dp(10));
        mainLayout.addView(hint);
        
        // Status
        statusText = text("状态：未连接", 15, true);
        statusText.setPadding(0, dp(8), 0, dp(8));
        mainLayout.addView(statusText);
        
        // Scan and disconnect buttons
        LinearLayout connectRow = horizontal();
        Button scanBtn = button("扫描并连接");
        scanBtn.setOnClickListener(v -> bleManager.startScan());
        connectRow.addView(scanBtn, weight());
        
        Button disconnectBtn = button("断开");
        disconnectBtn.setOnClickListener(v -> bleManager.disconnect());
        connectRow.addView(disconnectBtn, weight());
        mainLayout.addView(connectRow);
        
        // Fan control section
        addSection(mainLayout, "原有风扇 / 制冷模式");
        
        fanLevelLabel = text("手动档位：1 / 10（1012=0x28）", 16, true);
        mainLayout.addView(fanLevelLabel);
        
        fanSeek = new SeekBar(this);
        fanSeek.setMax(9);
        fanSeek.setProgress(0);
        fanSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                fanLevel = progress + 1;
                updateFanLabel();
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        mainLayout.addView(fanSeek, new LinearLayout.LayoutParams(-1, dp(48)));
        
        LinearLayout fanRow1 = horizontal();
        Button manualBtn = button("应用手动档位");
        manualBtn.setOnClickListener(v -> {
            addLog("设置手动档位: " + fanLevel);
            fanController.setManualFanLevel(fanLevel);
        });
        fanRow1.addView(manualBtn, weight());
        
        Button smartBtn = button("智能温控");
        smartBtn.setOnClickListener(v -> {
            addLog("启动智能温控模式");
            fanController.setSmartMode();
        });
        fanRow1.addView(smartBtn, weight());
        mainLayout.addView(fanRow1);
        
        LinearLayout fanRow2 = horizontal();
        Button boostBtn = button("破坏神");
        boostBtn.setOnClickListener(v -> {
            addLog("启动破坏神模式");
            fanController.setBoostMode();
        });
        fanRow2.addView(boostBtn, weight());
        
        Button powerOffBtn = button("关闭散热");
        powerOffBtn.setOnClickListener(v -> {
            addLog("关闭散热器");
            fanController.powerOff();
        });
        fanRow2.addView(powerOffBtn, weight());
        mainLayout.addView(fanRow2);
        
        // Native light modes
        addSection(mainLayout, "原有灯光模式（4 字节 1013）");
        
        TextView lightHint = text("逐灯 F0/F1 与原生灯光共用 UUID1013，但所有包都严格保持 4 字节。点击下面任一原生模式即可重新走固件原来的 4 字节灯光逻辑。", 13, false);
        mainLayout.addView(lightHint);
        
        LinearLayout native1 = horizontal();
        Button mode01 = button("模式01 默认/联动");
        mode01.setOnClickListener(v -> {
            addLog("原生灯光: 模式01");
            ledController.sendNativeLight(1, 0, 0, 0);
        });
        native1.addView(mode01, weight());
        
        Button mode03 = button("模式03 + RGB");
        mode03.setOnClickListener(v -> {
            addLog(String.format("原生灯光: 模式03 RGB(%d,%d,%d)", red, green, blue));
            ledController.sendNativeLight(3, red, green, blue);
        });
        native1.addView(mode03, weight());
        mainLayout.addView(native1);
        
        LinearLayout native2 = horizontal();
        Button mode04 = button("模式04 + RGB");
        mode04.setOnClickListener(v -> {
            addLog(String.format("原生灯光: 模式04 RGB(%d,%d,%d)", red, green, blue));
            ledController.sendNativeLight(4, red, green, blue);
        });
        native2.addView(mode04, weight());
        
        Button mode06 = button("模式06 原生");
        mode06.setOnClickListener(v -> {
            addLog("原生灯光: 模式06");
            ledController.sendNativeLight(6, 0, 0, 0);
        });
        native2.addView(mode06, weight());
        mainLayout.addView(native2);
        
        // Per-pixel LED control
        addSection(mainLayout, "16 灯珠逐颗控制（F0/F1 双包）");
        
        selectedLabel = text("当前灯珠：1 / 16", 17, true);
        selectedLabel.setPadding(0, dp(4), 0, dp(8));
        mainLayout.addView(selectedLabel);
        
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        
        for (int i = 0; i < LED_COUNT; i++) {
            final int idx = i;
            Button b = button(String.valueOf(i + 1));
            ledButtons[i] = b;
            b.setOnClickListener(v -> {
                selectedLed = idx;
                updateLedSelection();
            });
            
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(48);
            lp.rowSpec = GridLayout.spec(i / 4, 1.0f);
            lp.columnSpec = GridLayout.spec(i % 4, 1.0f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(b, lp);
        }
        mainLayout.addView(grid);
        updateLedSelection();
        
        // RGB controls
        redValue = text("R: 32", 16, false);
        mainLayout.addView(redValue);
        redSeek = new SeekBar(this);
        redSeek.setMax(255);
        redSeek.setProgress(32);
        redSeek.setOnSeekBarChangeListener(createChannelListener(color -> {
            red = color;
            redValue.setText("R: " + red);
        }));
        mainLayout.addView(redSeek, new LinearLayout.LayoutParams(-1, dp(48)));
        
        greenValue = text("G: 0", 16, false);
        mainLayout.addView(greenValue);
        greenSeek = new SeekBar(this);
        greenSeek.setMax(255);
        greenSeek.setProgress(0);
        greenSeek.setOnSeekBarChangeListener(createChannelListener(color -> {
            green = color;
            greenValue.setText("G: " + green);
        }));
        mainLayout.addView(greenSeek, new LinearLayout.LayoutParams(-1, dp(48)));
        
        blueValue = text("B: 0", 16, false);
        mainLayout.addView(blueValue);
        blueSeek = new SeekBar(this);
        blueSeek.setMax(255);
        blueSeek.setProgress(0);
        blueSeek.setOnSeekBarChangeListener(createChannelListener(color -> {
            blue = color;
            blueValue.setText("B: " + blue);
        }));
        mainLayout.addView(blueSeek, new LinearLayout.LayoutParams(-1, dp(48)));
        
        // Preset colors
        LinearLayout presets = horizontal();
        Button pr = button("低亮红");
        pr.setOnClickListener(v -> setPreset(32, 0, 0));
        presets.addView(pr, weight());
        
        Button pg = button("低亮绿");
        pg.setOnClickListener(v -> setPreset(0, 32, 0));
        presets.addView(pg, weight());
        
        Button pb = button("低亮蓝");
        pb.setOnClickListener(v -> setPreset(0, 0, 32));
        presets.addView(pb, weight());
        mainLayout.addView(presets);
        
        // Action buttons
        LinearLayout actions = horizontal();
        Button apply = button("发送当前 RGB");
        apply.setOnClickListener(v -> {
            addLog(String.format("设置LED#%d RGB(%d,%d,%d)", selectedLed + 1, red, green, blue));
            ledController.sendPerPixelColor(selectedLed, red, green, blue);
        });
        actions.addView(apply, weight());
        
        Button off = button("全部熄灭");
        off.setOnClickListener(v -> {
            addLog("全部LED熄灭");
            ledController.turnOffAll();
        });
        actions.addView(off, weight());
        mainLayout.addView(actions);
        
        Button chase = button("单灯跑一圈（红=32）");
        chase.setOnClickListener(v -> {
            addLog("启动单灯跑圈动画");
            ledController.chaseAnimation();
        });
        LinearLayout.LayoutParams chaseLp = new LinearLayout.LayoutParams(-1, dp(52));
        chaseLp.topMargin = dp(8);
        mainLayout.addView(chase, chaseLp);
        
        // New LED effects section
        addSection(mainLayout, "🎨 灯效动画");
        
        // Row 1: Rainbow effects
        LinearLayout effects1 = horizontal();
        Button rainbowChase = button("🌈 彩虹跑马");
        rainbowChase.setOnClickListener(v -> {
            addLog("启动灯效: 彩虹跑马灯");
            ledController.rainbowChase();
        });
        effects1.addView(rainbowChase, weight());
        
        Button rainbowCycle = button("🌈 彩虹循环");
        rainbowCycle.setOnClickListener(v -> {
            addLog("启动灯效: 彩虹循环");
            ledController.rainbowCycle();
        });
        effects1.addView(rainbowCycle, weight());
        
        Button waveEffect = button("🌊 彩虹波浪");
        waveEffect.setOnClickListener(v -> {
            addLog("启动灯效: 彩虹波浪");
            ledController.waveEffect();
        });
        effects1.addView(waveEffect, weight());
        mainLayout.addView(effects1);
        
        // Row 2: Dynamic effects
        LinearLayout effects2 = horizontal();
        Button meteorEffect = button("☄️ 流星拖尾");
        meteorEffect.setOnClickListener(v -> {
            addLog("启动灯效: 流星拖尾");
            ledController.meteorEffect();
        });
        effects2.addView(meteorEffect, weight());
        
        Button theaterEffect = button("🎭 剧场追逐");
        theaterEffect.setOnClickListener(v -> {
            addLog("启动灯效: 剧场追逐");
            ledController.theaterChase(80, 0, 80);
        });
        effects2.addView(theaterEffect, weight());
        
        Button strobeEffect = button("⚡ 频闪");
        strobeEffect.setOnClickListener(v -> {
            addLog("启动灯效: 频闪");
            ledController.strobeEffect(100, 100, 100);
        });
        effects2.addView(strobeEffect, weight());
        mainLayout.addView(effects2);
        
        // Row 3: Breathing and wipe
        LinearLayout effects3 = horizontal();
        Button breathingEffect = button("💫 呼吸灯");
        breathingEffect.setOnClickListener(v -> {
            addLog("启动灯效: 呼吸灯");
            ledController.breathingEffect(80, 40, 0);
        });
        effects3.addView(breathingEffect, weight());
        
        Button colorWipe = button("🎨 颜色填充");
        colorWipe.setOnClickListener(v -> {
            addLog(String.format("启动灯效: 颜色填充 RGB(%d,%d,%d)", red, green, blue));
            ledController.colorWipe(red, green, blue);
        });
        effects3.addView(colorWipe, weight());
        
        Button stopAnimation = button("⏹️ 停止动画");
        stopAnimation.setOnClickListener(v -> {
            addLog("停止所有LED动画");
            ledController.stopAnimation();
            setStatus("已停止动画");
        });
        effects3.addView(stopAnimation, weight());
        mainLayout.addView(effects3);
        
        // Protocol info
        TextView protocol = text("逐灯双包：F0 + 灯珠编号 + R + G（暂存）\\n" +
                "　　　　　 F1 + 灯珠编号 + B + 00（提交并刷新）\\n" +
                "手动档位：1011=02 → 1018=00 → 1017=00 → 1012=(0x28 + 4×(档位-1))\\n" +
                "智能温控：1011=02 → 1017=00 → 1018=01\\n" +
                "破坏神：1011=02 → 1018=00 → 1012=50 → 1017=01", 13, false);
        protocol.setPadding(0, dp(14), 0, 0);
        mainLayout.addView(protocol);
        
        // Log section
        addSection(mainLayout, "📝 操作日志");
        
        // Log controls
        LinearLayout logControls = horizontal();
        Button clearLog = button("清空日志");
        clearLog.setOnClickListener(v -> clearLog());
        logControls.addView(clearLog, weight());
        
        Button copyLog = button("复制日志");
        copyLog.setOnClickListener(v -> copyLogToClipboard());
        logControls.addView(copyLog, weight());
        mainLayout.addView(logControls);
        
        // Log view
        logScrollView = new ScrollView(this);
        logScrollView.setBackgroundColor(Color.rgb(240, 240, 240));
        LinearLayout.LayoutParams logScrollLp = new LinearLayout.LayoutParams(-1, dp(200));
        logScrollLp.topMargin = dp(8);
        
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(Color.rgb(60, 60, 60));
        logView.setPadding(dp(8), dp(8), dp(8), dp(8));
        logView.setTypeface(Typeface.MONOSPACE);
        logScrollView.addView(logView);
        mainLayout.addView(logScrollView, logScrollLp);
        
        addLog("=== 应用启动 ===");
        
        return scrollView;
    }
    
    private void addSection(LinearLayout parent, String title) {
        TextView section = text(title, 18, true);
        section.setPadding(0, dp(18), 0, dp(8));
        section.setTextColor(Color.rgb(40, 90, 150));
        parent.addView(section);
    }
    
    private SeekBar.OnSeekBarChangeListener createChannelListener(ColorCallback callback) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                callback.onColorChanged(progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }
    
    private interface ColorCallback {
        void onColorChanged(int value);
    }
    
    private void setPreset(int r, int g, int b) {
        red = r;
        green = g;
        blue = b;
        redSeek.setProgress(r);
        greenSeek.setProgress(g);
        blueSeek.setProgress(b);
        redValue.setText("R: " + r);
        greenValue.setText("G: " + g);
        blueValue.setText("B: " + b);
        addLog(String.format("预设颜色: RGB(%d,%d,%d)", r, g, b));
    }
    
    private void updateFanLabel() {
        int value = 0x28 + 4 * (fanLevel - 1);
        fanLevelLabel.setText(String.format("手动档位：%d / 10（1012=0x%02X）", fanLevel, value));
    }
    
    private void updateLedSelection() {
        for (int i = 0; i < LED_COUNT; i++) {
            if (i == selectedLed) {
                ledButtons[i].setBackgroundColor(Color.rgb(100, 150, 255));
            } else {
                ledButtons[i].setBackgroundColor(Color.rgb(220, 220, 220));
            }
        }
        selectedLabel.setText("当前灯珠：" + (selectedLed + 1) + " / 16");
    }
    
    private void setStatus(String message) {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText("状态：" + message);
            }
        });
    }
    
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(16); // CENTER
        return l;
    }
    
    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1.0f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }
    
    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14.0f);
        return b;
    }
    
    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(25, 25, 28));
        if (bold) {
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return t;
    }
    
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
    
    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission("android.permission.BLUETOOTH_SCAN") == 0 &&
                   checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == 0;
        }
        return checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == 0;
    }
    
    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_CONNECT"
            }, REQ_PERMISSIONS);
        } else {
            requestPermissions(new String[]{
                "android.permission.ACCESS_FINE_LOCATION"
            }, REQ_PERMISSIONS);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS && !hasPermissions()) {
            Toast.makeText(this, "需要附近设备/蓝牙权限才能扫描散热器", Toast.LENGTH_LONG).show();
        }
    }
    
    private void addLog(String message) {
        runOnUiThread(() -> {
            if (logView == null) return;
            
            // Add timestamp
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            String logLine = "[" + timestamp + "] " + message + "\n";
            
            logBuffer.append(logLine);
            
            // Keep only last 100 lines
            String[] lines = logBuffer.toString().split("\n");
            if (lines.length > 100) {
                logBuffer.setLength(0);
                for (int i = lines.length - 100; i < lines.length; i++) {
                    logBuffer.append(lines[i]).append("\n");
                }
            }
            
            logView.setText(logBuffer.toString());
            
            // Auto scroll to bottom
            logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
    
    private void clearLog() {
        logBuffer.setLength(0);
        if (logView != null) {
            logView.setText("");
        }
        addLog("=== 日志已清空 ===");
    }
    
    private void copyLogToClipboard() {
        if (Build.VERSION.SDK_INT >= 11) {
            android.content.ClipboardManager clipboard = 
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("操作日志", logBuffer.toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }
}
