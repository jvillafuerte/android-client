package io.netbird.client.ui;

import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Opens a bottom sheet at its full height.
 *
 * <p>On a TV the peek state is not usable: the sheet parks at the bottom with
 * only its header visible, and there is no drag gesture to pull it up. Every
 * sheet that contains a list therefore expands itself in {@code onStart()}.
 */
public final class BottomSheetExpander {
    public static void expand(BottomSheetDialogFragment sheet) {
        if (!(sheet.getDialog() instanceof BottomSheetDialog)) {
            return;
        }
        BottomSheetDialog dialog = (BottomSheetDialog) sheet.getDialog();
        View content = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (content == null) {
            return;
        }
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(content);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private BottomSheetExpander() {
    }
}
