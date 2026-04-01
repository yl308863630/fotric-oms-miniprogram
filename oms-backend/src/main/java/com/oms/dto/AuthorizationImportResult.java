package com.oms.dto;

import com.oms.entity.AuthorizationRecord;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuthorizationImportResult {
    private boolean success;
    private String message;
    private int totalRows;
    private int importedRows;
    private List<AuthorizationRecord> records = new ArrayList<>();
}
