package at.kidstune.requests.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkRejectRequest(
        @NotNull List<String> requestIds,
        String                note
) {}
