package com.example.budget;

public class Transaction {
    private long id;
    private long accountId;
    private String type;        // "credit" | "debit"
    private double amount;
    private String description;
    private String date;
    private String time;

    public Transaction(long id, long accountId, String type, double amount,
                       String description, String date, String time) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.date = date;
        this.time = time;
    }

    public long   getId()          { return id; }
    public long   getAccountId()   { return accountId; }
    public String getType()        { return type; }
    public double getAmount()      { return amount; }
    public String getDescription() { return description; }
    public String getDate()        { return date; }
    public String getTime()        { return time; }
}
