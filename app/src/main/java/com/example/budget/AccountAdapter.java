package com.example.budget;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {

    public interface OnAccountClickListener {
        void onAccountClick(Account account);
        void onAccountLongClick(Account account);
    }

    private List<Account> accounts;
    private final OnAccountClickListener listener;
    private boolean removeMode = false;
    private final List<Long> selectedIds = new ArrayList<>();

    public AccountAdapter(List<Account> accounts, OnAccountClickListener listener) {
        this.accounts = accounts;
        this.listener = listener;
    }

    /* ---- mode helpers ---- */

    public void setRemoveMode(boolean enable) {
        removeMode = enable;
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public boolean isRemoveMode() { return removeMode; }

    public List<Long> getSelectedIds() { return selectedIds; }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
        notifyDataSetChanged();
    }

    /* ---- RecyclerView ---- */

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_account, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Account account = accounts.get(position);
        h.tvName.setText(account.getName());
        h.tvBalance.setText(String.format("₹%.2f", account.getBalance()));
        // Show first letter in the icon circle
        h.tvIcon.setText(account.getName().isEmpty() ? "?" :
                String.valueOf(account.getName().charAt(0)).toUpperCase());

        // Checkbox visibility & state
        h.checkBox.setVisibility(removeMode ? View.VISIBLE : View.GONE);
        h.checkBox.setChecked(selectedIds.contains(account.getId()));

        h.itemView.setOnClickListener(v -> {
            if (removeMode) {
                Long id = account.getId();
                if (selectedIds.contains(id)) {
                    selectedIds.remove(id);
                    h.checkBox.setChecked(false);
                } else {
                    selectedIds.add(id);
                    h.checkBox.setChecked(true);
                }
            } else {
                listener.onAccountClick(account);
            }
        });

        h.itemView.setOnLongClickListener(v -> {
            listener.onAccountLongClick(account);
            return true;
        });
    }

    @Override
    public int getItemCount() { return accounts.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final TextView tvName, tvBalance, tvIcon;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox  = itemView.findViewById(R.id.checkbox);
            tvName    = itemView.findViewById(R.id.tv_account_name);
            tvBalance = itemView.findViewById(R.id.tv_account_balance);
            tvIcon    = itemView.findViewById(R.id.tv_icon);
        }
    }
}
