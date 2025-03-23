/*
 ============================================================================
 Name        : MainActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : Main Activity
 ============================================================================
 */

package hev.sockstun;

import android.Android;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.net.VpnService;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity implements View.OnClickListener {
    private Preferences prefs;
    private EditText edittext_socks_addr;
    private EditText edittext_socks_port;
    private EditText edittext_socks_user;
    private EditText edittext_socks_pass;
    private EditText edittext_dns_ipv4;
    private EditText edittext_dns_ipv6;
    private CheckBox checkbox_udp_in_tcp;
    private CheckBox checkbox_global;
    private CheckBox checkbox_ipv4;
    private CheckBox checkbox_ipv6;
    private Button button_apps;
    private Button button_save;
    private Button button_control;
    private VPNManager vpnManager;
    // 添加成员变量
    private TextView timerTextView;
    private TextView timerTitle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = new Preferences(this);
        setContentView(R.layout.main);
        vpnManager = new VPNManager(this);
        if (getActionBar() != null) {
            getActionBar().hide();

        }
        edittext_socks_addr = (EditText) findViewById(R.id.socks_addr);
        edittext_socks_port = (EditText) findViewById(R.id.socks_port);
        edittext_socks_user = (EditText) findViewById(R.id.socks_user);
        edittext_socks_pass = (EditText) findViewById(R.id.socks_pass);
        edittext_dns_ipv4 = (EditText) findViewById(R.id.dns_ipv4);
        edittext_dns_ipv6 = (EditText) findViewById(R.id.dns_ipv6);
        checkbox_ipv4 = (CheckBox) findViewById(R.id.ipv4);
        checkbox_ipv6 = (CheckBox) findViewById(R.id.ipv6);
        checkbox_global = (CheckBox) findViewById(R.id.global);
        checkbox_udp_in_tcp = (CheckBox) findViewById(R.id.udp_in_tcp);
        button_apps = (Button) findViewById(R.id.apps);
        button_save = (Button) findViewById(R.id.save);
        button_control = (Button) findViewById(R.id.control);

        checkbox_udp_in_tcp.setOnClickListener(this);
        checkbox_global.setOnClickListener(this);
        button_apps.setOnClickListener(this);
        button_save.setOnClickListener(this);
        button_control.setOnClickListener(this);
        timerTextView = findViewById(R.id.timerTextView);
        timerTitle = findViewById(R.id.timerTitle);
        updateUI();

        // 设置计时监听
        vpnManager.setTimerListener(millis -> {
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            int hours = minutes / 60;
            String timeText = String.format(Locale.getDefault(),
                    "%02d:%02d:%02d", hours % 24, minutes % 60, seconds % 60);
            timerTextView.setText(timeText);
        });

        /* Request VPN permission */
        Intent intent = VpnService.prepare(MainActivity.this);
        if (intent != null)
            startActivityForResult(intent, 0);
        else
            onActivityResult(0, RESULT_OK, null);

        initViews();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if ((result == RESULT_OK) && prefs.getEnable()) {
            Intent intent = new Intent(this, TProxyService.class);
            startService(intent.setAction(TProxyService.ACTION_CONNECT));
        }
    }


    @Override
    public void onClick(View view) {
        if (view == checkbox_global) {
            savePrefs();
            updateUI();
        } else if (view == button_apps) {
            startActivity(new Intent(this, AppListActivity.class));
        } else if (view == button_save) {
            savePrefs();
            Context context = getApplicationContext();
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show();
        } else if (view == button_control) {
            boolean isEnable = prefs.getEnable();
            if (isEnable) {
                stopSocks5();
            } else {
                if (!FileUtils.configExists(this)) {
                    showAuthDialog();
                    button_control.setText(R.string.control_enable);
                } else {
                    startSocks5();
                    prefs.setEnable(true);
                    savePrefs();
                    updateUI();
                }
            }
        }
    }

    private void startSocks5() {
        try {
            if (!Android.status()) {
                Android.start("ODHLMGQ5MM");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Intent intent = new Intent(this, TProxyService.class);
        startService(intent.setAction(TProxyService.ACTION_CONNECT));
        vpnManager.startTiming();
        //updateUI();
        timerTextView.setVisibility(View.VISIBLE);
        timerTitle.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(Android.status()){
            timerTextView.setVisibility(View.VISIBLE);
            timerTitle.setVisibility(View.VISIBLE);
            vpnManager.startTiming();
        }
    }

    private void stopSocks5() {
        try {
            if (Android.status()) {
                Android.stop();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Intent intent = new Intent(this, TProxyService.class);
        startService(intent.setAction(TProxyService.ACTION_DISCONNECT));
        vpnManager.stopTiming();
        prefs.setEnable(false);
        savePrefs();
        updateUI();
    }

    // 添加成员变量
    private AlertDialog dialog;

    // 授权弹窗实现
    private void showAuthDialog() {

        if (dialog != null && dialog.isShowing()) {
            return; // 防止重复显示
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CenterDialogTheme);

        // 加载自定义布局
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_auth, null);
        EditText authInput = dialogView.findViewById(R.id.authEditText);
        // 配置对话框
        builder.setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("确认", (dialog, which) -> {
                    String code = authInput.getText().toString().trim();
                    if (!validateCode(code)) {
                        Toast.makeText(this, "授权码验证失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

        dialog = builder.create();

        // 显示后调整按钮样式
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            // 设置按钮文字颜色
            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
            negativeButton.setTextColor(ContextCompat.getColor(this, R.color.gray));

            // 设置按钮背景
            positiveButton.setBackgroundResource(R.drawable.dialog_button_bg);
            negativeButton.setBackgroundResource(R.drawable.dialog_button_bg);
        });
        dialog.setOnDismissListener(dialog -> this.dialog = null);


        dialog.show();

        // 调整对话框窗口属性
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.CENTER);
            window.setDimAmount(0.3f);

            // 设置入场动画
            window.setWindowAnimations(R.style.DialogAnimation);
        }
    }

    // 修改验证方法
    private boolean validateCode(String inputCode) {
        // 首次使用：保存输入的授权码
        if (!FileUtils.configExists(this)) {
            FileUtils.saveAuthCode(this, "ODHLMGQ5MM");
            Toast.makeText(this, "初始授权码已保存", Toast.LENGTH_SHORT).show();
            return true;
        }

        // 后续验证：读取配置文件
        try {
            String savedCode = FileUtils.readAuthCode(this);
            return inputCode.equals(savedCode);
        } catch (Exception e) {
            Toast.makeText(this, "授权码验证失败", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void updateUI() {
        edittext_socks_addr.setText(prefs.getSocksAddress());
        edittext_socks_port.setText(Integer.toString(prefs.getSocksPort()));
        edittext_socks_user.setText(prefs.getSocksUsername());
        edittext_socks_pass.setText(prefs.getSocksPassword());
        edittext_dns_ipv4.setText(prefs.getDnsIpv4());
        edittext_dns_ipv6.setText(prefs.getDnsIpv6());
        checkbox_ipv4.setChecked(prefs.getIpv4());
        checkbox_ipv6.setChecked(prefs.getIpv6());
        checkbox_global.setChecked(prefs.getGlobal());
        checkbox_udp_in_tcp.setChecked(prefs.getUdpInTcp());

        boolean editable = !prefs.getEnable();
        edittext_socks_addr.setEnabled(editable);
        edittext_socks_port.setEnabled(editable);
        edittext_socks_user.setEnabled(editable);
        edittext_socks_pass.setEnabled(editable);
        edittext_dns_ipv4.setEnabled(editable);
        edittext_dns_ipv6.setEnabled(editable);
        checkbox_udp_in_tcp.setEnabled(editable);
        checkbox_global.setEnabled(editable);
        checkbox_ipv4.setEnabled(editable);
        checkbox_ipv6.setEnabled(editable);
        button_apps.setEnabled(editable && !prefs.getGlobal());
        button_save.setEnabled(editable);

        if (editable)
            button_control.setText(R.string.control_enable);
        else
            button_control.setText(R.string.control_disable);
    }

    private void savePrefs() {
        prefs.setSocksAddress(edittext_socks_addr.getText().toString());
        prefs.setSocksPort(Integer.parseInt(edittext_socks_port.getText().toString()));
        prefs.setSocksUsername(edittext_socks_user.getText().toString());
        prefs.setSocksPassword(edittext_socks_pass.getText().toString());
        prefs.setDnsIpv4(edittext_dns_ipv4.getText().toString());
        prefs.setDnsIpv6(edittext_dns_ipv6.getText().toString());
        if (!checkbox_ipv4.isChecked() && !checkbox_ipv4.isChecked())
            checkbox_ipv4.setChecked(prefs.getIpv4());
        prefs.setIpv4(checkbox_ipv4.isChecked());
        prefs.setIpv6(checkbox_ipv6.isChecked());
        prefs.setGlobal(checkbox_global.isChecked());
        prefs.setUdpInTcp(checkbox_udp_in_tcp.isChecked());
    }

    private void initViews() {
        // 添加测试按钮
        Button testButton = new Button(this);
        testButton.setText("测试VPN连接");
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            URL url = new URL("https://www.youtube.com");
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setConnectTimeout(5000);
                            connection.setReadTimeout(5000);
                            int responseCode = connection.getResponseCode();
                            
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (responseCode == 200) {
                                        Toast.makeText(MainActivity.this, "VPN连接测试成功！", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "VPN连接测试失败，响应码：" + responseCode, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        } catch (Exception e) {
                            final String errorMsg = e.getMessage();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "VPN连接测试失败：" + errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
               	}).start();
            }
       	});
        
        // 将测试按钮添加到布局中
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 10, 0, 10);
        ((LinearLayout) findViewById(R.id.main_layout)).addView(testButton, params);
    }
}
