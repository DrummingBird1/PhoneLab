package com.sensolab.devicemonitor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Opened by the system (through the {@code ViewPermissionUsageActivity} alias) when the user
 * asks why PhoneLab wants a health permission — Android 16 gates the heart-rate sensor behind
 * {@code READ_HEART_RATE}. Android revokes health permissions from apps without this screen.
 * It hands straight off to the privacy policy and closes.
 */
public class PermissionsRationaleActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.about_no_browser, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
