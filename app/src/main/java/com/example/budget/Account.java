package com.example.budget;

public class Account {
    private long id;
    private String name;
    private double balance;

    public Account(long id, String name, double balance) {
        this.id = id;
        this.name = name;
        this.balance = balance;
    }

    public long getId()         { return id; }
    public String getName()     { return name; }
    public double getBalance()  { return balance; }
    public void setName(String name)       { this.name = name; }
    public void setBalance(double balance) { this.balance = balance; }
}
