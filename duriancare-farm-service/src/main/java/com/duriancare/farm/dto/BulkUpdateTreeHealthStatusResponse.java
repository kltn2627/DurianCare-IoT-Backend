package com.duriancare.farm.dto;

import java.util.List;

/**
 * Per-tree outcome of a bulk health-status change. Trees are independent: some may be updated
 * while others fail, and {@code updated}/{@code failed} always reflect what was actually saved.
 */
public record BulkUpdateTreeHealthStatusResponse(
        String healthStatus,
        int requested,
        int updated,
        int failed,
        List<Result> results) {

    /**
     * @param errorCode null when updated; otherwise NOT_FOUND, FORBIDDEN, NOT_ACTIVE,
     *                  or INVALID
     */
    public record Result(
            String treeId,
            String treeCode,
            boolean updated,
            String healthStatus,
            String errorCode,
            String message) {
    }
}
