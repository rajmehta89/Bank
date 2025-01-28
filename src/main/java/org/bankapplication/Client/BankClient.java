package org.bankapplication.Client;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.Scanner;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class BankClient {

    private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("^[0-9]{10}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z ]{3,30}$");


    private static final ExecutorService taskExecutor = new ThreadPoolExecutor(
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
            // Create a socket for communication with the server
            final ZMQ.Socket clientSocket = context.createSocket(ZMQ.DEALER); // Declare final
            clientSocket.connect("tcp://localhost:5555");

            System.out.println("Connected to the server...");

            // Create the ExecutorService for both tasks
            ExecutorService pingPongExecutor = Executors.newCachedThreadPool();


            // Start the ping-pong check in a separate thread using a helper method
            pingPongExecutor.submit(() -> checkPing(clientSocket));

            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.println("\nChoose an operation:");
                System.out.println("1. Create Account");
                System.out.println("2. Check Balance");
                System.out.println("3. Deposit Money");
                System.out.println("4. Withdraw Money");
                System.out.println("5. Exit");
                System.out.print("Enter your choice: ");

                int choice;
                try {
                    choice = Integer.parseInt(scanner.nextLine());
                } catch (NumberFormatException e) {
                    System.out.println("Error: Please enter a valid number.");
                    continue;
                }

                String request = "";

                switch (choice) {
                    case 1:
                        request = handleCreateAccount(scanner);
                        break;
                    case 2:
                        request = handleCheckBalance(scanner);
                        break;
                    case 3:
                        request = handleDeposit(scanner);
                        break;
                    case 4:
                        request = handleWithdraw(scanner);
                        break;
                    case 5:
                        System.out.println("Exiting...");
                        pingPongExecutor.shutdown();
                        taskExecutor.shutdown();
                        return;
                    default:
                        System.out.println("Invalid choice. Please try again.");
                }

                if (!request.isEmpty()) {
                    // Submit the task for banking operations in a separate thread
                    final String finalRequest = request;  // Effectively final
                    taskExecutor.submit(() -> sendRequest(clientSocket, finalRequest));
                }
            }
        }
    }

    // Helper method to handle the ping operation
    private static void checkPing(ZMQ.Socket clientSocket) {
        if (!pingServer(clientSocket)) {
            System.out.println("Failed to connect to the server. Exiting...");
            return;
        }
    }

    // Ping the server and check if it responds with "PONG"
    private static boolean pingServer(ZMQ.Socket clientSocket) {
        clientSocket.send("PING");
        String response = clientSocket.recvStr();
        if ("PONG".equals(response)) {
            System.out.println("Server is up and running.");
            return true;
        }
        return false;
    }

    // Send a request to the server
    private static void sendRequest(ZMQ.Socket clientSocket, String request) {
        clientSocket.send(request);
        String response = clientSocket.recvStr();
        System.out.println(response);
    }

    // Handle account creation logic
    private static String handleCreateAccount(Scanner scanner) {
        String accountId, holderName;
        int initialBalance;

        while (true) {
            System.out.print("Enter Account ID: ");
            accountId = scanner.nextLine();
            if (!ACCOUNT_ID_PATTERN.matcher(accountId).matches()) {
                System.out.println("Error: Account ID must be a 10-digit number.");
                continue;
            }
            break;
        }

        while (true) {
            System.out.print("Enter Account Holder Name: ");
            holderName = scanner.nextLine();
            if (!NAME_PATTERN.matcher(holderName).matches()) {
                System.out.println("Error: Name must be 3-30 characters long and contain only letters and spaces.");
                continue;
            }
            break;
        }

        while (true) {
            System.out.print("Enter Initial Balance: ");
            try {
                initialBalance = Integer.parseInt(scanner.nextLine());
                if (initialBalance < 0) {
                    System.out.println("Error: Initial balance must be a positive number.");
                    continue;
                }
            } catch (NumberFormatException e) {
                System.out.println("Error: Please enter a valid integer for balance.");
                continue;
            }
            break;
        }

        return "CREATE;" + accountId + ";" + holderName + ";" + initialBalance;
    }

    // Handle checking balance logic
    private static String handleCheckBalance(Scanner scanner) {
        String accountId;

        while (true) {
            System.out.print("Enter Account ID: ");
            accountId = scanner.nextLine();
            if (!ACCOUNT_ID_PATTERN.matcher(accountId).matches()) {
                System.out.println("Error: Account ID must be a 10-digit number.");
                continue;
            }
            break;
        }

        return "BALANCE;" + accountId;
    }

    // Handle deposit logic
    private static String handleDeposit(Scanner scanner) {
        String accountId;
        int amount;

        while (true) {
            System.out.print("Enter Account ID: ");
            accountId = scanner.nextLine();
            if (!ACCOUNT_ID_PATTERN.matcher(accountId).matches()) {
                System.out.println("Error: Account ID must be a 10-digit number.");
                continue;
            }
            break;
        }

        while (true) {
            System.out.print("Enter Amount to Deposit: ");
            try {
                amount = Integer.parseInt(scanner.nextLine());
                if (amount <= 0) {
                    System.out.println("Error: Deposit amount must be a positive number.");
                    continue;
                }
            } catch (NumberFormatException e) {
                System.out.println("Error: Please enter a valid integer for deposit amount.");
                continue;
            }
            break;
        }

        return "DEPOSIT;" + accountId + ";" + amount;
    }

    // Handle withdraw logic
    private static String handleWithdraw(Scanner scanner) {
        String accountId;
        int amount;

        while (true) {
            System.out.print("Enter Account ID: ");
            accountId = scanner.nextLine();
            if (!ACCOUNT_ID_PATTERN.matcher(accountId).matches()) {
                System.out.println("Error: Account ID must be a 10-digit number.");
                continue;
            }
            break;
        }

        while (true) {
            System.out.print("Enter Amount to Withdraw: ");
            try {
                amount = Integer.parseInt(scanner.nextLine());
                if (amount <= 0) {
                    System.out.println("Error: Withdrawal amount must be a positive number.");
                    continue;
                }
            } catch (NumberFormatException e) {
                System.out.println("Error: Please enter a valid integer for withdrawal amount.");
                continue;
            }
            break;
        }

        return "WITHDRAW;" + accountId + ";" + amount;
    }
}
