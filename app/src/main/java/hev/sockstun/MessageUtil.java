package hev.sockstun;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class MessageUtil {
    private static final String EXTRA_KEY_ACTION = "action";
    private static final String EXTRA_KEY_DATA = "data";

    /**
     * 发送消息到UI
     */
    public static void sendMsg2UI(Context ctx, int action, String content) {
        if (ctx == null) return;

        try {
            Intent intent = new Intent(Constants.BROADCAST_ACTION_ACTIVITY);
            intent.setPackage(ctx.getPackageName());
            intent.putExtra(EXTRA_KEY_ACTION, action);
            intent.putExtra(EXTRA_KEY_DATA, content != null ? content : "");
            ctx.sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送消息到服务
     */
    public static void sendMsg2Service(Context ctx, int action, String content) {
        if (ctx == null) return;

        try {
            Intent intent = new Intent(Constants.BROADCAST_ACTION_SERVICE);
            intent.setPackage(ctx.getPackageName());
            intent.putExtra(EXTRA_KEY_ACTION, action);
            intent.putExtra(EXTRA_KEY_DATA, content != null ? content : "");
            ctx.sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取消息包含的Action
     */
    public static int getActionFromMsg(Bundle bundle) {
        if (bundle == null) return 0;

        return bundle.getInt(EXTRA_KEY_ACTION);
    }

    /**
     * 获取消息包含的数据
     */
    public static String getDataFromMsg(Bundle bundle) {
        if (bundle == null) return "";

        String result = bundle.getString(EXTRA_KEY_DATA);
        return result != null ? result : "";
    }
}