package com.sensolab.devicemonitor;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AboutFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        view.findViewById(R.id.btn_rate).setOnClickListener(v -> openPlayStore());
        view.findViewById(R.id.btn_donate).setOnClickListener(v -> openDonate());
        view.findViewById(R.id.btn_share).setOnClickListener(v -> shareApp());
        view.findViewById(R.id.btn_email).setOnClickListener(v -> sendEmail());

        return view;
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
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.paypal.com/donate")));
        } catch (Exception e) {
            Toast.makeText(requireContext(),"Could not open browser",Toast.LENGTH_SHORT).show();
        }
    }

    private void shareApp() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT,"SensoLab - Device Sensor Monitor");
        i.putExtra(Intent.EXTRA_TEXT,
                "Check out SensoLab — the ultimate device sensor monitor for Android!\n" +
                "https://play.google.com/store/apps/details?id="+requireActivity().getPackageName());
        startActivity(Intent.createChooser(i,"Share SensoLab"));
    }

    private void sendEmail() {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@sensolab.app"));
        i.putExtra(Intent.EXTRA_SUBJECT,"SensoLab Feedback");
        try { startActivity(Intent.createChooser(i,"Send Email")); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(),"No email app found",Toast.LENGTH_SHORT).show();
        }
    }
}
