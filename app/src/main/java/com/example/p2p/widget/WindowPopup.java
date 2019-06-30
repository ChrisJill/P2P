package com.example.p2p.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.example.loading.StatusView;
import com.example.p2p.R;
import com.example.p2p.bean.User;
import com.example.p2p.config.Constant;
import com.example.p2p.core.OnlineUserManager;
import com.example.utils.FileUtil;


/**
 * Created by 陈健宇 at 2019/6/29
 */
public class WindowPopup extends PopupWindow {

    TextView mTvRefresh;

    private StatusView mStatusView;

    public WindowPopup(Context context, StatusView statusView) {
        super(context);
        this.mStatusView = statusView;
        View view = LayoutInflater.from(context).inflate(R.layout.popup_widow, null);
        mTvRefresh = view.findViewById(R.id.tv_refresh);
        this.setContentView(view);
        this.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        this.setTouchable(true);
        this.setFocusable(true);
        mTvRefresh.setOnClickListener(v -> {
            this.dismiss();
            if(OnlineUserManager.getInstance().isRefresh()) return;
            mStatusView.showLoading();
            OnlineUserManager.getInstance().login((User) FileUtil.restoreObject(context, Constant.FILE_NAME_USER));
            OnlineUserManager.getInstance().getOnlineUsers();
        });
    }

}
