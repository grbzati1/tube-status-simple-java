package com.example.tube.service;

import com.example.tube.dto.LineStatusResponse;
import com.example.tube.dto.UnplannedDisruptionsResponse;
import com.example.tube.tfl.Line;

import java.time.LocalDate;
import java.util.*;

public class TubeStatusService {
    private final TflClient client;
    private final LineMapper mapper = new LineMapper();
    private final String baseUrl;

    public TubeStatusService(TflClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    public LineStatusResponse getLineStatus(String lineId, LocalDate from, LocalDate to) {
        boolean planned = (from != null && to != null);
        String sourceUrl = planned
                ? "%s/Line/%s/Status/%s/to/%s".formatted(baseUrl, lineId, from, to)
                : "%s/Line/%s/Status".formatted(baseUrl, lineId);

        Line[] lines = client.getLineStatus(lineId, from, to);
        if (lines == null || lines.length == 0) {
            return new LineStatusResponse(lineId, lineId, "Unknown", false, planned, List.of(), sourceUrl);
        }
        return mapper.toResponse(lines[0], sourceUrl, planned);
    }

    public UnplannedDisruptionsResponse getAllUnplannedDisruptions() {
        String sourceUrl = "%s/Line/Mode/tube/Status".formatted(baseUrl);
        Line[] lines = client.getAllTubeLineStatus();

        List<LineStatusResponse> out = new ArrayList<>();
        if (lines != null) {
            for (Line l : lines) {
                LineStatusResponse r = mapper.toResponse(l, sourceUrl, false);
                if (!r.disrupted()) continue;
                if (looksPlanned(r.reasons())) continue;
                out.add(r);
            }
        }
        return new UnplannedDisruptionsResponse(out.size(), out);
    }

    private boolean looksPlanned(List<String> reasons) {
        if (reasons == null) return false;
        for (String reason : reasons) {
            if (reason == null) continue;
            String s = reason.toLowerCase(Locale.ROOT);
            if (s.contains("planned") || s.contains("engineering work") || s.contains("scheduled")) return true;
        }
        return false;
    }
}
