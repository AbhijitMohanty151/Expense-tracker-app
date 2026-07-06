package com.example.budget;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Bottom sheet that moves money from the currently open account to
 * another account, recorded as a linked debit/credit pair.
 */
public class TransferBottomSheet extends BottomSheetDialogFragment {

    public interface OnTransferSavedListener {
        void onTransferSaved();
    }

    private static final String ARG_ACCOUNT_ID = "account_id";

    public static TransferBottomSheet newInstance(long accountId) {
        TransferBottomSheet sheet = new TransferBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_ACCOUNT_ID, accountId);
        sheet.setArguments(args);
        return sheet;
    }

    private OnTransferSavedListener listener;

    public void setOnTransferSavedListener(OnTransferSavedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transfer_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        long fromAccountId = args != null ? args.getLong(ARG_ACCOUNT_ID) : -1;

        DatabaseHelper db = new DatabaseHelper(requireContext());

        TextView          tvFromAccount   = view.findViewById(R.id.tv_from_account_name);
        Spinner           spinnerTo       = view.findViewById(R.id.spinner_to_account);
        TextInputEditText etAmount        = view.findViewById(R.id.et_transfer_amount);
        TextView          tvExprPreview   = view.findViewById(R.id.tv_transfer_expr_preview);
        TextInputEditText etDesc          = view.findViewById(R.id.et_transfer_description);
        TextInputEditText etDate          = view.findViewById(R.id.et_transfer_date);
        TextInputEditText etTime          = view.findViewById(R.id.et_transfer_time);
        Button            btnSubmit       = view.findViewById(R.id.btn_transfer_submit);

        Account fromAccount = db.getAccount(fromAccountId);
        tvFromAccount.setText(fromAccount != null ? fromAccount.getName() : "");

        // Every account except the one we're transferring from.
        List<Account> otherAccounts = new ArrayList<>();
        for (Account a : db.getAllAccounts()) {
            if (a.getId() != fromAccountId) otherAccounts.add(a);
        }

        if (otherAccounts.isEmpty()) {
            Toast.makeText(getContext(), "Create another account first to transfer money", Toast.LENGTH_LONG).show();
            dismiss();
            return;
        }

        List<String> names = new ArrayList<>();
        for (Account a : otherAccounts) names.add(a.getName());

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, names);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTo.setAdapter(spinnerAdapter);

        /* ---- Pre-fill date/time ---- */
        String currentDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
        String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
        etDate.setText(currentDate);
        etTime.setText(currentTime);

        /* ---- Live expression preview ---- */
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String raw = s.toString().trim();
                if (raw.isEmpty() || raw.matches("[0-9]*\\.?[0-9]*")) {
                    tvExprPreview.setVisibility(View.GONE);
                    return;
                }
                Double result = ExpressionEvaluator.evaluate(raw);
                if (result == null) {
                    tvExprPreview.setText("⚠ Invalid expression");
                    tvExprPreview.setTextColor(0xFFE74C3C);
                } else {
                    tvExprPreview.setText("= ₹" + String.format(Locale.getDefault(), "%.2f", result));
                    tvExprPreview.setTextColor(0xFF1A73E8);
                }
                tvExprPreview.setVisibility(View.VISIBLE);
            }
        });

        /* ---- Date / time pickers ---- */
        etDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(requireContext(),
                    (picker, year, month, day) ->
                            etDate.setText(String.format(Locale.getDefault(),
                                    "%02d/%02d/%04d", day, month + 1, year)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                    .show();
        });

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

            Double amount = ExpressionEvaluator.evaluate(rawAmount);
            if (amount == null) {
                etAmount.setError("Enter a valid amount or expression (e.g. 100+30-20)");
                return;
            }
            if (amount <= 0) {
                etAmount.setError(String.format(Locale.getDefault(),
                        "Result is ₹%.2f — amount must be greater than zero", amount));
                return;
            }

            int selectedIndex = spinnerTo.getSelectedItemPosition();
            if (selectedIndex < 0 || selectedIndex >= otherAccounts.size()) {
                Toast.makeText(getContext(), "Select a destination account", Toast.LENGTH_SHORT).show();
                return;
            }
            long toAccountId = otherAccounts.get(selectedIndex).getId();

            String desc = Objects.requireNonNull(etDesc.getText()).toString().trim();
            String date = Objects.requireNonNull(etDate.getText()).toString().trim();
            String time = Objects.requireNonNull(etTime.getText()).toString().trim();

            db.transferBetweenAccounts(fromAccountId, toAccountId, amount, desc, date, time);

            if (listener != null) listener.onTransferSaved();
            Toast.makeText(getContext(), "Transferred ₹" + String.format(Locale.getDefault(), "%.2f", amount),
                    Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }
}
