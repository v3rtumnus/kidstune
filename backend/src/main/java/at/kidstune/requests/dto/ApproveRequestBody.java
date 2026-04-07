package at.kidstune.requests.dto;

import at.kidstune.content.ContentType;

import java.util.List;

public record ApproveRequestBody(
        List<String> approvedByProfileIds,  // null → requesting profile only
        String       note,
        ContentType  contentTypeOverride     // null → keep content type from request
) {}
