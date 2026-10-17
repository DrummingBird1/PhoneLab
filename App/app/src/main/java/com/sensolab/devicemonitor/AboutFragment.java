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
    @Nullable private TipJar tipJar;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        binding = FragmentAboutBinding.inflate(inflater, container, false);
        binding.tvVersion.setText(getString(R.string.app_version_line,
                BuildConfig.VERSION_NAME, requireActivity().getPackageName()));
        binding.btnShare.setOnClickListener(v -> shareApp());
        binding.btnEmail.setOnClickListener(v -> sendEmail());
        binding.btnWeb.setOnClickListener(v -> openUrl(getString(R.string.about_web_url)));
        binding.btnSource.setOnClickListener(v -> openUrl(getString(R.string.about_source_url)));

        // Google Play-only actions; the foss build (GitHub / F-Droid) hides them.
        if (BuildConfig.PLAY_BUILD) {
            binding.btnRate.setOnClickListener(v -> openPlayStore());
        } else {
            binding.btnRate.setVisibility(View.GONE);
        }
        if (TipJar.isAvailable()) {
            binding.btnDonate.setOnClickListener(v -> {
                if (tipJar == null) tipJar = new TipJar(this);
                tipJar.open();
            });
        } else {
            binding.btnDonate.setVisibility(View.GONE);
        }

        // Y6: privacy policy link only when a URL is configured
        if (!BuildConfig.PRIVACY_POLICY_URL.isEmpty()) {
            binding.btnPrivacy.setVisibility(View.VISIBLE);
            binding.btnPrivacy.setOnClickListener(v -> openUrl(BuildConfig.PRIVACY_POLICY_URL));
        }
        return binding.getRoot();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (tipJar != null) { tipJar.release(); tipJar = null; }
        binding = null;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast(R.string.about_no_browser);
        }
    }

    private void openPlayStore() {
        String pkg = requireActivity().getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
        } catch (ActivityNotFoundException e) {
            openUrl("https://play.google.com/store/apps/details?id=" + pkg);
        }
    }

    private void shareApp() {
        String body = BuildConfig.PLAY_BUILD
                ? getString(R.string.about_share_body, requireActivity().getPackageName())
                : getString(R.string.about_share_body_foss, getString(R.string.about_source_url) + "/releases");
        Intent i = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.about_share_subject))
                .putExtra(Intent.EXTRA_TEXT, body);
        startActivity(Intent.createChooser(i, getString(R.string.about_share_chooser)));
    }

    private void sendEmail() {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@sensolab.app"))
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.about_email_subject));
        try { startActivity(Intent.createChooser(i, getString(R.string.about_email_chooser))); }
        catch (ActivityNotFoundException e) { toast(R.string.about_no_email_app); }
    }

    /** N9: Snackbar instead of Toast — anchored, and not throttled on Android 11+. */
    private void toast(@StringRes int msgRes) {
        View v = getView();
        if (v != null) Snackbar.make(v, msgRes, Snackbar.LENGTH_LONG).show();
    }
}
