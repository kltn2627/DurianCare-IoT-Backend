package com.duriancare.farm.dto;

public record GenerateTreesResult(
        String zoneId,
        int generated,
        int skipped,
        String treeCodePrefix) {
}
