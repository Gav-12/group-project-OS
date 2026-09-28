import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Scanner;

public class Client {
    //set ip & port
    public static final String SERVER_IP = "127.0.0.1";
    public static final int PORT = 8080;

    // ---------- TUI helpers (colors + boxes) ----------
    // Colors are turned off with:  java Client 1 --no-color   (for terminals that show weird symbols)
    private static boolean useColor = true;
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final int WIDTH = 60;

    private static String c(String color, String text) {
        return useColor ? color + text + RESET : text;
    }

    private static String repeat(char ch, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(ch);
        return sb.toString();
    }

    private static String pad(String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        return text + repeat(' ', width - text.length());
    }

    private static void boxTop(String title) {
        String t = " " + title + " ";
        System.out.println(c(CYAN, "+-" + t + repeat('-', WIDTH - t.length() - 1) + "+"));
    }

    private static void boxLine(String text, String color) {
        String body = pad(text, WIDTH - 2);
        System.out.println(c(CYAN, "|") + " " + (color == null ? body : c(color, body)) + " " + c(CYAN, "|"));
    }

    private static void boxBottom() {
        System.out.println(c(CYAN, "+" + repeat('-', WIDTH) + "+"));
    }

    private static void printBanner(String clientId) {
        System.out.println();
        boxTop("RESOURCE RESERVATION SYSTEM");
        boxLine("Logged in as: " + clientId, BOLD);
        boxLine("Server: " + SERVER_IP + ":" + PORT, null);
        boxBottom();
    }

    private static void printMenu() {
        System.out.println();
        boxTop("MENU");
        boxLine("[1] LIST      - show all resources", null);
        boxLine("[2] STATUS    - check one resource", null);
        boxLine("[3] RESERVE   - reserve a resource", null);
        boxLine("[4] CANCEL    - cancel your reservation", null);
        boxLine("[5] SHOOT     - stress test (1,500 clients)", null);
        boxLine("[0] QUIT", null);
        boxLine("You can also type a command, e.g.  RESERVE 10", YELLOW);
        boxBottom();
    }

    private static void printResult(String title, String sent, List<String> lines) {
        System.out.println();
        boxTop(title);
        boxLine("> " + sent, YELLOW);
        if (lines.isEmpty()) {
            boxLine("(no response from server)", RED);
        }
        for (String l : lines) {
            String upper = l.toUpperCase();
            String color = null;
            if (upper.contains("SUCCESS")) color = GREEN;
            else if (upper.contains("ERROR") || upper.contains("FAIL")) color = RED;
            boxLine(l, color);
        }
        boxBottom();
    }

    private static void printError(String message) {
        System.out.println(c(RED, " ! " + message));
    }

    private static String ask(Scanner scanner, String prompt) {
        System.out.print(c(BOLD, " " + prompt + ": "));
        return scanner.nextLine().trim();
    }

    // ---------- main ----------
    public static void main(String[] args) throws InterruptedException {

        //warning error cmd argument
        if (args.length < 1) {
            System.out.println("Usage: java Client <Client_Number> [--no-color]");
            System.out.println("Examples:");
            System.out.println("   java Client 1             (Interactive mode as Client-1)");
            System.out.println("   java Client kmutt         (Interactive mode as Client-kmutt)");
            return;
        }
        for (String a : args) {
            if (a.equals("--no-color")) useColor = false;
        }

        //set client
        final String clientId = "Client-" + args[0];
        Scanner scanner = new Scanner(System.in);

        //main send & read
        try (
            //set socket&Buffer
            Socket socket = new Socket(SERVER_IP, PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        ) {
            printBanner(clientId);
            System.out.println(c(GREEN, " Connected to server successfully"));

            while (true) {
                printMenu();
                System.out.print(c(BOLD, "\n " + clientId + " > "));
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                String command = parts[0].toUpperCase();
                String cmdArg = parts.length > 1 ? parts[1] : "";

                // allow menu numbers
                switch (command) {
                    case "1": command = "LIST"; break;
                    case "2": command = "STATUS"; break;
                    case "3": command = "RESERVE"; break;
                    case "4": command = "CANCEL"; break;
                    case "5": command = "SHOOT"; break;
                    case "0": command = "QUIT"; break;
                    default: break;
                }

                // commands that need an id: ask for it if the user didn't type one
                if ((command.equals("STATUS") || command.equals("RESERVE") || command.equals("CANCEL"))
                        && cmdArg.isEmpty()) {
                    cmdArg = ask(scanner, "Resource id");
                    if (cmdArg.isEmpty()) {
                        printError("Resource id is required");
                        continue;
                    }
                }

                switch (command) {
                    case "LIST": {
                        socket.setSoTimeout(15000);
                        String cmd = "LIST";
                        out.println(cmd);
                        printResult("LIST", cmd, readServerResponse(in));
                        break;
                    }
                    case "STATUS": {
                        String cmd = "STATUS " + cmdArg;
                        out.println(cmd);
                        printResult("STATUS", cmd, readServerResponse(in));
                        break;
                    }
                    case "RESERVE": {
                        String cmd = "RESERVE " + cmdArg + " " + clientId;
                        out.println(cmd);
                        printResult("RESERVE", cmd, readServerResponse(in));
                        break;
                    }
                    case "CANCEL": {
                        String cmd = "CANCEL " + cmdArg + " " + clientId;
                        out.println(cmd);
                        printResult("CANCEL", cmd, readServerResponse(in));
                        break;
                    }
                    case "SHOOT":
                        runStressTest();
                        break;
                    case "QUIT":
                        System.out.println(c(CYAN, "\n Goodbye!"));
                        return;
                    default:
                        printError("[" + parts[0] + "]: command not found");
                }
            }
        }
        //if can't connect to server
        catch (IOException e) {
            System.out.println("Connecting to Server Unsuccess [ PORT:" + PORT + " ]");
        }
    }

    // ---------- stress test (same logic as before, moved into a method) ----------
    private static void runStressTest() throws InterruptedException {
        int resourceId = 1;
        final int TOTAL = 1500;
        ExecutorService executor = Executors.newFixedThreadPool(TOTAL);
        CountDownLatch ready = new CountDownLatch(TOTAL);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(TOTAL);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 1; i <= TOTAL; i++) {
            final String id = "user-" + i;
            final int currentResourceId = resourceId;
            executor.submit(() -> {
                try (
                    Socket s = new Socket(SERVER_IP, PORT);
                    PrintWriter o = new PrintWriter(s.getOutputStream(), true);
                    BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()))
                ) {
                    ready.countDown();
                    startSignal.await();               // fire at the same time
                    o.println("RESERVE " + currentResourceId + " " + id);
                    String resp = r.readLine();
                    if (resp != null && resp.contains("SUCCESS:")) successCount.incrementAndGet();
                    else failCount.incrementAndGet();
                } catch (Exception e) {
                    ready.countDown();
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });

            if (resourceId == 50) resourceId = 1;
            else resourceId++;
        }

        ready.await();
        long start = System.currentTimeMillis();
        System.out.println(c(YELLOW, "\n Start stress test, wait a moment..."));
        startSignal.countDown();
        done.await();
        executor.shutdown();
        long duration = System.currentTimeMillis() - start;

        System.out.println();
        boxTop("STRESS TEST RESULTS");
        boxLine("Total Requests Sent : " + TOTAL, null);
        boxLine("Successful Bookings : " + successCount.get(), GREEN);
        boxLine("Rejected Bookings   : " + failCount.get(), RED);
        boxLine("Execution Time      : " + duration + " ms", null);
        boxBottom();
    }

    // ---------- read server reply (same idea as before, but returns the lines) ----------
    public static List<String> readServerResponse(BufferedReader in) throws IOException {
        List<String> lines = new ArrayList<>();
        String line = in.readLine();
        if (line != null) {
            lines.add(line);
            while (in.ready()) {
                lines.add(in.readLine());
            }
        }
        return lines;
    }
}
