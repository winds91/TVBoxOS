package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

public class ExitConfirmDialog extends BaseDialog {

    public ExitConfirmDialog(@NonNull @NotNull Context context) {
        super(context, R.style.CustomDialogStyleDim);
        setOwnerActivity((Activity) context);
        setContentView(R.layout.dialog_exit_confirm);
        setCancelable(true);

        View btnConfirm = findViewById(R.id.btnConfirm);
        View btnCancel = findViewById(R.id.btnCancel);

        btnConfirm.setOnClickListener(v -> {
            if (listener != null) listener.onConfirm();
            dismiss();
        });

        btnCancel.setOnClickListener(v -> {
            if (listener != null) listener.onCancel();
            dismiss();
        });

        btnConfirm.post(btnConfirm::requestFocus);
    }

    private OnListener listener;

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    public interface OnListener {
        void onConfirm();
        void onCancel();
    }
}
