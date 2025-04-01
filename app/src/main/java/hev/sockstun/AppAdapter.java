package hev.sockstun;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.List;

public class AppAdapter extends BaseAdapter {
    private Context context;
    private List<AppInfo> appList;
    private LayoutInflater inflater;
    private OnAppSelectedListener listener;

    public interface OnAppSelectedListener {
        void onAppSelectionChanged();
    }

    public AppAdapter(Context context, List<AppInfo> appList) {
        this.context = context;
        this.appList = appList;
        this.inflater = LayoutInflater.from(context);
    }

    public void setOnAppSelectedListener(OnAppSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return appList.size();
    }

    @Override
    public Object getItem(int position) {
        return appList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.app_list_item, parent, false);
            holder = new ViewHolder();
            holder.appIcon = convertView.findViewById(R.id.appIcon);
            holder.appName = convertView.findViewById(R.id.appName);
            holder.packageName = convertView.findViewById(R.id.packageName);
            holder.checkBox = convertView.findViewById(R.id.checkBox);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        AppInfo appInfo = appList.get(position);
        holder.appIcon.setImageDrawable(appInfo.getIcon());
        holder.appName.setText(appInfo.getAppName());
        holder.packageName.setText(appInfo.getPackageName());
        holder.checkBox.setChecked(appInfo.isSelected());
        
        // 点击事件处理
        convertView.setOnClickListener(v -> {
            boolean newState = !appInfo.isSelected();
            appInfo.setSelected(newState);
            holder.checkBox.setChecked(newState);
            
            // 通知监听器选择状态已更改
            if (listener != null) {
                listener.onAppSelectionChanged();
            }
        });
        
        // 复选框点击事件
        holder.checkBox.setOnClickListener(v -> {
            boolean isChecked = holder.checkBox.isChecked();
            appInfo.setSelected(isChecked);
            
            // 通知监听器选择状态已更改
            if (listener != null) {
                listener.onAppSelectionChanged();
            }
        });
        
        return convertView;
    }
    
    private static class ViewHolder {
        ImageView appIcon;
        TextView appName;
        TextView packageName;
        MaterialCheckBox checkBox;
    }
} 