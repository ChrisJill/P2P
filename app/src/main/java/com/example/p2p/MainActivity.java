package com.example.p2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.loading.Loading;
import com.example.loading.StatusView;
import com.example.p2p.adapter.RvUsersAdapter;
import com.example.p2p.base.activity.BaseActivity;
import com.example.p2p.bean.Image;
import com.example.p2p.bean.ItemType;
import com.example.p2p.bean.Mes;
import com.example.p2p.bean.MesType;
import com.example.p2p.bean.User;
import com.example.p2p.callback.IUserCallback;
import com.example.p2p.callback.IConnectCallback;
import com.example.p2p.callback.IDialogCallback;
import com.example.p2p.config.Constant;
import com.example.p2p.core.OnlineUserManager;
import com.example.p2p.core.ConnectManager;
import com.example.p2p.utils.LogUtils;
import com.example.p2p.utils.WifiUtils;
import com.example.p2p.widget.WindowPopup;
import com.example.p2p.widget.dialog.ConnectingDialog;
import com.example.p2p.widget.dialog.GotoWifiSettingsDialog;
import com.example.utils.FileUtil;
import com.example.utils.ToastUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;

public class MainActivity extends BaseActivity {

    @BindView(R.id.rv_user)
    RecyclerView rvMain;
    @BindView(R.id.tv_title)
    TextView tvTitle;
    @BindView(R.id.tool_bar)
    Toolbar toolBar;
    @BindView(R.id.iv_back)
    ImageView ivBack;
    @BindView(R.id.iv_more)
    ImageView ivMore;

    private String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_SOCKET_STATE = 0x000;
    private static final int REQUEST_WIFI_ENABLE = 0x001;

    private RvUsersAdapter mRvMainAdapter;
    private StatusView mStatusView;
    private GotoWifiSettingsDialog mGotoWifiSettingsDialog;
    private ConnectingDialog mConnectingDialog;
    private List<User> mOnlineUsers;
    private WifiConnectionReceiver mNetWorkConnectionReceiver;
    private WindowPopup mWindowPopup;
    private int mPosition;
    private long mLastPressTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //初始化用户系统
        OnlineUserManager.getInstance().initListener();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mNetWorkConnectionReceiver = new WifiConnectionReceiver();
        IntentFilter intentFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mNetWorkConnectionReceiver, intentFilter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mNetWorkConnectionReceiver);
    }

    @Override
    protected void onDestroy() {
        //退出时，销毁这边的连接
        OnlineUserManager.getInstance().exit();
        ConnectManager.getInstance().destory();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if(System.currentTimeMillis() - mLastPressTime < 2000){
            super.onBackPressed();
        }else {
            mLastPressTime = System.currentTimeMillis();
            ToastUtil.showToast(this, getString(R.string.main_exit));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == REQUEST_WIFI_ENABLE){
            if(WifiUtils.isWifiConnected(MainActivity.this)){
                refreshOnlineUsers();
            }else {
                //等待一下，如果用户返回过快，wifi可能正在连接中，但还连接上
                new Handler().postDelayed(() -> {
                    if(WifiUtils.isWifiConnected(MainActivity.this)){
                        refreshOnlineUsers();
                    }else {
                        if(mOnlineUsers.isEmpty())
                            mStatusView.showEmpty();
                    }
                }, 3000);
            }
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void initView() {
        setSupportActionBar(toolBar);
        ivBack.setVisibility(View.GONE);
        tvTitle.setText(getString(R.string.main_tlTitle));
        mStatusView = Loading.beginBuildStatusView(this)
                .warp(findViewById(R.id.rv_user))
                .addLoadingView(R.layout.loading_view)
                .addEmptyView(R.layout.empty_view)
                .create();
        mOnlineUsers = new ArrayList<>();
        rvMain.setLayoutManager(new LinearLayoutManager(MainActivity.this));
        mRvMainAdapter = new RvUsersAdapter(mOnlineUsers, R.layout.item_user);
        rvMain.setAdapter(mRvMainAdapter);
        mGotoWifiSettingsDialog = new GotoWifiSettingsDialog();
        mConnectingDialog = new ConnectingDialog();
        mWindowPopup = new WindowPopup(this, mStatusView);
        refreshOnlineUsers();
    }

    @Override
    protected void initCallback() {
        //item监听
        mRvMainAdapter.setOnItemClickListener((adapter, view, position) -> {
            if(mOnlineUsers.isEmpty()) return;
            mPosition = position;
            mConnectingDialog.show(getSupportFragmentManager());
            //连接回调监听
            ConnectManager.getInstance().connect(mOnlineUsers.get(position).getIp(), new IConnectCallback() {
                @Override
                public void onConnectSuccess(String targetIp) {
                    mConnectingDialog.dismiss();
                    ToastUtil.showToast(MainActivity.this, getString(R.string.main_connecting_success));
                    ChatActivity.startActiivty(MainActivity.this, mOnlineUsers.get(mPosition), REQUEST_SOCKET_STATE);
                }

                @Override
                public void onConnectFail(String targetIp) {
                    mConnectingDialog.dismiss();
                    ToastUtil.showToast(MainActivity.this, getString(R.string.main_connecting_fail));
                }
            });
        });
        mGotoWifiSettingsDialog.setDialogCallback(new IDialogCallback() {
            @Override
            public void onAgree() {
                WifiUtils.gotoWifiSettings(MainActivity.this, REQUEST_WIFI_ENABLE);
            }

            @Override
            public void onDismiss() {
                ToastUtil.showToast(MainActivity.this, getString(R.string.toast_wifi_noconnect));
                if(mOnlineUsers.isEmpty()){
                    mStatusView.showEmpty();
                    return;
                }
                mStatusView.showSuccess();
            }
        });
        //广播回调监听
        OnlineUserManager.getInstance().setUserCallback(new IUserCallback() {
            @Override
            public void onOnlineUsers(List<User> users) {
                mOnlineUsers.clear();
                if(users.isEmpty()){
                    mStatusView.showEmpty();
                }else {
                    mOnlineUsers.addAll(users);
                    mRvMainAdapter.notifyDataSetChanged();
                    for(User user : users){
                        final String userIp = user.getIp();
                        final String name = user.getName();
                        ConnectManager.getInstance().addImageReceiveCallback(userIp, imagePath -> {
                            for(int i = 0; i < mOnlineUsers.size(); i++){
                                if(mOnlineUsers.get(i).getIp().equals(userIp)){
                                    mOnlineUsers.get(i).setImagePath(imagePath);
                                    mRvMainAdapter.notifyItemChanged(i);
                                    LogUtils.d(TAG, "接收到用户图片，name = " + name + ", path = " + imagePath);
                                    break;
                                }
                            }
                        });
                    }
                    for(User user : users){
                        final User sendUser = user;
                        ConnectManager.getInstance().connect(sendUser.getIp(), new IConnectCallback() {
                            @Override
                            public void onConnectSuccess(String targetIp) {
                                Image image = new Image(Constant.FILE_USER_IMAGE);
                                Mes<Image> message = new Mes<>(ItemType.OTHER, MesType.IMAGE, sendUser.getIp(), image);
                                ConnectManager.getInstance().sendMessage(sendUser.getIp(), message);
                                LogUtils.d(TAG, "发送用户图片，name = " + sendUser.getName());
                            }

                            @Override
                            public void onConnectFail(String targetIp) {
                                LogUtils.e(TAG, "一个发送失败，user = " + sendUser);
                            }
                        });
                    }

                    mStatusView.showSuccess();
                }
            }

            @Override
            public void onJoin(User user){
                if(mOnlineUsers.isEmpty()) mStatusView.showSuccess();
                mOnlineUsers.add(user);
                mRvMainAdapter.notifyItemInserted(mOnlineUsers.size());
                mStatusView.showSuccess();
                final String userIp = user.getIp();
                final String name = user.getName();
                ConnectManager.getInstance().addImageReceiveCallback(userIp, imagePath -> {
                    for(int i = 0; i < mOnlineUsers.size(); i++){
                        if(mOnlineUsers.get(i).getIp().equals(userIp)){
                            mOnlineUsers.get(i).setImagePath(imagePath);
                            mRvMainAdapter.notifyItemChanged(i);
                            LogUtils.d(TAG, "接收到用户图片，name = " + name + ", path = " + imagePath);
                            break;
                        }
                    }
                });
                new Handler().postDelayed(() -> ConnectManager.getInstance().connect(user.getIp(), new IConnectCallback() {
                    @Override
                    public void onConnectSuccess(String targetIp) {
                        Image image = new Image(Constant.FILE_USER_IMAGE);
                        Mes<Image> message = new Mes<>(ItemType.OTHER, MesType.IMAGE, userIp, image);
                        ConnectManager.getInstance().sendMessage(userIp, message);
                        LogUtils.d(TAG, "发送用户图片，name = " + name);
                    }

                    @Override
                    public void onConnectFail(String targetIp) {
                        LogUtils.e(TAG, "一个发送失败，user = " + name);
                    }
                }), Constant.WAITING_TIME + 1000);
                ToastUtil.showToast(MainActivity.this, user.getName() + "加入聊天");
            }

            @Override
            public void onExit(User user) {
                int index = mOnlineUsers.indexOf(user);
                mOnlineUsers.remove(user);
                mRvMainAdapter.notifyItemRemoved(index);
                FileUtil.deleteDir(new File(Constant.FILE_PATH_ONLINE_USER + user.getIp() + File.separator));
                ConnectManager.getInstance().removeConnect(user.getIp());
                if(mOnlineUsers.isEmpty()) mStatusView.showEmpty();
                ToastUtil.showToast(MainActivity.this,  user.getName() + "退出聊天");
            }
        });
    }


    @OnClick({R.id.iv_more})
    public void onViewClick(View view) {
        switch (view.getId()) {
            case R.id.iv_more:
                mWindowPopup.showAsDropDown(ivMore);
                break;
            default:
                break;
        }
    }

    /**
     * 刷新在线用户
     */
    private void refreshOnlineUsers() {
        mStatusView.showLoading();
        User user = (User) FileUtil.restoreObject(this, Constant.FILE_NAME_USER);
        user.setImagePath(null);
        OnlineUserManager.getInstance().login(user);
        OnlineUserManager.getInstance().getOnlineUsers();
    }

    /**
     * 监听Wifi网络变化广播
     */
    public class WifiConnectionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)){
                NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                if(networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI){
                    NetworkInfo.State state = networkInfo.getState();
                    switch (state){
                        case CONNECTED:
                            LogUtils.d(TAG, "wifi已经连接");
                            if(mGotoWifiSettingsDialog.isAdded()) mGotoWifiSettingsDialog.dismiss();
                            break;
                        case DISCONNECTED:
                            LogUtils.d(TAG, "wifi已经断开");
                            break;
                        default:
                            LogUtils.d(TAG, "wifi已其他状态 = " + state);
                            break;
                    }
                }
            }
            if(intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)){
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                switch (state){
                    case WifiManager.WIFI_STATE_ENABLED:
                        LogUtils.d(TAG, "wifi已经打开");
                        break;
                    case WifiManager.WIFI_STATE_DISABLED:
                        LogUtils.d(TAG, "wifi已经关闭");
                        if(mConnectingDialog.isAdded()) mConnectingDialog.dismiss();
                        mGotoWifiSettingsDialog.show(getSupportFragmentManager());
                        break;
                    case WifiManager.WIFI_STATE_DISABLING:
                        LogUtils.d(TAG, "wifi关闭中...");
                        break;
                    case WifiManager.WIFI_STATE_ENABLING:
                        LogUtils.d(TAG, "wifi打开中...");
                        break;
                    default:
                        LogUtils.d(TAG, "wifi的其他状态 = " + state);
                        break;
                }
            }
        }
    }

    public static void startActivity(Context context){
        context.startActivity(new Intent(context, MainActivity.class));
    }
}
