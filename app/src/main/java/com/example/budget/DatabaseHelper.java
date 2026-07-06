package com.example.budget;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "budget.db";
    private static final int    DB_VERSION = 1;

    private static final String TABLE_ACC = "accounts";
    private static final String A_ID      = "id";
    private static final String A_NAME    = "name";
    private static final String A_BALANCE = "balance";

    private static final String TABLE_TXN = "transactions";
    private static final String T_ID      = "id";
    private static final String T_ACC_ID  = "account_id";
    private static final String T_TYPE    = "type";
    private static final String T_AMOUNT  = "amount";
    private static final String T_DESC    = "description";
    private static final String T_DATE    = "date";   // stored as "dd/MM/yyyy"
    private static final String T_TIME    = "time";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_ACC + "("
                + A_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + A_NAME + " TEXT NOT NULL,"
                + A_BALANCE + " REAL DEFAULT 0)");

        db.execSQL("CREATE TABLE " + TABLE_TXN + "("
                + T_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + T_ACC_ID + " INTEGER NOT NULL,"
                + T_TYPE + " TEXT NOT NULL,"
                + T_AMOUNT + " REAL NOT NULL,"
                + T_DESC + " TEXT DEFAULT '',"
                + T_DATE + " TEXT,"
                + T_TIME + " TEXT,"
                + "FOREIGN KEY(" + T_ACC_ID + ") REFERENCES " + TABLE_ACC + "(" + A_ID + "))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TXN);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACC);
        onCreate(db);
    }

    /* =========================================================
       ACCOUNT OPERATIONS
       ========================================================= */

    public long addAccount(String name) {
        ContentValues cv = new ContentValues();
        cv.put(A_NAME, name);
        cv.put(A_BALANCE, 0.0);
        return getWritableDatabase().insert(TABLE_ACC, null, cv);
    }

    /** Renames an existing account. No-op if the account doesn't exist. */
    public void renameAccount(long id, String newName) {
        ContentValues cv = new ContentValues();
        cv.put(A_NAME, newName);
        getWritableDatabase().update(TABLE_ACC, cv, A_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public Account findAccountByName(String name) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE_ACC
                        + " WHERE LOWER(" + A_NAME + ") = LOWER(?)"
                        + " LIMIT 1",
                new String[]{name});
        Account account = null;
        if (c.moveToFirst()) {
            account = new Account(
                    c.getLong(c.getColumnIndexOrThrow(A_ID)),
                    c.getString(c.getColumnIndexOrThrow(A_NAME)),
                    c.getDouble(c.getColumnIndexOrThrow(A_BALANCE)));
        }
        c.close();
        return account;
    }

    public long copyAccount(Account source) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues accCv = new ContentValues();
            accCv.put(A_NAME,    source.getName());
            accCv.put(A_BALANCE, source.getBalance());
            long newAccountId = db.insert(TABLE_ACC, null, accCv);

            Cursor c = db.rawQuery(
                    "SELECT * FROM " + TABLE_TXN
                            + " WHERE " + T_ACC_ID + " = ?"
                            + " ORDER BY " + T_ID + " ASC",
                    new String[]{String.valueOf(source.getId())});

            while (c.moveToNext()) {
                ContentValues txnCv = new ContentValues();
                txnCv.put(T_ACC_ID, newAccountId);
                txnCv.put(T_TYPE,   c.getString(c.getColumnIndexOrThrow(T_TYPE)));
                txnCv.put(T_AMOUNT, c.getDouble(c.getColumnIndexOrThrow(T_AMOUNT)));
                txnCv.put(T_DESC,   c.getString(c.getColumnIndexOrThrow(T_DESC)));
                txnCv.put(T_DATE,   c.getString(c.getColumnIndexOrThrow(T_DATE)));
                txnCv.put(T_TIME,   c.getString(c.getColumnIndexOrThrow(T_TIME)));
                db.insert(TABLE_TXN, null, txnCv);
            }
            c.close();

            db.setTransactionSuccessful();
            return newAccountId;
        } finally {
            db.endTransaction();
        }
    }

    public void deleteAccounts(List<Long> ids) {
        SQLiteDatabase db = getWritableDatabase();
        for (long id : ids) {
            db.delete(TABLE_TXN, T_ACC_ID + "=?", new String[]{String.valueOf(id)});
            db.delete(TABLE_ACC, A_ID + "=?",     new String[]{String.valueOf(id)});
        }
    }

    public List<Account> getAllAccounts() {
        List<Account> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE_ACC + " ORDER BY " + A_ID + " DESC", null);
        while (c.moveToNext()) {
            list.add(new Account(
                    c.getLong(c.getColumnIndexOrThrow(A_ID)),
                    c.getString(c.getColumnIndexOrThrow(A_NAME)),
                    c.getDouble(c.getColumnIndexOrThrow(A_BALANCE))));
        }
        c.close();
        return list;
    }

    public Account getAccount(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE_ACC + " WHERE " + A_ID + "=?",
                new String[]{String.valueOf(id)});
        Account account = null;
        if (c.moveToFirst()) {
            account = new Account(
                    c.getLong(c.getColumnIndexOrThrow(A_ID)),
                    c.getString(c.getColumnIndexOrThrow(A_NAME)),
                    c.getDouble(c.getColumnIndexOrThrow(A_BALANCE)));
        }
        c.close();
        return account;
    }

    /* =========================================================
       TRANSFERS
       ========================================================= */

    /**
     * Moves money from one account to another as a linked pair of
     * transactions (a debit on the source, a credit on the destination),
     * updating both balances atomically.
     */
    public void transferBetweenAccounts(long fromAccountId, long toAccountId, double amount,
                                        String desc, String date, String time) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String fromName = getAccountNameInternal(db, fromAccountId);
            String toName   = getAccountNameInternal(db, toAccountId);
            String baseDesc = (desc == null || desc.trim().isEmpty()) ? "Transfer" : desc.trim();

            ContentValues cvOut = new ContentValues();
            cvOut.put(T_ACC_ID, fromAccountId);
            cvOut.put(T_TYPE,   "debit");
            cvOut.put(T_AMOUNT, amount);
            cvOut.put(T_DESC,   baseDesc + " (to " + toName + ")");
            cvOut.put(T_DATE,   date);
            cvOut.put(T_TIME,   time);
            db.insert(TABLE_TXN, null, cvOut);

            ContentValues cvIn = new ContentValues();
            cvIn.put(T_ACC_ID, toAccountId);
            cvIn.put(T_TYPE,   "credit");
            cvIn.put(T_AMOUNT, amount);
            cvIn.put(T_DESC,   baseDesc + " (from " + fromName + ")");
            cvIn.put(T_DATE,   date);
            cvIn.put(T_TIME,   time);
            db.insert(TABLE_TXN, null, cvIn);

            db.execSQL("UPDATE " + TABLE_ACC + " SET " + A_BALANCE + " = " + A_BALANCE
                            + " - ? WHERE " + A_ID + " = ?",
                    new Object[]{amount, fromAccountId});
            db.execSQL("UPDATE " + TABLE_ACC + " SET " + A_BALANCE + " = " + A_BALANCE
                            + " + ? WHERE " + A_ID + " = ?",
                    new Object[]{amount, toAccountId});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private String getAccountNameInternal(SQLiteDatabase db, long id) {
        Cursor c = db.rawQuery(
                "SELECT " + A_NAME + " FROM " + TABLE_ACC + " WHERE " + A_ID + "=?",
                new String[]{String.valueOf(id)});
        String name = "account";
        if (c.moveToFirst()) name = c.getString(0);
        c.close();
        return name;
    }

    /* =========================================================
       ANALYTICS
       =========================================================
       Dates are stored as "dd/MM/yyyy". SQLite has no native date
       type for that format, so we convert to "yyyy-MM-dd" inside
       the query using substr for correct lexicographic ordering
       and comparison.

       Stored  : "13/06/2025"
       Converted: substr(date,7,4)||'-'||substr(date,4,2)||'-'||substr(date,1,2)
                  → "2025-06-13"
       ========================================================= */

    /** date_iso expression reusable in SQL */
    private static final String DATE_ISO =
            "substr(" + T_DATE + ",7,4)||'-'||substr(" + T_DATE + ",4,2)||'-'||substr(" + T_DATE + ",1,2)";

    /** month_key: "2025-06" */
    private static final String MONTH_KEY =
            "substr(" + T_DATE + ",7,4)||'-'||substr(" + T_DATE + ",4,2)";

    /** day_key: "2025-06-13" (same as DATE_ISO) */
    private static final String DAY_KEY = DATE_ISO;

    // ---- Summary (total credit / total debit) for an account ----

    public double[] getTotalCreditDebit(long accountId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT type, SUM(amount) FROM " + TABLE_TXN
                        + " WHERE " + T_ACC_ID + "=?"
                        + " GROUP BY type",
                new String[]{String.valueOf(accountId)});
        double credit = 0, debit = 0;
        while (c.moveToNext()) {
            String type = c.getString(0);
            double sum  = c.getDouble(1);
            if ("credit".equals(type)) credit = sum;
            else                       debit  = sum;
        }
        c.close();
        return new double[]{credit, debit};
    }

    // ---- Monthly breakdown: month label → {credit, debit} ----

    /**
     * Returns an ordered map of "MMM yyyy" → double[]{credit, debit},
     * sorted newest-first.
     */
    public Map<String, double[]> getMonthlyAnalysis(long accountId) {
        // We group by the ISO month key and order descending
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + MONTH_KEY + " AS mk,"
                        + " SUM(CASE WHEN type='credit' THEN amount ELSE 0 END) AS cr,"
                        + " SUM(CASE WHEN type='debit'  THEN amount ELSE 0 END) AS dr"
                        + " FROM " + TABLE_TXN
                        + " WHERE " + T_ACC_ID + "=?"
                        + " GROUP BY mk"
                        + " ORDER BY mk DESC",
                new String[]{String.valueOf(accountId)});

        Map<String, double[]> map = new LinkedHashMap<>();
        while (c.moveToNext()) {
            String mk = c.getString(0);           // "2025-06"
            double cr = c.getDouble(1);
            double dr = c.getDouble(2);
            map.put(isoMonthToLabel(mk), new double[]{cr, dr});
        }
        c.close();
        return map;
    }

    // ---- Daily breakdown for a given month ("yyyy-MM") ----

    /**
     * Returns an ordered map of "dd MMM" → double[]{credit, debit},
     * newest-first, filtered to the given month key "yyyy-MM".
     */
    public Map<String, double[]> getDailyAnalysisForMonth(long accountId, String isoMonth) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + DAY_KEY + " AS dk,"
                        + " SUM(CASE WHEN type='credit' THEN amount ELSE 0 END) AS cr,"
                        + " SUM(CASE WHEN type='debit'  THEN amount ELSE 0 END) AS dr"
                        + " FROM " + TABLE_TXN
                        + " WHERE " + T_ACC_ID + "=?"
                        + "   AND " + MONTH_KEY + " = ?"
                        + " GROUP BY dk"
                        + " ORDER BY dk DESC",
                new String[]{String.valueOf(accountId), isoMonth});

        Map<String, double[]> map = new LinkedHashMap<>();
        while (c.moveToNext()) {
            String dk = c.getString(0);           // "2025-06-13"
            double cr = c.getDouble(1);
            double dr = c.getDouble(2);
            map.put(isoDayToLabel(dk), new double[]{cr, dr});
        }
        c.close();
        return map;
    }

    // ---- Custom date-range analysis ----

    /**
     * Both fromDate and toDate are "dd/MM/yyyy".
     * Returns ordered map day-label → double[]{credit, debit}, newest-first.
     */
    public Map<String, double[]> getCustomRangeAnalysis(long accountId,
                                                         String fromDate, String toDate) {
        // Convert user-supplied "dd/MM/yyyy" to ISO for comparison
        String fromIso = ddMMyyyyToIso(fromDate);
        String toIso   = ddMMyyyyToIso(toDate);

        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + DAY_KEY + " AS dk,"
                        + " SUM(CASE WHEN type='credit' THEN amount ELSE 0 END) AS cr,"
                        + " SUM(CASE WHEN type='debit'  THEN amount ELSE 0 END) AS dr"
                        + " FROM " + TABLE_TXN
                        + " WHERE " + T_ACC_ID + "=?"
                        + "   AND " + DATE_ISO + " >= ?"
                        + "   AND " + DATE_ISO + " <= ?"
                        + " GROUP BY dk"
                        + " ORDER BY dk DESC",
                new String[]{String.valueOf(accountId), fromIso, toIso});

        Map<String, double[]> map = new LinkedHashMap<>();
        while (c.moveToNext()) {
            String dk = c.getString(0);
            double cr = c.getDouble(1);
            double dr = c.getDouble(2);
            map.put(isoDayToLabel(dk), new double[]{cr, dr});
        }
        c.close();
        return map;
    }

    // ---- Custom range totals (single credit/debit pair) ----

    public double[] getCustomRangeTotals(long accountId, String fromDate, String toDate) {
        String fromIso = ddMMyyyyToIso(fromDate);
        String toIso   = ddMMyyyyToIso(toDate);

        Cursor c = getReadableDatabase().rawQuery(
                "SELECT type, SUM(amount) FROM " + TABLE_TXN
                        + " WHERE " + T_ACC_ID + "=?"
                        + "   AND " + DATE_ISO + " >= ?"
                        + "   AND " + DATE_ISO + " <= ?"
                        + " GROUP BY type",
                new String[]{String.valueOf(accountId), fromIso, toIso});

        double credit = 0, debit = 0;
        while (c.moveToNext()) {
            String type = c.getString(0);
            double sum  = c.getDouble(1);
            if ("credit".equals(type)) credit = sum;
            else                       debit  = sum;
        }
        c.close();
        return new double[]{credit, debit};
    }

    // ---- Stacked-filter analysis (arbitrary combination of TransactionFilter) ----

    /**
     * Applies every filter in {@code filters} (AND-ed together) to this account's
     * transactions and returns a day-by-day breakdown, newest-first — same shape as
     * {@link #getCustomRangeAnalysis}. The account-level totals across the filtered
     * set are written into {@code totalsOut[0]} (credit) and {@code totalsOut[1]}
     * (debit); pass a {@code double[2]} and read it after the call.
     *
     * An empty or null filter list just returns the account's full history.
     */
    public Map<String, double[]> getFilteredAnalysis(long accountId,
                                                      List<TransactionFilter> filters,
                                                      double[] totalsOut) {
        StringBuilder where = new StringBuilder(T_ACC_ID + "=?");
        List<String> args = new ArrayList<>();
        buildFilterWhereClause(accountId, filters, where, args);

        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + DAY_KEY + " AS dk,"
                        + " SUM(CASE WHEN type='credit' THEN amount ELSE 0 END) AS cr,"
                        + " SUM(CASE WHEN type='debit'  THEN amount ELSE 0 END) AS dr"
                        + " FROM " + TABLE_TXN
                        + " WHERE " + where
                        + " GROUP BY dk"
                        + " ORDER BY dk DESC",
                args.toArray(new String[0]));

        Map<String, double[]> map = new LinkedHashMap<>();
        double totalCredit = 0, totalDebit = 0;
        while (c.moveToNext()) {
            String dk = c.getString(0);
            double cr = c.getDouble(1);
            double dr = c.getDouble(2);
            map.put(isoDayToLabel(dk), new double[]{cr, dr});
            totalCredit += cr;
            totalDebit  += dr;
        }
        c.close();

        if (totalsOut != null && totalsOut.length >= 2) {
            totalsOut[0] = totalCredit;
            totalsOut[1] = totalDebit;
        }
        return map;
    }

    /**
     * Same filter stack as {@link #getFilteredAnalysis}, but returns the matching
     * transactions themselves (newest-first) rather than a day-grouped summary —
     * e.g. for rendering the actual filtered transaction list in the UI.
     */
    public List<Transaction> getFilteredTransactions(long accountId,
                                                      List<TransactionFilter> filters) {
        StringBuilder where = new StringBuilder(T_ACC_ID + "=?");
        List<String> args = new ArrayList<>();
        buildFilterWhereClause(accountId, filters, where, args);

        List<Transaction> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE_TXN
                        + " WHERE " + where
                        + " ORDER BY " + T_ID + " DESC",
                args.toArray(new String[0]));
        while (c.moveToNext()) {
            list.add(new Transaction(
                    c.getLong(c.getColumnIndexOrThrow(T_ID)),
                    accountId,
                    c.getString(c.getColumnIndexOrThrow(T_TYPE)),
                    c.getDouble(c.getColumnIndexOrThrow(T_AMOUNT)),
                    c.getString(c.getColumnIndexOrThrow(T_DESC)),
                    c.getString(c.getColumnIndexOrThrow(T_DATE)),
                    c.getString(c.getColumnIndexOrThrow(T_TIME))));
        }
        c.close();
        return list;
    }

    /** Shared WHERE-clause builder for the filter-stack queries above. */
    private void buildFilterWhereClause(long accountId, List<TransactionFilter> filters,
                                        StringBuilder where, List<String> args) {
        args.add(String.valueOf(accountId));

        if (filters != null) {
            for (TransactionFilter f : filters) {
                switch (f.type) {
                    case DATE_RANGE:
                        where.append(" AND ").append(DATE_ISO).append(" >= ?")
                             .append(" AND ").append(DATE_ISO).append(" <= ?");
                        args.add(f.fromIso);
                        args.add(f.toIso);
                        break;

                    case DESCRIPTION:
                        where.append(" AND LOWER(").append(T_DESC).append(") ")
                             .append(f.excludeKeyword ? "NOT LIKE ?" : "LIKE ?");
                        args.add("%" + f.keyword.toLowerCase(Locale.ROOT) + "%");
                        break;

                    case TXN_TYPE:
                        where.append(" AND ").append(T_TYPE).append(" = ?");
                        args.add(f.txnType);
                        break;

                    case AMOUNT_RANGE:
                        where.append(" AND ").append(T_AMOUNT).append(" >= ?")
                             .append(" AND ").append(T_AMOUNT).append(" <= ?");
                        args.add(String.valueOf(f.minAmount));
                        args.add(String.valueOf(f.maxAmount));
                        break;
                }
            }
        }
    }

    /* =========================================================
       TRANSACTION OPERATIONS
       ========================================================= */

    public void addTransaction(long accountId, String type, double amount,
                               String desc, String date, String time) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put(T_ACC_ID, accountId);
            cv.put(T_TYPE,   type);
            cv.put(T_AMOUNT, amount);
            cv.put(T_DESC,   desc);
            cv.put(T_DATE,   date);
            cv.put(T_TIME,   time);
            db.insert(TABLE_TXN, null, cv);

            String op = type.equals("credit") ? "+" : "-";
            db.execSQL("UPDATE " + TABLE_ACC + " SET " + A_BALANCE + " = " + A_BALANCE
                    + " " + op + " ? WHERE " + A_ID + " = ?",
                    new Object[]{amount, accountId});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void updateTransaction(long txnId, long accountId,
                                  String oldType, double oldAmount,
                                  String newType, double newAmount,
                                  String desc, String date, String time) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String reverseOp = oldType.equals("credit") ? "-" : "+";
            db.execSQL("UPDATE " + TABLE_ACC + " SET " + A_BALANCE + " = " + A_BALANCE
                    + " " + reverseOp + " ? WHERE " + A_ID + " = ?",
                    new Object[]{oldAmount, accountId});

            String applyOp = newType.equals("credit") ? "+" : "-";
            db.execSQL("UPDATE " + TABLE_ACC + " SET " + A_BALANCE + " = " + A_BALANCE
                    + " " + applyOp + " ? WHERE " + A_ID + " = ?",
                    new Object[]{newAmount, accountId});

            ContentValues cv = new ContentValues();
            cv.put(T_TYPE,   newType);
            cv.put(T_AMOUNT, newAmount);
            cv.put(T_DESC,   desc);
            cv.put(T_DATE,   date);
            cv.put(T_TIME,   time);
            db.update(TABLE_TXN, cv, T_ID + "=?", new String[]{String.valueOf(txnId)});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void deleteTransactions(List<Long> txnIds, long accountId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (long txnId : txnIds) {
                Cursor c = db.rawQuery(
                        "SELECT " + T_TYPE + ", " + T_AMOUNT + " FROM " + TABLE_TXN
                                + " WHERE " + T_ID + "=?",
                        new String[]{String.valueOf(txnId)});
                if (c.moveToFirst()) {
                    String type   = c.getString(0);
                    double amount = c.getDouble(1);
                    String op = type.equals("credit") ? "-" : "+";
                    db.execSQL("UPDATE " + TABLE_ACC + " SET " + A_BALANCE + " = " + A_BALANCE
                            + " " + op + " ? WHERE " + A_ID + " = ?",
                            new Object[]{amount, accountId});
                }
                c.close();
                db.delete(TABLE_TXN, T_ID + "=?", new String[]{String.valueOf(txnId)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Transaction> getTransactions(long accountId, int offset, int limit) {
        List<Transaction> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE_TXN
                        + " WHERE " + T_ACC_ID + "=?"
                        + " ORDER BY " + T_ID + " DESC"
                        + " LIMIT ? OFFSET ?",
                new String[]{String.valueOf(accountId),
                        String.valueOf(limit),
                        String.valueOf(offset)});
        while (c.moveToNext()) {
            list.add(new Transaction(
                    c.getLong(c.getColumnIndexOrThrow(T_ID)),
                    accountId,
                    c.getString(c.getColumnIndexOrThrow(T_TYPE)),
                    c.getDouble(c.getColumnIndexOrThrow(T_AMOUNT)),
                    c.getString(c.getColumnIndexOrThrow(T_DESC)),
                    c.getString(c.getColumnIndexOrThrow(T_DATE)),
                    c.getString(c.getColumnIndexOrThrow(T_TIME))));
        }
        c.close();
        return list;
    }

    /* =========================================================
       Date helpers
       ========================================================= */

    /** "dd/MM/yyyy" → "yyyy-MM-dd" */
    private static String ddMMyyyyToIso(String d) {
        if (d == null || d.length() < 10) return "";
        // e.g. "13/06/2025" → "2025-06-13"
        return d.substring(6, 10) + "-" + d.substring(3, 5) + "-" + d.substring(0, 2);
    }

    private static final String[] MONTHS = {
            "Jan","Feb","Mar","Apr","May","Jun",
            "Jul","Aug","Sep","Oct","Nov","Dec"
    };

    /** "2025-06" → "Jun 2025" */
    private static String isoMonthToLabel(String mk) {
        if (mk == null || mk.length() < 7) return mk;
        int m = Integer.parseInt(mk.substring(5, 7));
        return MONTHS[m - 1] + " " + mk.substring(0, 4);
    }

    /** "2025-06-13" → "13 Jun 2025" */
    private static String isoDayToLabel(String dk) {
        if (dk == null || dk.length() < 10) return dk;
        int m = Integer.parseInt(dk.substring(5, 7));
        return dk.substring(8, 10) + " " + MONTHS[m - 1] + " " + dk.substring(0, 4);
    }
}
