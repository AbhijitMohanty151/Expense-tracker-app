package com.example.budget;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.Objects;

public class AccountsBottomSheet extends BottomSheetDialogFragment {

    private DatabaseHelper db;
    private AccountAdapter adapter;
    private List<Account>  accounts;

    private TextView tvTitle;
    private Button   btnAdd, btnRemoveDelete, btnCancel;
    private TextView tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.account_list, container, false);

        db              = new DatabaseHelper(requireContext());
        tvTitle         = view.findViewById(R.id.tv_sheet_title);
        tvEmpty         = view.findViewById(R.id.tv_empty);
        btnAdd          = view.findViewById(R.id.btn_add);
        btnRemoveDelete = view.findViewById(R.id.btn_remove_delete);
        btnCancel       = view.findViewById(R.id.btn_cancel);

        RecyclerView rv = view.findViewById(R.id.rv_accounts);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        accounts = db.getAllAccounts();
        adapter  = new AccountAdapter(accounts, new AccountAdapter.OnAccountClickListener() {
            @Override
            public void onAccountClick(Account account) {
                Intent intent = new Intent(getContext(), AccountDetailActivity.class);
                intent.putExtra("account_id", account.getId());
                startActivity(intent);
                dismiss();
            }
            @Override
            public void onAccountLongClick(Account account) {
                if (!adapter.isRemoveMode()) {
                    showAccountOptionsDialog(account);
                }
            }
        });
        rv.setAdapter(adapter);
        updateEmptyState();

        btnAdd.setOnClickListener(v -> showAddDialog());
        btnRemoveDelete.setOnClickListener(v -> {
            if (adapter.isRemoveMode()) deleteSelected();
            else enterRemoveMode();
        });
        btnCancel.setOnClickListener(v -> exitRemoveMode());

        Objects.requireNonNull(getDialog()).setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK
                    && event.getAction() == KeyEvent.ACTION_UP
                    && adapter.isRemoveMode()) {
                exitRemoveMode();
                return true;
            }
            return false;
        });

        return view;
    }

    /* ---- mode transitions ---- */

    private void enterRemoveMode() {
        adapter.setRemoveMode(true);
        btnAdd.setVisibility(View.GONE);
        btnCancel.setVisibility(View.VISIBLE);
        btnRemoveDelete.setText("Delete Selected");
        tvTitle.setText("Select Accounts");
    }

    private void exitRemoveMode() {
        adapter.setRemoveMode(false);
        btnAdd.setVisibility(View.VISIBLE);
        btnCancel.setVisibility(View.GONE);
        btnRemoveDelete.setText("Remove");
        tvTitle.setText("Accounts");
    }

    /* ---- long-press options (Rename / Remove) ---- */

    private void showAccountOptionsDialog(Account account) {
        String[] options = {"Rename", "Remove"};
        new AlertDialog.Builder(requireContext())
                .setTitle(account.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showRenameDialog(account);
                    else             enterRemoveMode();
                })
                .show();
    }

    private void showRenameDialog(Account account) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_account, null);
        EditText etName = dialogView.findViewById(R.id.et_account_name);
        etName.setText(account.getName());

        new AlertDialog.Builder(requireContext())
                .setTitle("Rename Account")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(getContext(), "Please enter a name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.renameAccount(account.getId(), newName);
                    refreshList();
                    Toast.makeText(getContext(), "Renamed to \"" + newName + "\"", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();

        etName.post(() -> {
            etName.requestFocus();
            etName.selectAll();
        });
    }

    /* ---- actions ---- */

    private void deleteSelected() {
        List<Long> ids = adapter.getSelectedIds();
        if (ids.isEmpty()) {
            Toast.makeText(getContext(), "No accounts selected", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Accounts")
                .setMessage("Delete " + ids.size() + " account(s) and all their transactions?")
                .setPositiveButton("Delete", (d, w) -> {
                    db.deleteAccounts(ids);
                    accounts = db.getAllAccounts();
                    adapter.setAccounts(accounts);
                    exitRemoveMode();
                    updateEmptyState();
                    Toast.makeText(getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_account, null);
        EditText etName = dialogView.findViewById(R.id.et_account_name);

        new AlertDialog.Builder(requireContext())
                .setTitle("New Account")
                .setView(dialogView)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "Please enter a name", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Check if an account with this name already exists
                    Account existing = db.findAccountByName(name);
                    if (existing != null) {
                        // Ask user whether to copy or cancel
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Account Exists")
                                .setMessage("An account named \"" + existing.getName()
                                        + "\" already exists.\nCreate a copy with its balance and transactions?")
                                .setPositiveButton("Copy", (d2, w2) -> {
                                    db.copyAccount(existing);
                                    refreshList();
                                    Toast.makeText(getContext(),
                                            "Copy of \"" + existing.getName() + "\" created",
                                            Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else {
                        db.addAccount(name);
                        refreshList();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();

        etName.post(() -> etName.requestFocus());
    }

    private void refreshList() {
        accounts = db.getAllAccounts();
        adapter.setAccounts(accounts);
        updateEmptyState();
    }

    private void updateEmptyState() {
        tvEmpty.setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
