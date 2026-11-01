package com.smartexpense.android.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.smartexpense.android.R;
import com.smartexpense.android.ui.auth.LoginActivity;
import com.smartexpense.android.ui.main.MainActivity;
import com.smartexpense.android.util.TokenManager;

/**
 * Splash screen — shows smooth, short brand splash with the official logo,
 * then seamlessly navigates to Login or Main based on auth state.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TokenManager.init(getApplicationContext());

        // Smooth subtle entrance animation
        View brandContainer = findViewById(R.id.ll_brand_container);
        if (brandContainer != null) {
            brandContainer.setAlpha(0f);
            brandContainer.setScaleX(0.92f);
            brandContainer.setScaleY(0.92f);
            brandContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(600)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }

        // Keep it snappy: 1100ms total splash
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateNext, 1100);
    }

    private void navigateNext() {
        Intent intent;
        if (TokenManager.isLoggedIn()) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
