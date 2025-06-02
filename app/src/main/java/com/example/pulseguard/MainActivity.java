package com.example.pulseguard;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pulseguard.activities.IntroActivity;

public class MainActivity extends AppCompatActivity {

    // Duration of splash screen in milliseconds (optional)
    private static final int SPLASH_DELAY = 500; // 0.5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Optional: set a simple splash layout
        // setContentView(R.layout.activity_main);

        // Delay starting IntroActivity to show splash (optional)
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Start IntroActivity
                Intent intent = new Intent(MainActivity.this, IntroActivity.class);
                startActivity(intent);
                finish(); // Prevent back to MainActivity
            }
        }, SPLASH_DELAY);
    }
}
