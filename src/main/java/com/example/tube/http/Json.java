package com.example.tube.http;

import com.example.tube.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class Json {
    private static final ObjectMapper om = new ObjectMapper().findAndRegisterModules();

    public static void sendJson(HttpExchange ex, int status, Object body) {
        try {
            byte[] bytes = om.writeValueAsBytes(body);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
            System.out.println("Json.sendJson wrote " + bytes.length + " bytes status=" + status);
        } catch (IOException ioe) {
            // Client went away. Not a server bug.
            if (isClientAbort(ioe)) {
                System.out.println("Client aborted connection while writing response (status=" + status + ")");
                return;
            }
            System.out.println("Json.sendJson FAILED: IOException: " + ioe.getMessage());
            ioe.printStackTrace(System.out);
        } catch (Exception e) {
            System.out.println("Json.sendJson FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
        } finally {
            ex.close();
        }
    }

    private static boolean isClientAbort(IOException ioe) {
        String msg = String.valueOf(ioe.getMessage()).toLowerCase();
        // Common Windows/JDK httpserver messages when client drops connection
        return msg.contains("aborted")
                || msg.contains("broken pipe")
                || msg.contains("connection reset")
                || msg.contains("forcibly closed");
    }



    public static void sendText(HttpExchange ex, int status, String text) {
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException ignored) {
        } finally {
            ex.close();
        }
    }

    public static void sendError(HttpExchange ex, int status, String error, String message) {
        sendJson(ex, status, new ApiError(Instant.now(), status, error, message, ex.getRequestURI().getPath()));
    }
}
