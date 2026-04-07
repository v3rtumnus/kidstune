package at.kidstune.requests.dto;

import at.kidstune.content.ContentType;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkApproveRequest(
        @NotNull List<String> requestIds,
        ContentType           contentTypeOverride,
        String                note
) {}
