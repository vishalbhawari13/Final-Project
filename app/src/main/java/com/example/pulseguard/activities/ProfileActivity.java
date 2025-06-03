/*
package com.example.pulseguard.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pulseguard.R;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName, tvEmail;
    private Button btnLogout;

    // Sample static user data (replace with real session/user data later)
    private static final String USER_NAME = "Vishal Bhawari";
    private static final String USER_EMAIL = "vishal@example.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize views
        tvName = findViewById(R.id.tv_name);
        tvEmail = findViewById(R.id.tv_email);
        btnLogout = findViewById(R.id.btn_logout);

        // Set user info
        tvName.setText(USER_NAME);
        tvEmail.setText(USER_EMAIL);

        // Logout button click listener using lambda
        btnLogout.setOnClickListener(v -> {
            // Clear back stack and start LoginActivity
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
*/
