package com.example.p2p.base;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.example.p2p.utils.CommonUtils;

import butterknife.ButterKnife;

public abstract class BaseActivity extends AppCompatActivity {

    protected abstract int getLayoutId();
    protected abstract void initView();
    protected abstract void initCallback();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId());
        ButterKnife.bind(this);
        CommonUtils.darkMode(this, true);
        initView();
        initCallback();
    }
}
