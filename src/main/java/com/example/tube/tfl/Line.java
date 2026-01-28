package com.example.tube.tfl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Line {
    public String id;
    public String name;
    public List<LineStatus> lineStatuses;
    public List<Disruption> disruptions;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LineStatus {
        public int statusSeverity;
        public String statusSeverityDescription;
        public String reason;
        public boolean isActive;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Disruption {
        public String category;
        public String description;
        public String additionalInfo;
        public String created;
        public String lastUpdate;
    }
}
