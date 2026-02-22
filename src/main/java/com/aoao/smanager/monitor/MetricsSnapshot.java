package com.aoao.smanager.monitor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetricsSnapshot {
    public long memoryTotalBytes;
    public long memoryUsedBytes;
    public long memoryFreeBytes;
    public double cpuUsage;
    public double[] systemLoadAverage;
    public long diskTotalBytes;
    public long diskFreeBytes;
    public double diskReadBytesPerSec;
    public double diskWriteBytesPerSec;
    public double netUpBytesPerSec;
    public double netDownBytesPerSec;
    public long timestamp;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String toJson(MetricsSnapshot s) {
        try {
            return MAPPER.writeValueAsString(s);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}

