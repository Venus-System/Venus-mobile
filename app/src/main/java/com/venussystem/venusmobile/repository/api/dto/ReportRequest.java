package com.venussystem.venusmobile.repository.api.dto;

public class ReportRequest {
    public Long userId;
    public Long adminUserId;
    public String targetType;
    public Long targetId;
    public String reason;
    public String status;

    public ReportRequest(Long userId, String targetType, Long targetId, String reason,
                         String status) {
        this.userId = userId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.status = status;
    }
}
