package com.example.budget;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

public class TransactionBottomSheet extends BottomSheetDialogFragment {

    public interface OnTransactionSavedListener {
        void onTransactionSaved();
    }

    // Args keys
    private static final String ARG_ACCOUNT_ID = "account_id";
    private static final String ARG_TYPE        = "type";
    private static final String ARG_IS_EDIT     = "is_edit";
    private static final String ARG_TXN_ID      = "txn_id";
    private static final String ARG_OLD_TYPE    = "old_type";
    private static final String ARG_OLD_AMOUNT  = "old_amount";
    private static final String ARG_PRE_AMOUNT  = "pre_amount";
    private static final String ARG_PRE_DESC    = "pre_desc";
    private static final String ARG_PRE_DATE    = "pre_date";
    private static final String ARG_PRE_TIME    = "pre_time";

    private OnTransactionSavedListener listener;

    /* ---- Factory: ADD mode ---- */
    public static TransactionBottomSheet newInstance(long accountId, String type) {
        TransactionBottomSheet sheet = new TransactionBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_ACCOUNT_ID, accountId);
        args.putString(ARG_TYPE, type);
        args.putBoolean(ARG_IS_EDIT, false);
        sheet.setArguments(args);
        return sheet;
    }

    /* ---- Factory: EDIT mode ---- */
    public static TransactionBottomSheet newEditInstance(Transaction t) {
        TransactionBottomSheet sheet = new TransactionBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_ACCOUNT_ID, t.getAccountId());
        args.putString(ARG_TYPE,       t.getType());
        args.putBoolean(ARG_IS_EDIT,   true);
        args.putLong(ARG_TXN_ID,       t.getId());
        args.putString(ARG_OLD_TYPE,   t.getType());
        args.putDouble(ARG_OLD_AMOUNT, t.getAmount());
        args.putDouble(ARG_PRE_AMOUNT, t.getAmount());
        args.putString(ARG_PRE_DESC,   t.getDescription() != null ? t.getDescription() : "");
        args.putString(ARG_PRE_DATE,   t.getDate());
        args.putString(ARG_PRE_TIME,   t.getTime());
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnTransactionSavedListener(OnTransactionSavedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_transaction_bottom_sheet, container, false);

        Bundle args = getArguments();
        if (args == null) return view;

        long    accountId       = args.getLong(ARG_ACCOUNT_ID);
        String  transactionType = args.getString(ARG_TYPE, "credit");
        boolean isEdit          = args.getBoolean(ARG_IS_EDIT, false);
        long    txnId           = args.getLong(ARG_TXN_ID, -1);
        String  oldType         = args.getString(ARG_OLD_TYPE, transactionType);
        double  oldAmount       = args.getDouble(ARG_OLD_AMOUNT, 0);

        TextView          tvTitle      = view.findViewById(R.id.tv_transaction_title);
        TextInputEditText etAmount     = view.findViewById(R.id.et_amount);
        TextView          tvExprPreview = view.findViewById(R.id.tv_expr_preview); // NEW
        TextInputEditText etDesc       = view.findViewById(R.id.et_description);
        TextInputEditText etDate       = view.findViewById(R.id.et_date);
        TextInputEditText etTime       = view.findViewById(R.id.et_time);
        Button            btnSubmit    = view.findViewById(R.id.btn_submit);

        boolean isCredit = "credit".equals(transactionType);

        /* ---- Title & button label ---- */
        if (isEdit) {
            tvTitle.setText("Edit Transaction");
            btnSubmit.setText("Save Changes");
        } else {
            tvTitle.setText(isCredit ? "Add Amount" : "Subtract Amount");
            btnSubmit.setText(isCredit ? "Add" : "Subtract");
        }
        btnSubmit.setBackgroundTintList(
                requireContext().getColorStateList(isCredit ? R.color.txn_credit : R.color.txn_debit));

        /* ---- Pre-fill ---- */
        String currentDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
        String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(Calendar.getInstance().getTime());

        if (isEdit) {
            etAmount.setText(String.format(Locale.getDefault(), "%.2f", args.getDouble(ARG_PRE_AMOUNT)));
            etDesc.setText(args.getString(ARG_PRE_DESC, ""));
            etDate.setText(args.getString(ARG_PRE_DATE, currentDate));
            etTime.setText(args.getString(ARG_PRE_TIME, currentTime));
        } else {
            etDate.setText(currentDate);
            etTime.setText(currentTime);
        }

        /* ---- Live expression preview (NEW) ---- */
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String raw = s.toString().trim();

                // Hide preview for empty input or plain numbers (no operators)
                if (raw.isEmpty() || raw.matches("[0-9]*\\.?[0-9]*")) {
                    tvExprPreview.setVisibility(View.GONE);
                    return;
                }

                Double result = ExpressionEvaluator.evaluate(raw);
                if (result == null) {
                    tvExprPreview.setText("⚠ Invalid expression");
                    tvExprPreview.setTextColor(0xFFE74C3C); // red
                } else {
                    tvExprPreview.setText("= ₹" + String.format(Locale.getDefault(), "%.2f", result));
                    tvExprPreview.setTextColor(0xFF1A73E8); // blue
                }
                tvExprPreview.setVisibility(View.VISIBLE);
            }
        });

        /* ---- Date picker ---- */
        etDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(requireContext(),
                    (picker, year, month, day) ->
                            etDate.setText(String.format(Locale.getDefault(),
                                    "%02d/%02d/%04d", day, month + 1, year)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                    .show();
        });

        /* ---- Time picker ---- */
        etTime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(requireContext(),
                    (picker, hour, minute) -> {
                        String amPm = hour >= 12 ? "PM" : "AM";
                        int h12 = (hour == 0) ? 12 : (hour > 12 ? hour - 12 : hour);
                        etTime.setText(String.format(Locale.getDefault(),
                                "%02d:%02d %s", h12, minute, amPm));
                    },
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false)
                    .show();
        });

        /* ---- Submit ---- */
        btnSubmit.setOnClickListener(v -> {
            String rawAmount = Objects.requireNonNull(etAmount.getText()).toString().trim();

            if (rawAmount.isEmpty()) {
                Toast.makeText(getContext(), "Enter an amount", Toast.LENGTH_SHORT).show();
                return;
            }

            // Use ExpressionEvaluator so plain numbers AND expressions both work (NEW)
            Double amount = ExpressionEvaluator.evaluate(rawAmount);
            if (amount == null || amount <= 0) {
                etAmount.setError("Enter a valid amount or expression (e.g. 100+30-20)");
                return;
            }

            String desc = Objects.requireNonNull(etDesc.getText()).toString().trim();
            String date = Objects.requireNonNull(etDate.getText()).toString().trim();
            String time = Objects.requireNonNull(etTime.getText()).toString().trim();

            DatabaseHelper db = new DatabaseHelper(requireContext());
            if (isEdit) {
                db.updateTransaction(txnId, accountId, oldType, oldAmount,
                        transactionType, amount, desc, date, time);
            } else {
                db.addTransaction(accountId, transactionType, amount, desc, date, time);
            }

            if (listener != null) listener.onTransactionSaved();
            dismiss();
        });

        return view;
    }
}
