package at.kidstune.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmPairingRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "code must be exactly 6 digits")
        String code,

        @NotBlank @Size(max = 255)
        String deviceName
) {}
