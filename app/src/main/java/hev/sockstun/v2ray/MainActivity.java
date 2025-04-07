package hev.sockstun.v2ray;

import android.Android;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import hev.sockstun.AppListActivity;
import hev.sockstun.R;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_VPN_PERMISSION = 1; // 请求码

    private TextView statusTextView;
    private TextView modeTextView;
    private MaterialButton toggleButton;
    private MaterialButton appSelectButton; // 应用选择按钮
    private boolean isGlobalMode = true; // 默认全局模式
    private ConnectivityManager connectivityManager;
    private Set<String> selectedPackages = new HashSet<>();
    private TabLayout tabLayout;
    private View settingsCardView;

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getExtras() != null) {
                Bundle bundle = intent.getExtras();
                int action = MessageUtil.getActionFromMsg(bundle);

                if (action == Constants.MSG_STATE_START_SUCCESS) {
                    updateUI(true);
                    Utils.toast(context, getString(R.string.vpn_start_success));
                } else if (action == Constants.MSG_STATE_START_FAILURE) {
                    updateUI(false);
                    Utils.toast(context, getString(R.string.vpn_start_failed));
                } else if (action == Constants.MSG_STATE_STOP_SUCCESS) {
                    updateUI(false);
                    Utils.toast(context, getString(R.string.vpn_stopped));
                }
            }
        }
    };

    private final BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                boolean isVpnActive = isVpnActive();
                updateUI(isVpnActive);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 设置Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        statusTextView = findViewById(R.id.statusTextView);
        modeTextView = findViewById(R.id.modeTextView);
        toggleButton = findViewById(R.id.toggleButton);
        appSelectButton = findViewById(R.id.appSelectButton);
        tabLayout = findViewById(R.id.tabLayout);
        settingsCardView = findViewById(R.id.settingsCardView);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        // 加载保存的配置
        loadPreferences();
        
        toggleButton.setOnClickListener(v -> {
            if (isVpnActive()) {
                stopVpnService();
            } else {
                prepareStartVpn();
            }
        });
        
        // 设置Tab选择监听
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    // 全局代理模式
                    isGlobalMode = true;
                } else {
                    // 局部代理模式
                    isGlobalMode = false;
                }
                updateModeUI();
                savePreferences();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // 不需要处理
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 不需要处理
            }
        });
        
        // 应用选择按钮
        appSelectButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppListActivity.class);
            intent.putExtra("selected_packages", new ArrayList<>(selectedPackages));
            startActivityForResult(intent, 2);
        });

        // 注册广播接收器
        IntentFilter filter = new IntentFilter(Constants.BROADCAST_ACTION_ACTIVITY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(messageReceiver, filter,Context.RECEIVER_NOT_EXPORTED);
        }else{
            registerReceiver(messageReceiver, filter);
        }

        // 注册VPN状态监听
        IntentFilter vpnFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(vpnStateReceiver, vpnFilter);

        // 检查VPN状态
        checkVpnStatus();
        
        // 初始化界面
        initUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(messageReceiver);
            unregisterReceiver(vpnStateReceiver);
        } catch (Exception e) {
            Log.e(TAG, "注销广播接收器失败: " + e.getMessage());
        }
    }

    private void prepareStartVpn() {
        Toast.makeText(this,"status:"+Android.status(),Toast.LENGTH_SHORT).show();
        try {
            if (!Android.status()) {
                Android.start("ODHLMGQ5MM");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, REQUEST_VPN_PERMISSION);
        } else {
            startVpnService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN_PERMISSION) {
            if (resultCode == RESULT_OK) {
                startVpnService();
            } else {
                Utils.toast(this, getString(R.string.vpn_permission_required));
            }
        } else if (requestCode == 2 && resultCode == RESULT_OK && data != null) {
            // 获取应用选择结果
            ArrayList<String> selected = data.getStringArrayListExtra("selected_packages");
            if (selected != null) {
                selectedPackages.clear();
                selectedPackages.addAll(selected);
                savePreferences();
                // 更新UI显示
                updateSelectedAppsInfo();
            }
        }
    }

    private void startVpnService() {
        Intent intent = new Intent(this, Socks5VpnService.class);
        intent.putExtra("is_global_mode", isGlobalMode);
        if (!isGlobalMode) {
            intent.putExtra("selected_packages", new ArrayList<>(selectedPackages));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopVpnService() {
        MessageUtil.sendMsg2Service(this, Constants.MSG_STATE_STOP, "");
    }

    private void initUI() {
        // 设置初始Tab状态
        tabLayout.getTabAt(isGlobalMode ? 0 : 1).select();
        
        // 更新UI显示
        updateModeUI();
        
        // 是否启用Tab根据VPN状态
        updateTabsEnabled(!isVpnActive());
    }

    private void updateTabsEnabled(boolean enabled) {
        // 只有在VPN停止时才能切换Tab
        tabLayout.setEnabled(enabled);
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) {
                View view = tab.view;
                if (view != null) {
                    view.setEnabled(enabled);
                }
            }
        }
    }

    private void updateUI(boolean isRunning) {
        statusTextView.setText(isRunning ? getString(R.string.running) : getString(R.string.stopped));
        statusTextView.setTextColor(
                getResources().getColor(
                        isRunning ? android.R.color.holo_green_dark : android.R.color.holo_red_dark
                )
        );
        
        // 更新按钮状态和文本
        if (isRunning) {
            toggleButton.setText(R.string.stop_vpn);
            toggleButton.setIcon(getDrawable(android.R.drawable.ic_media_pause));
            toggleButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorError)));
        } else {
            toggleButton.setText(R.string.start_vpn);
            toggleButton.setIcon(getDrawable(android.R.drawable.ic_media_play));
            toggleButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
        }
        
        appSelectButton.setEnabled(!isRunning); // VPN运行时禁用应用选择按钮
        
        // 运行时禁用Tab切换
        updateTabsEnabled(!isRunning);
    }

    private void checkVpnStatus() {
        if (isVpnActive()) {
            updateUI(true);
        } else {
            updateUI(false);
        }
    }

    private boolean isVpnActive() {
        if (connectivityManager == null) return false;
        
        Network[] networks = connectivityManager.getAllNetworks();
        for (Network network : networks) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    private void updateModeUI() {
        modeTextView.setText(isGlobalMode ? getString(R.string.global_proxy) : getString(R.string.per_app_proxy));
        
        // 在全局模式下隐藏整个设置卡片
        settingsCardView.setVisibility(isGlobalMode ? View.GONE : View.VISIBLE);
        
        // 在局部代理模式下显示应用选择按钮
        appSelectButton.setVisibility(isGlobalMode ? View.GONE : View.VISIBLE);
        
        updateSelectedAppsInfo();
    }
    
    private void updateSelectedAppsInfo() {
        TextView selectedAppsInfo = findViewById(R.id.selectedAppsInfo);
        if (selectedAppsInfo != null) {
            if (!isGlobalMode) {
                selectedAppsInfo.setVisibility(View.VISIBLE);
                selectedAppsInfo.setText(getString(R.string.selected_apps_count, selectedPackages.size()));
            } else {
                selectedAppsInfo.setVisibility(View.GONE);
            }
        }
    }
    
    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE);
        prefs.edit()
             .putBoolean("global_mode", isGlobalMode)
             .putStringSet("selected_apps", selectedPackages)
             .apply();
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE);
        isGlobalMode = prefs.getBoolean("global_mode", true);
        selectedPackages = prefs.getStringSet("selected_apps", new HashSet<>());
    }
}