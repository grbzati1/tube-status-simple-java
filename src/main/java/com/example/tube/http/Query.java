package com.example.tube.http;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Query {
    public static Map<String, String> parse(URI uri) {
        Map<String, String> out = new HashMap<>();
        String q = uri.getRawQuery();
        if (q == null || q.isBlank()) return out;
        for (String pair : q.split("&")) {
            if (pair.isBlank()) continue;
            int idx = pair.indexOf('=');
            if (idx < 0) out.put(dec(pair), "");
            else out.put(dec(pair.substring(0, idx)), dec(pair.substring(idx + 1)));
        }
        return out;
    }
    private static String dec(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
}
