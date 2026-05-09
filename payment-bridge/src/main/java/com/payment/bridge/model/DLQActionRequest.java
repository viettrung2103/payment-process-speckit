package com.payment.bridge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class DLQActionRequest {

    @NotBlank(message = "Operator is required")
    private String operator;

    @NotBlank(message = "Resolution details are required")
    private String resolutionDetails;

    public DLQActionRequest() {
    }

    public DLQActionRequest(String operator, String resolutionDetails) {
        this.operator = operator;
        this.resolutionDetails = resolutionDetails;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    @JsonProperty("resolution_details")
    public String getResolutionDetails() {
        return resolutionDetails;
    }

    public void setResolutionDetails(String resolutionDetails) {
        this.resolutionDetails = resolutionDetails;
    }
}