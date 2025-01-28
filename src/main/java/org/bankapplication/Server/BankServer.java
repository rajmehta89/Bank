package org.bankapplication.Server;

import org.bankapplication.models.Account;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class BankServer {

    private static final Logger logger = LoggerFactory.getLogger(BankServer.class); // Logger setup

    private static final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z ]{3,30}$");
    private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("^[0-9]{10}$");

    // Executor service for Ping-Pong health check requests
    private static final ExecutorService pingPongExecutor = Executors.newCachedThreadPool();

    // Executor service for banking operations (Create, Withdraw, Deposit)
    private static final ExecutorService bankingExecutor = new ThreadPoolExecutor(
            5, // Core threads
            100, // Max threads
            60L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(), // Use a synchronous queue for task submission
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy() // Handles task overload by running the task in the caller thread
    );

    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket serverSocket = context.createSocket(ZMQ.ROUTER);
            serverSocket.bind("tcp://*:5555");

            logger.info("Server is waiting for client requests...");

            while (true) {
                // Receive client identity and message
                byte[] clientIdentity = serverSocket.recv(0); // Client identity
                byte[] message = serverSocket.recv(0);
                String request = new String(message, ZMQ.CHARSET);

                // Log the received message
                logger.info("[REQUEST RECEIVED] Client Identity: " + new String(clientIdentity, ZMQ.CHARSET));
                logger.info("Request Message: " + request);

                // If it's a ping, use pingPongExecutor, else process banking operation in bankingExecutor
                if ("PING".equals(request)) {
                    pingPongExecutor.submit(() -> handlePing(serverSocket, clientIdentity));
                } else {
                    bankingExecutor.submit(() -> handleClientRequest(serverSocket, clientIdentity, request));
                }
            }
        }
    }

    // Method to handle Ping requests (Health Check)
    private static void handlePing(ZMQ.Socket serverSocket, byte[] clientIdentity) {
        String response = "PONG";
        logger.info("[ACTION] Ping received. Sending PONG.");

        // Send the response back to the specific client
        serverSocket.send(clientIdentity, ZMQ.SNDMORE);
        serverSocket.send(response.getBytes(ZMQ.CHARSET), 0);
    }

    // Method to handle Client Requests for banking operations
    private static void handleClientRequest(ZMQ.Socket serverSocket, byte[] clientIdentity, String request) {
        String response = processRequest(request);

        // Log the response before sending it
        logger.info("[RESPONSE SENT] Response: " + response);

        // Send the response back to the specific client
        serverSocket.send(clientIdentity, ZMQ.SNDMORE);
        serverSocket.send(response.getBytes(ZMQ.CHARSET), 0);
    }

    private static String processRequest(String request) {
        String[] parts = request.split(";");
        String operation = parts[0];

        switch (operation) {
            case "CREATE":
                return createAccount(parts);
            case "BALANCE":
                return checkBalance(parts);
            case "DEPOSIT":
                return depositMoney(parts);
            case "WITHDRAW":
                return withdrawMoney(parts);
            default:
                return "Invalid operation.";
        }
    }

    private static String createAccount(String[] parts) {
        if (parts.length != 4) return "Invalid CREATE request format.";

        String accountId = parts[1];
        String holderName = parts[2];
        int initialBalance;

        try {
            initialBalance = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return "Initial balance must be a valid integer.";
        }

        if (!ACCOUNT_ID_PATTERN.matcher(accountId).matches()) {
            return "Account ID must be a 10-digit number.";
        }

        if (!NAME_PATTERN.matcher(holderName).matches()) {
            return "Name must be 3-30 characters long and contain only letters and spaces.";
        }

        if (accounts.putIfAbsent(accountId, new Account(accountId, holderName, initialBalance)) == null) {
            return "Account created successfully for " + holderName + " with ID: " + accountId;
        } else {
            return "Account with ID " + accountId + " already exists.";
        }
    }

    private static String checkBalance(String[] parts) {
        if (parts.length != 2) return "Invalid BALANCE request format.";

        String accountId = parts[1];
        Account account = accounts.get(accountId);

        if (account != null) {
            return "Account ID: " + accountId + " - Balance: " + account.getBalance();
        } else {
            return "Account ID " + accountId + " does not exist.";
        }
    }

    private static String depositMoney(String[] parts) {
        if (parts.length != 3) return "Invalid DEPOSIT request format.";

        String accountId = parts[1];
        int amount;

        try {
            amount = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return "Deposit amount must be a valid integer.";
        }

        Account account = accounts.get(accountId);

        if (account != null) {
            account.deposit(amount);
            return "Deposited " + amount + " to account " + accountId + ". New Balance: " + account.getBalance();
        } else {
            return "Account ID " + accountId + " does not exist.";
        }
    }

    private static String withdrawMoney(String[] parts) {
        if (parts.length != 3) return "Invalid WITHDRAW request format.";

        String accountId = parts[1];
        int amount;

        try {
            amount = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return "Withdrawal amount must be a valid integer.";
        }

        Account account = accounts.get(accountId);

        if (account != null) {
            if (account.withdraw(amount)) {
                return "Withdrawn " + amount + " from account " + accountId + ". New Balance: " + account.getBalance();
            } else {
                return "Insufficient balance in account " + accountId + ".";
            }
        } else {
            return "Account ID " + accountId + " does not exist.";
        }
    }
}
