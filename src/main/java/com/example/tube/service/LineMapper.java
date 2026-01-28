package com.example.tube.service;

import com.example.tube.dto.LineStatusResponse;
import com.example.tube.tfl.Line;

import java.util.ArrayList;
import java.util.List;

public class LineMapper {
    public LineStatusResponse toResponse(Line line, String sourceUrl, boolean planned) {
        String status = "Unknown";
        boolean disrupted = false;
        List<String> reasons = new ArrayList<>();

        if (line != null && line.lineStatuses != null && !line.lineStatuses.isEmpty()) {
            var ls = line.lineStatuses.getFirst();
            if (ls.statusSeverityDescription != null) status = ls.statusSeverityDescription;
            if (ls.reason != null && !ls.reason.isBlank()) { disrupted = true; reasons.add(ls.reason); }
        }

        if (line != null && line.disruptions != null) {
            for (var d : line.disruptions) {
                if (d == null) continue;
                if (d.description != null && !d.description.isBlank()) { disrupted = true; reasons.add(d.description); }
                else if (d.additionalInfo != null && !d.additionalInfo.isBlank()) { disrupted = true; reasons.add(d.additionalInfo); }
            }
        }

        return new LineStatusResponse(line.id, line.name, status, disrupted, planned, reasons, sourceUrl);
    }
}
