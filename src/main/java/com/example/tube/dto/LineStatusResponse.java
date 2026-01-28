package com.example.tube.dto;

import java.time.Instant;
import java.util.List;

public record LineStatusResponse(
        String lineId, String lineName, String status,
        boolean disrupted, boolean planned,
        List<String> reasons, String sourceUrl) {}
