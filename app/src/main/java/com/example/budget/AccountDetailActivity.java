package com.example.budget;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

public class AccountDetailActivity extends AppCompatActivity
        implements TransactionAdapter.ActionListener {

    private static final int BATCH_SIZE = 20;

    // Light red used for the balance figure when it goes negative,
    // chosen for contrast against the blue header card.
    private static final int COLOR_BALANCE_NEGATIVE = 0xFFFF8A80;
    private static final int COLOR_BALANCE_POSITIVE = 0xFFFFFFFF;

    private long accountId;
    private DatabaseHelper db;

    private TextView           tvAccountName, tvBalance, tvTotalCredit, tvTotalDebit;
    private TextView           tvNoTransactions;
    private RecyclerView       recyclerView;
    private ProgressBar        progressBar;
    private TransactionAdapter adapter;

    private LinearLayout llNormalActions, llSelectActions;
    private Button       btnDelete;

    private int     currentOffset = 0;
    private boolean isLoading     = false;
    private boolean hasMore       = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_detail);

        accountId = getIntent().getLongExtra("account_id", -1);
        db        = new DatabaseHelper(this);

        /* ---- Toolbar ---- */
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> {
            if (adapter.isSelectMode()) exitSelectMode();
            else finish();
        });

        /* ---- Views ---- */
        tvAccountName    = findViewById(R.id.tv_account_name);
        tvBalance        = findViewById(R.id.tv_balance);
        tvTotalCredit    = findViewById(R.id.tv_total_credit);
        tvTotalDebit     = findViewById(R.id.tv_total_debit);
        tvNoTransactions = findViewById(R.id.tv_no_transactions);
        progressBar      = findViewById(R.id.progress_bar);
        recyclerView     = findViewById(R.id.rv_transactions);
        llNormalActions  = findViewById(R.id.ll_normal_actions);
        llSelectActions  = findViewById(R.id.ll_select_actions);
        btnDelete        = findViewById(R.id.btn_delete);

        Button  btnCredit       = findViewById(R.id.btn_credit);
        Button  btnDebit        = findViewById(R.id.btn_debit);
        Button  btnTransfer     = findViewById(R.id.btn_transfer);
        Button  btnCancelSelect = findViewById(R.id.btn_cancel_select);
        TextView btnAnalysis    = findViewById(R.id.btn_analysis);

        /* ---- RecyclerView ---- */
        LinearLayoutManager lm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(lm);
        adapter = new TransactionAdapter(this, this);
        recyclerView.setAdapter(adapter);

        /* ---- Infinite scroll ---- */
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || isLoading || !hasMore || adapter.isSelectMode()) return;
                int total       = lm.getItemCount();
                int lastVisible = lm.findLastVisibleItemPosition();
                if (lastVisible >= total - 4) loadNextBatch(true);
            }
        });

        /* ---- Button listeners ---- */
        btnCredit.setOnClickListener(v -> openAddSheet("credit"));
        btnDebit .setOnClickListener(v -> openAddSheet("debit"));
        btnTransfer.setOnClickListener(v -> openTransferSheet());
        btnCancelSelect.setOnClickListener(v -> exitSelectMode());

        btnDelete.setOnClickListener(v -> {
            List<Long> ids = adapter.getSelectedIds();
            if (ids.isEmpty()) {
                Toast.makeText(this, "Nothing selected", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Delete Transactions")
                    .setMessage("Delete " + ids.size() + " transaction(s)?\nThis cannot be undone.")
                    .setPositiveButton("Delete", (d, w) -> {
                        db.deleteTransactions(ids, accountId);
                        exitSelectMode();
                        refreshAll();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnAnalysis.setOnClickListener(v -> {
            AnalysisBottomSheet sheet = AnalysisBottomSheet.newInstance(accountId);
            sheet.show(getSupportFragmentManager(), "analysis");
        });

        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAccountHeader();
    }

    @Override
    public void onBackPressed() {
        if (adapter.isSelectMode()) exitSelectMode();
        else super.onBackPressed();
    }

    /* =========================================================
       TransactionAdapter.ActionListener
       ========================================================= */

    @Override
    public void onEditClick(Transaction t) {
        TransactionBottomSheet sheet = TransactionBottomSheet.newEditInstance(t);
        sheet.setOnTransactionSavedListener(this::refreshAll);
        sheet.show(getSupportFragmentManager(), "edit_txn");
    }

    @Override
    public void onEnterSelectMode() { enterSelectMode(); }

    @Override
    public void onSelectionChanged(int count) {
        btnDelete.setText(count == 0 ? "Delete" : "Delete (" + count + ")");
    }

    /* =========================================================
       Select mode
       ========================================================= */

    private void enterSelectMode() {
        adapter.enterSelectMode();
        llNormalActions.setVisibility(View.GONE);
        llSelectActions.setVisibility(View.VISIBLE);
        btnDelete.setText("Delete");
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Select Transactions");
    }

    private void exitSelectMode() {
        adapter.exitSelectMode();
        llNormalActions.setVisibility(View.VISIBLE);
        llSelectActions.setVisibility(View.GONE);
        loadAccountHeader();
    }

    /* =========================================================
       Data helpers
       ========================================================= */

    private void loadAccountHeader() {
        Account account = db.getAccount(accountId);
        if (account == null) { finish(); return; }
        tvAccountName.setText(account.getName());
        tvBalance.setText(String.format("₹%.2f", account.getBalance()));
        tvBalance.setTextColor(account.getBalance() < 0
                ? COLOR_BALANCE_NEGATIVE
                : COLOR_BALANCE_POSITIVE);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(account.getName());

        // Total credit / debit stats
        double[] stats = db.getTotalCreditDebit(accountId);
        tvTotalCredit.setText(String.format("₹%.2f", stats[0]));
        tvTotalDebit .setText(String.format("₹%.2f", stats[1]));
    }

    private void loadNextBatch(boolean showLoader) {
        isLoading = true;
        if (showLoader) progressBar.setVisibility(View.VISIBLE);

        List<Transaction> batch = db.getTransactions(accountId, currentOffset, BATCH_SIZE);
        currentOffset += batch.size();
        if (batch.size() < BATCH_SIZE) hasMore = false;

        adapter.appendTransactions(batch);

        boolean empty = adapter.getItemCount() == 0;
        tvNoTransactions.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView    .setVisibility(empty ? View.GONE    : View.VISIBLE);

        if (showLoader) {
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> progressBar.setVisibility(View.GONE), 1000);
        }
        isLoading = false;
    }

    private void refreshAll() {
        currentOffset = 0;
        hasMore       = true;
        isLoading     = false;
        adapter.clearTransactions();
        loadAccountHeader();
        loadNextBatch(false);
    }

    private void openAddSheet(String type) {
        TransactionBottomSheet sheet = TransactionBottomSheet.newInstance(accountId, type);
        sheet.setOnTransactionSavedListener(this::refreshAll);
        sheet.show(getSupportFragmentManager(), "add_txn");
    }

    private void openTransferSheet() {
        // Need at least one other account to transfer to.
        if (db.getAllAccounts().size() < 2) {
            Toast.makeText(this, "Create another account first to transfer money", Toast.LENGTH_LONG).show();
            return;
        }
        TransferBottomSheet sheet = TransferBottomSheet.newInstance(accountId);
        sheet.setOnTransferSavedListener(this::refreshAll);
        sheet.show(getSupportFragmentManager(), "transfer_txn");
    }
}
