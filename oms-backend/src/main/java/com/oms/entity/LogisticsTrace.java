package com.oms.entity;

import lombok.Data;
import java.util.List;

@Data
public class LogisticsTrace {
    private String company;
    private String trackingNumber;
    private List<TraceItem> traces;
    private String status;
    private Boolean isSuccess;
    private String message;

    @Data
    public static class TraceItem {
        private String time;
        private String status;
        private String desc;
    }
}
