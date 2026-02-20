package com.javawebserver;

public class Main {
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        HTTPServer server = new HTTPServer();
        int port = parsePort(args);
        try {
            server.run(port);
        } catch (Exception t) {
            t.printStackTrace();
        }
    }

    private static int parsePort(String[] args) {
        if (args == null || args.length == 0) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException ignored) {
            return DEFAULT_PORT;
        }
    }
}
