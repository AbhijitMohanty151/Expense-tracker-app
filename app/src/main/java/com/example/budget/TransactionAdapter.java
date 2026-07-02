package com.example.budget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    public interface ActionListener {
        void onEditClick(Transaction t);          // pencil tapped in normal mode
        void onEnterSelectMode();                 // first long-press
        void onSelectionChanged(int count);       // checkbox toggled
    }

    private final Context        context;
    private final ActionListener actionListener;
    private final List<Transaction> transactions = new ArrayList<>();

    private boolean    selectMode   = false;
    private final List<Long> selectedIds = new ArrayList<>();

    public TransactionAdapter(Context context, ActionListener actionListener) {
        this.context        = context;
        this.actionListener = actionListener;
    }

    /* =========================================================
       Data methods
       ========================================================= */

    /** Append a new batch – animates only the new rows. */
    public void appendTransactions(List<Transaction> more) {
        int start = transactions.size();
        transactions.addAll(more);
        notifyItemRangeInserted(start, more.size());
    }

    /** Clear list for a full refresh. */
    public void clearTransactions() {
        int size = transactions.size();
        transactions.clear();
        notifyItemRangeRemoved(0, size);
    }

    /* =========================================================
       Select mode
       ========================================================= */

    public void enterSelectMode() {
        selectMode = true;
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public void exitSelectMode() {
        selectMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectMode()        { return selectMode; }
    public List<Long> getSelectedIds()   { return selectedIds; }

    /* =========================================================
       RecyclerView
       ========================================================= */

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Transaction t = transactions.get(position);

        String desc = (t.getDescription() == null || t.getDescription().isEmpty())
                ? "No description" : t.getDescription();
        h.tvDescription.setText(desc);
        h.tvDateTime.setText(t.getDate() + "  " + t.getTime());

        boolean isCredit = "credit".equals(t.getType());
        h.tvAmount.setText((isCredit ? "+" : "−") + "₹" + String.format("%.2f", t.getAmount()));
        h.tvAmount.setTextColor(ContextCompat.getColor(context,
                isCredit ? R.color.txn_credit : R.color.txn_debit));
        h.stripe.setBackgroundColor(ContextCompat.getColor(context,
                isCredit ? R.color.txn_credit : R.color.txn_debit));

        /* ---- Select mode vs normal mode ---- */
        if (selectMode) {
            h.checkBox.setVisibility(View.VISIBLE);
            h.btnEdit .setVisibility(View.GONE);
            h.checkBox.setChecked(selectedIds.contains(t.getId()));

            h.itemView.setOnClickListener(v -> {
                Long id = t.getId();
                if (selectedIds.contains(id)) {
                    selectedIds.remove(id);
                    h.checkBox.setChecked(false);
                } else {
                    selectedIds.add(id);
                    h.checkBox.setChecked(true);
                }
                actionListener.onSelectionChanged(selectedIds.size());
            });
            h.itemView.setOnLongClickListener(null);

        } else {
            h.checkBox.setVisibility(View.GONE);
            h.btnEdit .setVisibility(View.VISIBLE);
            h.checkBox.setChecked(false);

            h.btnEdit.setOnClickListener(v -> actionListener.onEditClick(t));

            h.itemView.setOnClickListener(null);
            h.itemView.setOnLongClickListener(v -> {
                actionListener.onEnterSelectMode();
                return true;
            });
        }
    }

    @Override
    public int getItemCount() { return transactions.size(); }

    /* =========================================================
       ViewHolder
       ========================================================= */

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final View     stripe;
        final TextView tvDescription, tvDateTime, tvAmount;
        final TextView btnEdit;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox      = itemView.findViewById(R.id.checkbox);
            stripe        = itemView.findViewById(R.id.view_stripe);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvDateTime    = itemView.findViewById(R.id.tv_date_time);
            tvAmount      = itemView.findViewById(R.id.tv_amount);
            btnEdit       = itemView.findViewById(R.id.btn_edit);
        }
    }
}
