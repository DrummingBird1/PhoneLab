package com.sensolab.devicemonitor;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import com.google.android.material.snackbar.Snackbar;
import com.sensolab.devicemonitor.databinding.FragmentAboutBinding;

public class AboutFragment extends Fragment {

    private FragmentAboutBinding binding;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAboutBinding.inflate(inflater, container, false);

        binding.tvVersion.setText(getString(R.string.app_version_line,
                BuildConfig.VERSION_NAME, requireActivity().getPackageName()));

        binding.btnRate.setOnClickListener(v -> openPlayStore());
        binding.btnShare.setOnClickListener(v -> shareApp());
        binding.btnEmail.setOnClickListener(v -> sendEmail());

        // Y3: disable Donate when no PayPal id is configured (instead of leading user to a dead URL)
        if (BuildConfig.PAYPAL_BUSINESS_ID.isEmpty()) {
            binding.btnDonate.setEnabled(false);
            binding.btnDonate.setAlpha(0.45f);
        } else {
            binding.btnDonate.setOnClickListener(v -> openDonate());
        }

        // Y6: show Privacy Policy button only when URL is configured
        if (!BuildConfig.PRIVACY_POLICY_URL.isEmpty()) {
            binding.btnPrivacy.setVisibility(View.VISIBLE);
            binding.btnPrivacy.setOnClickListener(v -> openPrivacy());
        }

        return binding.getRoot();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void openPrivacy() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)));
        } catch (Exception e) {
            toast(R.string.about_no_browser);
        }
    }

    private void openPlayStore() {
        String pkg = requireActivity().getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + pkg)));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        }
    }

    private void openDonate() {
        // Y3: only reached when BuildConfig.PAYPAL_BUSINESS_ID is non-empty
        String url = "https://www.paypal.com/donate/?business=" + BuildConfig.PAYPAL_BUSINESS_ID;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast(R.string.about_no_browser);
        }
    }

    private void shareApp() {
        String pkg = requireActivity().getPackageName();
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.about_share_subject));
        i.putExtra(Intent.EXTRA_TEXT,    getString(R.string.about_share_body, pkg));
        startActivity(Intent.createChooser(i, getString(R.string.about_share_chooser)));
    }

    private void sendEmail() {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@sensolab.app"));
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.about_email_subject));
        try { startActivity(Intent.createChooser(i, getString(R.string.about_email_chooser))); }
        catch (ActivityNotFoundException e) {
            toast(R.string.about_no_email_app);
        }
    }

    /** N9: Snackbar instead of Toast — Material, anchored to the fragment view,
     *  not throttled by Android 11+ toast restrictions. */
    private void toast(@StringRes int msgRes) {
        View v = getView();
        if (v != null) Snackbar.make(v, msgRes, Snackbar.LENGTH_SHORT).show();
    }
}
