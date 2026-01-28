package com.example.tube.dto;

import java.time.Instant;
import java.util.List;


public record UnplannedDisruptionsResponse(int count, List<com.example.tube.dto.LineStatusResponse> lines) {}
