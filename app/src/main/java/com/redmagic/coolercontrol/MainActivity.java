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
        mainLayout.setBackgroundColor(Color.rgb(245, 247, 250)); // Light modern gray
        scrollView.addView(mainLayout);
        
        // Title
        TextView titleView = text("红魔 8 Pro · 逐灯/原生控制测试器 V6", 22, true);
        titleView.setTextColor(Color.rgb(66, 133, 244));
        titleView.setPadding(0, dp(4), 0, dp(4));
        mainLayout.addView(titleView);
        
        // Hint
        TextView hint = text("适用于 8.4.7 PerPixel F0/F1 COMPAT V6 固件。1013 始终保持原厂 4 字节；逐灯使用 F0/F1 双包，RGB 已实机确认。动画停止修复+完整BLE日志+现代UI！", 14, false);
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
        
        // 30 LED Effects Section
        addSection(mainLayout, "🎨 30种炫酷灯效");
        
        // Row 1
        LinearLayout row1 = horizontal();
        Button chase = button("1.追逐");
        chase.setOnClickListener(v -> { addLog("灯效: 追逐"); ledController.chaseAnimation(); });
        row1.addView(chase, weight());
        Button rainbowChase = button("2.彩虹跑马");
        rainbowChase.setOnClickListener(v -> { addLog("灯效: 彩虹跑马"); ledController.rainbowChase(); });
        row1.addView(rainbowChase, weight());
        Button rainbowCycle = button("3.彩虹循环");
        rainbowCycle.setOnClickListener(v -> { addLog("灯效: 彩虹循环"); ledController.rainbowCycle(); });
        row1.addView(rainbowCycle, weight());
        mainLayout.addView(row1);
        
        // Row 2
        LinearLayout row2 = horizontal();
        Button breathing = button("4.呼吸");
        breathing.setOnClickListener(v -> { addLog("灯效: 呼吸"); ledController.breathingEffect(80, 40, 0); });
        row2.addView(breathing, weight());
        Button wave = button("5.波浪");
        wave.setOnClickListener(v -> { addLog("灯效: 波浪"); ledController.waveEffect(); });
        row2.addView(wave, weight());
        Button meteor = button("6.流星");
        meteor.setOnClickListener(v -> { addLog("灯效: 流星"); ledController.meteorEffect(); });
        row2.addView(meteor, weight());
        mainLayout.addView(row2);
        
        // Row 3
        LinearLayout row3 = horizontal();
        Button strobe = button("7.频闪");
        strobe.setOnClickListener(v -> { addLog("灯效: 频闪"); ledController.strobeEffect(100, 100, 100); });
        row3.addView(strobe, weight());
        Button theater = button("8.剧场");
        theater.setOnClickListener(v -> { addLog("灯效: 剧场"); ledController.theaterChase(80, 0, 80); });
        row3.addView(theater, weight());
        Button wipe = button("9.填充");
        wipe.setOnClickListener(v -> { addLog("灯效: 填充"); ledController.colorWipe(red, green, blue); });
        row3.addView(wipe, weight());
        mainLayout.addView(row3);
        
        // Row 4
        LinearLayout row4 = horizontal();
        Button fire = button("10.🔥火焰");
        fire.setOnClickListener(v -> { addLog("灯效: 火焰"); ledController.fireEffect(); });
        row4.addView(fire, weight());
        Button water = button("11.💧水波");
        water.setOnClickListener(v -> { addLog("灯效: 水波"); ledController.waterEffect(); });
        row4.addView(water, weight());
        Button sunrise = button("12.🌅日出");
        sunrise.setOnClickListener(v -> { addLog("灯效: 日出"); ledController.sunriseEffect(); });
        row4.addView(sunrise, weight());
        mainLayout.addView(row4);
        
        // Row 5
        LinearLayout row5 = horizontal();
        Button sunset = button("13.🌇日落");
        sunset.setOnClickListener(v -> { addLog("灯效: 日落"); ledController.sunsetEffect(); });
        row5.addView(sunset, weight());
        Button aurora = button("14.🌌极光");
        aurora.setOnClickListener(v -> { addLog("灯效: 极光"); ledController.auraBorealis(); });
        row5.addView(aurora, weight());
        Button candle = button("15.🕯蜡烛");
        candle.setOnClickListener(v -> { addLog("灯效: 蜡烛"); ledController.candleFlicker(); });
        row5.addView(candle, weight());
        mainLayout.addView(row5);
        
        // Row 6
        LinearLayout row6 = horizontal();
        Button dualChase = button("16.双向");
        dualChase.setOnClickListener(v -> { addLog("灯效: 双向追逐"); ledController.dualChase(); });
        row6.addView(dualChase, weight());
        Button pingPong = button("17.乒乓");
        pingPong.setOnClickListener(v -> { addLog("灯效: 乒乓"); ledController.pingPong(); });
        row6.addView(pingPong, weight());
        Button spiral = button("18.螺旋");
        spiral.setOnClickListener(v -> { addLog("灯效: 螺旋"); ledController.spiral(); });
        row6.addView(spiral, weight());
        mainLayout.addView(row6);
        
        // Row 7
        LinearLayout row7 = horizontal();
        Button randomBlink = button("19.随机");
        randomBlink.setOnClickListener(v -> { addLog("灯效: 随机闪烁"); ledController.randomBlink(); });
        row7.addView(randomBlink, weight());
        Button snake = button("20.🐍蛇");
        snake.setOnClickListener(v -> { addLog("灯效: 贪吃蛇"); ledController.snake(); });
        row7.addView(snake, weight());
        Button scanner = button("21.扫描");
        scanner.setOnClickListener(v -> { addLog("灯效: 扫描仪"); ledController.scanner(); });
        row7.addView(scanner, weight());
        mainLayout.addView(row7);
        
        // Row 8
        LinearLayout row8 = horizontal();
        Button comet = button("22.☄彗星");
        comet.setOnClickListener(v -> { addLog("灯效: 彗星"); ledController.comet(); });
        row8.addView(comet, weight());
        Button colorFade = button("23.渐变");
        colorFade.setOnClickListener(v -> { addLog("灯效: 颜色渐变"); ledController.colorFade(100, 0, 0, 0, 0, 100); });
        row8.addView(colorFade, weight());
        Button rainbowFade = button("24.彩虹");
        rainbowFade.setOnClickListener(v -> { addLog("灯效: 彩虹渐变"); ledController.rainbowFade(); });
        row8.addView(rainbowFade, weight());
        mainLayout.addView(row8);
        
        // Row 9
        LinearLayout row9 = horizontal();
        Button twinkle = button("25.✨闪烁");
        twinkle.setOnClickListener(v -> { addLog("灯效: 星光闪烁"); ledController.twinkle(80, 80, 80); });
        row9.addView(twinkle, weight());
        Button sparkle = button("26.💫闪耀");
        sparkle.setOnClickListener(v -> { addLog("灯效: 闪烁星"); ledController.sparkle(100, 100, 0); });
        row9.addView(sparkle, weight());
        Button pulse = button("27.脉冲");
        pulse.setOnClickListener(v -> { addLog("灯效: 脉冲"); ledController.pulse(60, 0, 60); });
        row9.addView(pulse, weight());
        mainLayout.addView(row9);
        
        // Row 10
        LinearLayout row10 = horizontal();
        Button halfAndHalf = button("28.分半");
        halfAndHalf.setOnClickListener(v -> { addLog("灯效: 分半"); ledController.halfAndHalf(100, 0, 0, 0, 0, 100); });
        row10.addView(halfAndHalf, weight());
        Button alternate = button("29.交替");
        alternate.setOnClickListener(v -> { addLog("灯效: 交替"); ledController.alternate(80, 80, 0); });
        row10.addView(alternate, weight());
        Button loading = button("30.⌛加载");
        loading.setOnClickListener(v -> { addLog("灯效: 加载"); ledController.loading(0, 100, 100); });
        row10.addView(loading, weight());
        mainLayout.addView(row10);
        
        // Stop button
        LinearLayout stopRow = horizontal();
        Button stopAnimation = button("⏹️ 停止所有灯效");
        stopAnimation.setOnClickListener(v -> {
            addLog("停止所有LED动画");
            ledController.stopAnimation();
            setStatus("已停止动画");
        });
        stopRow.addView(stopAnimation, weight());
        mainLayout.addView(stopRow);
        
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
        android.graphics.drawable.GradientDrawable logBg = new android.graphics.drawable.GradientDrawable();
        logBg.setColor(Color.rgb(245, 245, 245));
        logBg.setCornerRadius(dp(8));
        logBg.setStroke(dp(1), Color.rgb(200, 200, 200));
        logScrollView.setBackground(logBg);
        logScrollView.setElevation(dp(2));
        LinearLayout.LayoutParams logScrollLp = new LinearLayout.LayoutParams(-1, dp(300));
        logScrollLp.topMargin = dp(8);
        
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(Color.rgb(40, 40, 40));
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));
        logView.setTypeface(Typeface.MONOSPACE);
        logScrollView.addView(logView);
        mainLayout.addView(logScrollView, logScrollLp);
        
        addLog("=== 应用启动 ===");
        
        return scrollView;
    }
    
    private void addSection(LinearLayout parent, String title) {
        // Add divider line
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(200, 200, 200));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(20), 0, dp(10));
        parent.addView(divider, dividerParams);
        
        // Add section title with card background
        TextView section = text(title, 18, true);
        section.setPadding(dp(12), dp(10), dp(12), dp(10));
        section.setTextColor(Color.rgb(255, 255, 255));
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(Color.rgb(66, 133, 244));
        cardBg.setCornerRadius(dp(8));
        section.setBackground(cardBg);
        section.setElevation(dp(2));
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
        // Add rounded corners and shadow
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.rgb(66, 133, 244)); // Material Blue
        gd.setCornerRadius(dp(8));
        b.setBackground(gd);
        b.setTextColor(Color.WHITE);
        b.setElevation(dp(2));
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
