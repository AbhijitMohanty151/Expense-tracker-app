package com.example.budget;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Analysis sheet with a stackable-filter engine.
 *
 * Results are shown in two sections:
 *   1. Day-grouped summary table  (date | In | Spent)
 *   2. Individual transaction cards grouped under their date header
 *
 * Database work runs on a background thread; a ProgressBar is shown while loading.
 */
public class AnalysisBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_ACCOUNT_ID = "account_id";

    public static AnalysisBottomSheet newInstance(long accountId) {
        AnalysisBottomSheet f = new AnalysisBottomSheet();
        Bundle b = new Bundle();
        b.putLong(ARG_ACCOUNT_ID, accountId);
        f.setArguments(b);
        return f;
    }

    // ---- state ----
    private long           accountId;
    private DatabaseHelper db;
    private final List<TransactionFilter> activeFilters = new ArrayList<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler         uiHandler = new Handler(Looper.getMainLooper());

    // ---- result views ----
    private ChipGroup    chipGroup;
    private ProgressBar  progressBar;
    private TextView     tvNoResults;
    private TextView     tvSummary;
    private LinearLayout llSummaryTable;   // day-grouped credit/debit rows
    private LinearLayout llTransactions;   // individual transaction cards

    // ---- filter-panel views ----
    private LinearLayout      panelDateRange, panelDesc, panelType, panelAmount;
    private TextView          tvFromDate, tvToDate;
    private TextInputEditText etKeyword;
    private RadioGroup        rgDescMode, rgType;
    private TextInputEditText etMinAmount, etMaxAmount;

    private String pendingFrom, pendingTo; // "yyyy-MM-dd"

    // =========================================================
    // Lifecycle
    // =========================================================

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_analysis, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        accountId = getArguments() != null ? getArguments().getLong(ARG_ACCOUNT_ID) : -1;
        db = new DatabaseHelper(requireContext());

        // Result section
        chipGroup      = view.findViewById(R.id.chip_group_filters);
        progressBar    = view.findViewById(R.id.progress_bar);
        tvNoResults    = view.findViewById(R.id.tv_no_results);
        tvSummary      = view.findViewById(R.id.tv_summary);
        llSummaryTable = view.findViewById(R.id.ll_summary_table);
        llTransactions = view.findViewById(R.id.ll_transactions);

        // Filter-add buttons
        view.findViewById(R.id.btn_add_date_range).setOnClickListener(v -> togglePanel(panelDateRange));
        view.findViewById(R.id.btn_add_desc)      .setOnClickListener(v -> togglePanel(panelDesc));
        view.findViewById(R.id.btn_add_type)      .setOnClickListener(v -> togglePanel(panelType));
        view.findViewById(R.id.btn_add_amount)    .setOnClickListener(v -> togglePanel(panelAmount));
        view.findViewById(R.id.btn_clear_all)     .setOnClickListener(v -> clearAllFilters());

        // Filter panels
        panelDateRange = view.findViewById(R.id.panel_date_range);
        panelDesc      = view.findViewById(R.id.panel_desc);
        panelType      = view.findViewById(R.id.panel_type);
        panelAmount    = view.findViewById(R.id.panel_amount);

        // Date range panel
        tvFromDate = view.findViewById(R.id.tv_from_date);
        tvToDate   = view.findViewById(R.id.tv_to_date);
        view.findViewById(R.id.btn_pick_from) .setOnClickListener(v -> pickDate(true));
        view.findViewById(R.id.btn_pick_to)   .setOnClickListener(v -> pickDate(false));
        view.findViewById(R.id.btn_apply_date).setOnClickListener(v -> applyDateRange());

        // Description panel
        etKeyword  = view.findViewById(R.id.et_keyword);
        rgDescMode = view.findViewById(R.id.rg_desc_mode);
        view.findViewById(R.id.btn_apply_desc).setOnClickListener(v -> applyDesc());

        // Type panel
        rgType = view.findViewById(R.id.rg_type);
        view.findViewById(R.id.btn_apply_type).setOnClickListener(v -> applyType());

        // Amount panel
        etMinAmount = view.findViewById(R.id.et_min_amount);
        etMaxAmount = view.findViewById(R.id.et_max_amount);
        view.findViewById(R.id.btn_apply_amount).setOnClickListener(v -> applyAmount());

        runQuery();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // =========================================================
    // Panel toggle
    // =========================================================

    private void togglePanel(LinearLayout panel) {
        boolean showing = panel.getVisibility() == View.VISIBLE;
        collapseAllPanels();
        if (!showing) panel.setVisibility(View.VISIBLE);
    }

    private void collapseAllPanels() {
        panelDateRange.setVisibility(View.GONE);
        panelDesc     .setVisibility(View.GONE);
        panelType     .setVisibility(View.GONE);
        panelAmount   .setVisibility(View.GONE);
    }

    // =========================================================
    // Apply filters
    // =========================================================

    private void applyDateRange() {
        if (pendingFrom == null || pendingTo == null) {
            toast("Pick both From and To dates"); return;
        }
        if (pendingFrom.compareTo(pendingTo) > 0) {
            toast("'From' must be before 'To'"); return;
        }
        activeFilters.removeIf(f -> f.type == TransactionFilter.FilterType.DATE_RANGE);
        activeFilters.add(TransactionFilter.dateRange(pendingFrom, pendingTo));
        pendingFrom = null; pendingTo = null;
        tvFromDate.setText("—"); tvToDate.setText("—");
        collapseAllPanels();
        refreshChips();
        runQuery();
    }

    private void applyDesc() {
        String kw = etKeyword.getText() != null ? etKeyword.getText().toString().trim() : "";
        if (kw.isEmpty()) { toast("Enter a keyword"); return; }
        boolean exclude = rgDescMode.getCheckedRadioButtonId() == R.id.rb_exclude;
        activeFilters.add(TransactionFilter.description(kw, exclude));
        etKeyword.setText("");
        collapseAllPanels();
        refreshChips();
        runQuery();
    }

    private void applyType() {
        int id = rgType.getCheckedRadioButtonId();
        if (id == -1) { toast("Pick a type"); return; }
        activeFilters.removeIf(f -> f.type == TransactionFilter.FilterType.TXN_TYPE);
        activeFilters.add(TransactionFilter.txnType(id == R.id.rb_credit ? "credit" : "debit"));
        rgType.clearCheck();
        collapseAllPanels();
        refreshChips();
        runQuery();
    }

    private void applyAmount() {
        String minS = etMinAmount.getText() != null ? etMinAmount.getText().toString().trim() : "";
        String maxS = etMaxAmount.getText() != null ? etMaxAmount.getText().toString().trim() : "";
        if (minS.isEmpty() || maxS.isEmpty()) { toast("Enter both min and max amounts"); return; }
        try {
            double min = Double.parseDouble(minS);
            double max = Double.parseDouble(maxS);
            if (min > max) { toast("Min must be ≤ Max"); return; }
            activeFilters.removeIf(f -> f.type == TransactionFilter.FilterType.AMOUNT_RANGE);
            activeFilters.add(TransactionFilter.amountRange(min, max));
            etMinAmount.setText(""); etMaxAmount.setText("");
            collapseAllPanels();
            refreshChips();
            runQuery();
        } catch (NumberFormatException e) {
            toast("Invalid amount");
        }
    }

    // =========================================================
    // Chips
    // =========================================================

    private void refreshChips() {
        chipGroup.removeAllViews();
        for (int i = 0; i < activeFilters.size(); i++) {
            final int idx = i;
            Chip chip = new Chip(requireContext());
            chip.setText(activeFilters.get(i).label());
            chip.setCloseIconVisible(true);
            chip.setChipBackgroundColor(ColorStateList.valueOf(0xFFFFFFFF));
            chip.setTextColor(0xFF1a73e8);
            chip.setChipStrokeWidth(2f);
            chip.setChipStrokeColor(ColorStateList.valueOf(0xFF1a73e8));
            chip.setOnCloseIconClickListener(v -> {
                activeFilters.remove(idx);
                refreshChips();
                runQuery();
            });
            chipGroup.addView(chip);
        }
    }

    private void clearAllFilters() {
        activeFilters.clear();
        refreshChips();
        collapseAllPanels();
        runQuery();
    }

    // =========================================================
    // Query (background thread)
    // =========================================================

    private void runQuery() {
        // Show loader, hide old results
        progressBar   .setVisibility(View.VISIBLE);
        tvNoResults   .setVisibility(View.GONE);
        tvSummary     .setVisibility(View.GONE);
        llSummaryTable.removeAllViews();
        llTransactions.removeAllViews();

        // Snapshot the filter list for the background thread
        final List<TransactionFilter> snapshot = new ArrayList<>(activeFilters);

        executor.execute(() -> {
            final double[] totals = new double[2];
            final Map<String, double[]> summary =
                    db.getFilteredAnalysis(accountId, snapshot, totals);
            final List<Transaction> txns =
                    db.getFilteredTransactions(accountId, snapshot);

            uiHandler.post(() -> {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                renderResults(summary, txns, totals);
            });
        });
    }

    // =========================================================
    // Render results
    // =========================================================

    private void renderResults(Map<String, double[]> summary,
                               List<Transaction> txns,
                               double[] totals) {
        if (txns.isEmpty()) {
            tvNoResults.setVisibility(View.VISIBLE);
            tvSummary  .setVisibility(View.GONE);
            return;
        }

        tvNoResults.setVisibility(View.GONE);

        // ---- Summary table ----
        addSummaryHeader();
        for (Map.Entry<String, double[]> e : summary.entrySet()) {
            addSummaryRow(e.getKey(), e.getValue()[0], e.getValue()[1]);
        }

        // ---- Divider between summary and individual txns ----
        addSectionLabel(llSummaryTable,
                txns.size() + " transaction" + (txns.size() == 1 ? "" : "s"));

        // ---- Individual transaction cards, grouped by date ----
        String lastDate = null;
        for (Transaction t : txns) {
            // Date group header
            if (!t.getDate().equals(lastDate)) {
                lastDate = t.getDate();
                addDateHeader(lastDate);
            }
            addTransactionCard(t);
        }

        // ---- Summary bar ----
        double net = totals[0] - totals[1];
        tvSummary.setText(String.format(
                "Total In: ₹%.2f   |   Spent: ₹%.2f   |   Net: %s₹%.2f",
                totals[0], totals[1],
                net < 0 ? "−" : "+", Math.abs(net)));
        tvSummary.setTextColor(net >= 0 ? 0xFF2ECC71 : 0xFFE74C3C);
        tvSummary.setVisibility(View.VISIBLE);
    }

    // =========================================================
    // Summary table row builders
    // =========================================================

    private void addSummaryHeader() {
        View row = buildTableRow();
        styleCell((TextView) row.findViewWithTag("c1"), "Date",     true);
        styleCell((TextView) row.findViewWithTag("c2"), "In (₹)",   true);
        styleCell((TextView) row.findViewWithTag("c3"), "Spent (₹)",true);
        row.setBackgroundColor(0xFFEEF3FF);
        llSummaryTable.addView(row);
    }

    private void addSummaryRow(String label, double credit, double debit) {
        View row = buildTableRow();
        styleCell((TextView) row.findViewWithTag("c1"), label, false);

        TextView tvCr = row.findViewWithTag("c2");
        tvCr.setText(credit > 0 ? String.format("%.2f", credit) : "—");
        tvCr.setTextColor(credit > 0 ? 0xFF2ECC71 : 0xFF999999);
        tvCr.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);

        TextView tvDr = row.findViewWithTag("c3");
        tvDr.setText(debit > 0 ? String.format("%.2f", debit) : "—");
        tvDr.setTextColor(debit > 0 ? 0xFFE74C3C : 0xFF999999);
        tvDr.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);

        llSummaryTable.addView(row);
    }

    private void addSectionLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(4);
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(0xFF555555);
        parent.addView(tv);
    }

    // =========================================================
    // Individual transaction card builders
    // =========================================================

    private void addDateHeader(String date) {
        // e.g.  "13/06/2025"  →  shown as-is (already user-friendly)
        TextView tv = new TextView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin    = dp(10);
        lp.bottomMargin = dp(4);
        tv.setLayoutParams(lp);
        tv.setText(date);
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(0xFF1a73e8);
        llTransactions.addView(tv);
    }

    private void addTransactionCard(Transaction t) {
        boolean isCredit = "credit".equals(t.getType());
        int     color    = isCredit ? 0xFF2ECC71 : 0xFFE74C3C;

        // Card wrapper
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(6);
        card.setLayoutParams(cardLp);
        card.setBackgroundColor(0xFFFFFFFF);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));

        // Colored stripe
        View stripe = new View(requireContext());
        LinearLayout.LayoutParams stripeLp =
                new LinearLayout.LayoutParams(dp(4), dp(36));
        stripeLp.rightMargin = dp(10);
        stripe.setLayoutParams(stripeLp);
        stripe.setBackgroundColor(color);
        card.addView(stripe);

        // Description + time column
        LinearLayout textCol = new LinearLayout(requireContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvDesc = new TextView(requireContext());
        String desc = (t.getDescription() == null || t.getDescription().isEmpty())
                ? "No description" : t.getDescription();
        tvDesc.setText(desc);
        tvDesc.setTextSize(13f);
        tvDesc.setTypeface(null, Typeface.BOLD);
        tvDesc.setTextColor(0xFF222222);
        tvDesc.setMaxLines(1);
        tvDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(tvDesc);

        TextView tvTime = new TextView(requireContext());
        tvTime.setText(t.getTime() != null ? t.getTime() : "");
        tvTime.setTextSize(11f);
        tvTime.setTextColor(0xFF999999);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeLp.topMargin = dp(2);
        tvTime.setLayoutParams(timeLp);
        textCol.addView(tvTime);

        card.addView(textCol);

        // Amount
        TextView tvAmount = new TextView(requireContext());
        tvAmount.setText((isCredit ? "+" : "−") + "₹" +
                String.format("%.2f", t.getAmount()));
        tvAmount.setTextSize(14f);
        tvAmount.setTypeface(null, Typeface.BOLD);
        tvAmount.setTextColor(color);
        LinearLayout.LayoutParams amtLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        amtLp.leftMargin = dp(8);
        tvAmount.setLayoutParams(amtLp);
        card.addView(tvAmount);

        // Bottom divider
        View divider = new View(requireContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFEEEEEE);

        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.addView(card);
        wrapper.addView(divider);

        llTransactions.addView(wrapper);
    }

    // =========================================================
    // Table row builders
    // =========================================================

    private View buildTableRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView c1 = makeCell(0, 1.6f); c1.setTag("c1"); row.addView(c1);
        TextView c2 = makeCell(0, 1.0f); c2.setTag("c2"); row.addView(c2);
        TextView c3 = makeCell(0, 1.0f); c3.setTag("c3"); row.addView(c3);

        View divider = new View(requireContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFEEEEEE);

        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.addView(row);
        wrapper.addView(divider);
        return wrapper;
    }

    private TextView makeCell(int width, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        TextView tv = new TextView(requireContext());
        tv.setLayoutParams(lp);
        tv.setTextSize(13f);
        tv.setTextColor(0xFF333333);
        return tv;
    }

    private void styleCell(TextView tv, String text, boolean bold) {
        tv.setText(text);
        tv.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        tv.setTextColor(0xFF333333);
    }

    // =========================================================
    // Date picker
    // =========================================================

    private void pickDate(boolean isFrom) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (dp, year, month, day) -> {
            String iso   = String.format("%04d-%02d-%02d", year, month + 1, day);
            String label = String.format("%02d/%02d/%04d", day, month + 1, year);
            if (isFrom) { pendingFrom = iso; tvFromDate.setText(label); }
            else        { pendingTo   = iso; tvToDate  .setText(label); }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void toast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}