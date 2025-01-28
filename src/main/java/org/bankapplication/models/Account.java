package org.bankapplication.models;


public class Account {
    private final String accountId;
    private final String holderName;
    private int balance;

    public Account(String accountId, String holderName, int balance) {
        this.accountId = accountId;
        this.holderName = holderName;
        this.balance = balance;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getHolderName() {
        return holderName;
    }

    public synchronized int getBalance() {
        return balance;
    }

    public synchronized void deposit(int amount) {
        balance += amount;
    }

    public synchronized boolean withdraw(int amount) {
        if (amount <= balance) {
            balance -= amount;
            return true;
        }
        return false;
    }
}
