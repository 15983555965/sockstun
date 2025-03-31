/*
 ============================================================================
 Name        : AppListActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : App List Activity
 ============================================================================
 */

package hev.sockstun;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppListActivity extends AppCompatActivity implements AppAdapter.OnAppSelectedListener {
    
    private ListView appListView;
    private TextView noAppsTextView;
    private TextView appCountTextView;
    private EditText searchEditText;
    private List<AppInfo> appList = new ArrayList<>();
    private List<AppInfo> filteredAppList = new ArrayList<>();
    private AppAdapter adapter;
    private ArrayList<String> selectedPackages = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);
        
        // 设置Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // 初始化视图
        appListView = findViewById(R.id.appListView);
        noAppsTextView = findViewById(R.id.noAppsTextView);
        appCountTextView = findViewById(R.id.appCountTextView);
        searchEditText = findViewById(R.id.searchEditText);
        Button saveButton = findViewById(R.id.saveButton);
        Button selectAllButton = findViewById(R.id.selectAllButton);
        Button deselectAllButton = findViewById(R.id.deselectAllButton);
        ImageButton searchButton = findViewById(R.id.searchButton);
        
        // 获取已选择的应用包名
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("selected_packages")) {
            selectedPackages = intent.getStringArrayListExtra("selected_packages");
        }
        
        // 加载应用列表
        loadAppList();
        
        // 设置适配器
        filteredAppList.addAll(appList);
        adapter = new AppAdapter(this, filteredAppList);
        adapter.setOnAppSelectedListener(this);
        appListView.setAdapter(adapter);
        updateAppCount();
        
        // 搜索功能
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filterApps(s.toString());
            }
        });
        
        searchButton.setOnClickListener(v -> {
            filterApps(searchEditText.getText().toString());
        });
        
        // 全选按钮
        selectAllButton.setOnClickListener(v -> {
            for (AppInfo appInfo : filteredAppList) {
                appInfo.setSelected(true);
            }
            adapter.notifyDataSetChanged();
            updateAppCount();
        });
        
        // 取消全选按钮
        deselectAllButton.setOnClickListener(v -> {
            for (AppInfo appInfo : filteredAppList) {
                appInfo.setSelected(false);
            }
            adapter.notifyDataSetChanged();
            updateAppCount();
        });
        
        // 保存按钮点击事件
        saveButton.setOnClickListener(v -> {
            saveSelectedApps();
        });
    }
    
    private void filterApps(String query) {
        filteredAppList.clear();
        
        if (query.isEmpty()) {
            filteredAppList.addAll(appList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (AppInfo appInfo : appList) {
                if (appInfo.getAppName().toLowerCase().contains(lowerQuery) || 
                    appInfo.getPackageName().toLowerCase().contains(lowerQuery)) {
                    filteredAppList.add(appInfo);
                }
            }
        }
        
        adapter.notifyDataSetChanged();
        updateAppCount();
        
        // 显示或隐藏"无应用"提示
        if (filteredAppList.isEmpty()) {
            noAppsTextView.setVisibility(View.VISIBLE);
            appListView.setVisibility(View.GONE);
        } else {
            noAppsTextView.setVisibility(View.GONE);
            appListView.setVisibility(View.VISIBLE);
        }
    }
    
    private void updateAppCount() {
        // 计算已选择的应用数量
        int selectedCount = 0;
        for (AppInfo appInfo : filteredAppList) {
            if (appInfo.isSelected()) {
                selectedCount++;
            }
        }
        
        // 显示已选择/总应用数量
        appCountTextView.setText(getString(R.string.app_count_with_selected, selectedCount, filteredAppList.size()));
    }
    
    private void loadAppList() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        
        for (ApplicationInfo packageInfo : packages) {
            // 跳过系统应用
            if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }
            
            // 跳过当前应用自身
            if (packageInfo.packageName.equals(getPackageName())) {
                continue;
            }
            
            String appName = packageInfo.loadLabel(pm).toString();
            AppInfo appInfo = new AppInfo(
                    appName,
                    packageInfo.packageName,
                    packageInfo.loadIcon(pm)
            );
            
            // 设置选中状态
            if (selectedPackages.contains(packageInfo.packageName)) {
                appInfo.setSelected(true);
            }
            
            appList.add(appInfo);
        }
        
        // 按应用名称排序
        Collections.sort(appList, (app1, app2) -> app1.getAppName().compareToIgnoreCase(app2.getAppName()));
    }
    
    private void saveSelectedApps() {
        ArrayList<String> selectedApps = new ArrayList<>();
        
        // 从完整列表中获取所选应用，而不仅仅是筛选列表
        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected()) {
                selectedApps.add(appInfo.getPackageName());
            }
        }
        
        Intent resultIntent = new Intent();
        resultIntent.putStringArrayListExtra("selected_packages", selectedApps);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
    
    // 实现OnAppSelectedListener接口的方法
    @Override
    public void onAppSelectionChanged() {
        updateAppCount();
    }
}
