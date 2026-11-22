package io.netbird.client.ui.troubleshoot;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.netbird.client.databinding.SheetAnonymizeLevelBinding;
import io.netbird.client.tool.Preferences;

import io.netbird.client.ui.BottomSheetExpander;
public class AnonymizeLevelSheet extends BottomSheetDialogFragment {

    public interface OnLevelChangedListener {
        void onAnonymizeLevelChanged(String level);
    }

    private static final String ARG_LEVEL = "level";

    private SheetAnonymizeLevelBinding binding;

    public static AnonymizeLevelSheet newInstance(String current) {
        AnonymizeLevelSheet sheet = new AnonymizeLevelSheet();
        Bundle args = new Bundle();
        args.putString(ARG_LEVEL, current);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetAnonymizeLevelBinding.inflate(inflater, container, false);

        binding.levelRowNone.setOnClickListener(v -> pick(Preferences.ANONYMIZE_LEVEL_NONE));
        binding.levelRowDefault.setOnClickListener(v -> pick(Preferences.ANONYMIZE_LEVEL_DEFAULT));
        binding.levelRowStrict.setOnClickListener(v -> pick(Preferences.ANONYMIZE_LEVEL_STRICT));

        showCheckmarkFor(currentLevel());
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetExpander.expand(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private String currentLevel() {
        Bundle args = getArguments();
        if (args == null) {
            return Preferences.ANONYMIZE_LEVEL_DEFAULT;
        }
        return args.getString(ARG_LEVEL, Preferences.ANONYMIZE_LEVEL_DEFAULT);
    }

    private void showCheckmarkFor(String level) {
        boolean none = Preferences.ANONYMIZE_LEVEL_NONE.equals(level);
        boolean strict = Preferences.ANONYMIZE_LEVEL_STRICT.equals(level);
        binding.levelCheckNone.setVisibility(none ? View.VISIBLE : View.INVISIBLE);
        binding.levelCheckStrict.setVisibility(strict ? View.VISIBLE : View.INVISIBLE);
        binding.levelCheckDefault.setVisibility(none || strict ? View.INVISIBLE : View.VISIBLE);
        // Drives the selected-row background in sheet_row_bg, not just the check.
        binding.levelRowNone.setSelected(none);
        binding.levelRowStrict.setSelected(strict);
        binding.levelRowDefault.setSelected(!none && !strict);
    }

    private void pick(String level) {
        if (getParentFragment() instanceof OnLevelChangedListener) {
            ((OnLevelChangedListener) getParentFragment()).onAnonymizeLevelChanged(level);
        }
        dismiss();
    }
}
