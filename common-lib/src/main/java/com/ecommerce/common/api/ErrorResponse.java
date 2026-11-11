package com.ecommerce.common.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Single error payload returned by every service of the platform.
 *
 * <p>Shared on purpose: the web and mobile front ends can rely on one shape
 * whatever service answered.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private List<String> details;
}
