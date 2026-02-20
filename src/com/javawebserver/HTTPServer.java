package com.javawebserver;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class HTTPServer {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    private static final String WEB_ROOT = "web";
    private static final String CONTENT_LENGTH_HEADER = "Content-Length: ";
    private static final int MAX_REQUEST_LINE_PARTS = 3;

    private static class RequestData {
        private final String method;
        private final String target;
        private final String body;

        private RequestData(String method, String target, String body) {
            this.method = method;
            this.target = target;
            this.body = body;
        }
    }

    private static class ResponseData {
        private final int statusCode;
        private final String reasonPhrase;
        private final String contentType;
        private final byte[] body;

        private ResponseData(int statusCode, String reasonPhrase, String contentType, byte[] body) {
            this.statusCode = statusCode;
            this.reasonPhrase = reasonPhrase;
            this.contentType = contentType;
            this.body = body;
        }
    }

    private static class SocketProcessor implements Runnable {

        private final Socket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        private SocketProcessor(Socket socket) throws Exception {
            this.socket = socket;
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
        }

        public void run() {
            try {
                RequestData request = readRequest();
                ResponseData response = buildResponse(request);
                writeResponse(response);
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    writeResponse(errorResponse(500, "Internal Server Error"));
                } catch (Exception ignored) {
                    // The connection is likely already broken.
                }
            } finally {
                try {
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private RequestData readRequest() throws Exception {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, DEFAULT_CHARSET));
            String method = "";
            String target = "";
            int contentLength = 0;

            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.length() == 0) {
                    break;
                }

                if (method.length() == 0) {
                    String[] requestLineParts = line.split(" ");
                    if (requestLineParts.length >= MAX_REQUEST_LINE_PARTS) {
                        method = requestLineParts[0];
                        target = requestLineParts[1];
                    }
                }

                if (line.startsWith(CONTENT_LENGTH_HEADER)) {
                    contentLength = Integer.parseInt(line.substring(CONTENT_LENGTH_HEADER.length()).trim());
                }
                System.out.println(line);
            }

            String body = "";
            if (contentLength > 0) {
                StringBuilder bodyBuilder = new StringBuilder();
                for (int i = 0; i < contentLength; i++) {
                    int c = reader.read();
                    if (c == -1) {
                        break;
                    }
                    bodyBuilder.append((char) c);
                }
                body = bodyBuilder.toString();
                System.out.println("Ignored request body: " + body);
            }

            return new RequestData(method, target, body);
        }

        private ResponseData buildResponse(RequestData request) throws Exception {
            if (request.method.length() == 0) {
                return errorResponse(400, "Bad Request");
            }

            if ("GET".equals(request.method)) {
                return serveStaticFile(request.target);
            }
            if ("HEAD".equals(request.method)) {
                ResponseData getResponse = serveStaticFile(request.target);
                return new ResponseData(getResponse.statusCode, getResponse.reasonPhrase, getResponse.contentType, new byte[0]);
            }
            if ("POST".equals(request.method)
                || "PUT".equals(request.method)
                || "PATCH".equals(request.method)
                || "DELETE".equals(request.method)
                || "OPTIONS".equals(request.method)) {
                return methodStubResponse(request.method);
            }
            return errorResponse(405, "Method Not Allowed");
        }

        private ResponseData serveStaticFile(String rawTarget) throws Exception {
            String target = sanitizeTarget(rawTarget);
            Path webRoot = Paths.get(WEB_ROOT).toAbsolutePath().normalize();
            Path requested = webRoot.resolve(target).normalize();

            if (!requested.startsWith(webRoot)) {
                return errorResponse(403, "Forbidden");
            }
            if (!Files.exists(requested) || !Files.isRegularFile(requested)) {
                return errorResponse(404, "Not Found");
            }

            byte[] body = Files.readAllBytes(requested);
            String contentType = detectContentType(requested.getFileName().toString());
            return new ResponseData(200, "OK", contentType, body);
        }

        private void writeResponse(ResponseData response) throws Exception {
            String headers = "HTTP/1.1 " + response.statusCode + " " + response.reasonPhrase + "\r\n"
                + "Server: " + System.getProperty("os.name") + " Java " + System.getProperty("java.version") + "\r\n"
                + "Content-Type: " + response.contentType + "\r\n"
                + "Content-Length: " + response.body.length + "\r\n"
                + "Content-Language: en\r\n"
                + "Connection: close\r\n\r\n";

            outputStream.write(headers.getBytes(DEFAULT_CHARSET));
            outputStream.write(response.body);
            outputStream.flush();
        }

        private ResponseData errorResponse(int statusCode, String reason) {
            String text = statusCode + " " + reason;
            return new ResponseData(statusCode, reason, "text/plain; charset=UTF-8", text.getBytes(DEFAULT_CHARSET));
        }

        private ResponseData methodStubResponse(String method) {
            String body = "{\"status\":\"stub\",\"method\":\"" + method + "\",\"message\":\"Method is recognized but not implemented yet\"}";
            return new ResponseData(501, "Not Implemented", "application/json; charset=UTF-8", body.getBytes(DEFAULT_CHARSET));
        }

        private String sanitizeTarget(String rawTarget) throws Exception {
            String target = rawTarget == null ? "/" : rawTarget;
            if (target.equals("/")) {
                return "index.html";
            }

            String pathOnly = target;
            int queryStart = pathOnly.indexOf('?');
            if (queryStart >= 0) {
                pathOnly = pathOnly.substring(0, queryStart);
            }
            if (pathOnly.startsWith("/")) {
                pathOnly = pathOnly.substring(1);
            }
            if (pathOnly.length() == 0) {
                return "index.html";
            }
            return URLDecoder.decode(pathOnly, "UTF-8");
        }

        private String detectContentType(String filename) {
            Map<String, String> contentTypes = new HashMap<String, String>();
            contentTypes.put(".html", "text/html; charset=UTF-8");
            contentTypes.put(".css", "text/css; charset=UTF-8");
            contentTypes.put(".js", "application/javascript; charset=UTF-8");
            contentTypes.put(".json", "application/json; charset=UTF-8");
            contentTypes.put(".txt", "text/plain; charset=UTF-8");

            for (Map.Entry<String, String> entry : contentTypes.entrySet()) {
                if (filename.endsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return "application/octet-stream";
        }
    }

    public void run(int port) throws Exception {
        ServerSocket ss = new ServerSocket(port);
        while (true) {
            Socket s = ss.accept();
            new Thread(new SocketProcessor(s)).start();
        }
    }
}
