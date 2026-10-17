package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sensolab.devicemonitor.databinding.FragmentDiagnosticsBinding;
import com.sensolab.devicemonitor.databinding.ItemDiagTestBinding;

/**
 * "Check this phone" mode: guided hardware tests (touch, display, speaker, mic, vibration,
 * buttons, proximity, flashlight, sensors, battery) with a shareable pass/fail report —
 * aimed at people buying or selling a used phone.
 */
public class DiagnosticsFragment extends Fragment {

    private static final String STATE_SEQUENCE = "sequence_index";

    private FragmentDiagnosticsBinding binding;
    /** Index of the next test in a "Start full check" run, or -1 when not running a sequence. */
    private int sequenceIndex = -1;

    private final ActivityResultLauncher<Intent> runTest = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onTestFinished);

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = FragmentDiagnosticsBinding.inflate(inf, c, false);
        if (s != null) sequenceIndex = s.getInt(STATE_SEQUENCE, -1);
        binding.btnDiagStart.setOnClickListener(v -> {
            sequenceIndex = 0;
            launchNext();
        });
        binding.btnDiagShare.setOnClickListener(v -> shareReport());
        binding.btnDiagReset.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.diag_reset_confirm)
                .setPositiveButton(R.string.diag_btn_reset, (d, w) -> {
                    DiagnosticsStore.clear(requireContext());
                    render();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        return binding.getRoot();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_SEQUENCE, sequenceIndex);
    }

    @Override public void onResume() {
        super.onResume();
        render();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void render() {
        if (binding == null) return;
        Context ctx = requireContext();
        binding.diagList.removeAllViews();
        int pass = 0, fail = 0, skipped = 0, notRun = 0;
        LayoutInflater inf = LayoutInflater.from(ctx);
        for (DiagTest t : DiagTest.values()) {
            DiagnosticsStore.Result r = DiagnosticsStore.result(ctx, t);
            switch (r) {
                case PASS:    pass++; break;
                case FAIL:    fail++; break;
                case SKIPPED: skipped++; break;
                default:      notRun++; break;
            }
            ItemDiagTestBinding row = ItemDiagTestBinding.inflate(inf, binding.diagList, false);
            row.diagIcon.setImageResource(t.icon);
            row.diagTitle.setText(t.title);
            row.diagDesc.setText(t.description);
            row.diagResultIcon.setImageResource(r.icon);
            row.diagResultIcon.setContentDescription(getString(r.label));
            row.diagResultIcon.setColorFilter(ContextCompat.getColor(ctx, colorFor(r)));
            String note = DiagnosticsStore.note(ctx, t);
            if (!note.isEmpty()) {
                row.diagNote.setText(note);
                row.diagNote.setVisibility(View.VISIBLE);
            }
            row.getRoot().setOnClickListener(v -> {
                sequenceIndex = -1;
                runTest.launch(DiagnosticTestActivity.intent(ctx, t));
            });
            binding.diagList.addView(row.getRoot());
        }
        binding.tvDiagSummary.setText(getString(R.string.diag_summary, pass, fail, skipped, notRun));
    }

    private static int colorFor(DiagnosticsStore.Result r) {
        switch (r) {
            case PASS: return R.color.gauge_ok;
            case FAIL: return R.color.gauge_hot;
            case SKIPPED: return R.color.gauge_warn;
            default: return R.color.outline;
        }
    }

    private void onTestFinished(ActivityResult result) {
        if (sequenceIndex < 0) return;
        if (result.getResultCode() != android.app.Activity.RESULT_OK) {
            sequenceIndex = -1; // user backed out of the run entirely
            return;
        }
        sequenceIndex++;
        if (sequenceIndex >= DiagTest.values().length) {
            sequenceIndex = -1;
            return;
        }
        launchNext();
    }

    private void launchNext() {
        if (sequenceIndex < 0 || sequenceIndex >= DiagTest.values().length) return;
        runTest.launch(DiagnosticTestActivity.intent(requireContext(), DiagTest.values()[sequenceIndex]));
    }

    private void shareReport() {
        Intent i = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diag_report_title))
                .putExtra(Intent.EXTRA_TEXT, DiagnosticsReport.build(requireContext()));
        startActivity(Intent.createChooser(i, getString(R.string.diag_btn_share)));
    }
}
