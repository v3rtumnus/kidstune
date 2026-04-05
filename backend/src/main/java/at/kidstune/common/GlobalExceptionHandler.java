package at.kidstune.common;

import at.kidstune.auth.PairingException;
import at.kidstune.content.ContentException;
import at.kidstune.device.DeviceException;
import at.kidstune.profile.ProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ContentException.class)
    public ResponseEntity<ApiError> handleContentException(ContentException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getMessage(), ex.getCode()));
    }

    @ExceptionHandler(ProfileException.class)
    public ResponseEntity<ApiError> handleProfileException(ProfileException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getMessage(), ex.getCode()));
    }

    @ExceptionHandler(PairingException.class)
    public ResponseEntity<ApiError> handlePairingException(PairingException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getMessage(), ex.getCode()));
    }

    @ExceptionHandler(DeviceException.class)
    public ResponseEntity<ApiError> handleDeviceException(DeviceException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getMessage(), ex.getCode()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> handleValidation(WebExchangeBindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(new ApiError(message, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiError> handleServerWebInput(ServerWebInputException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("Invalid request: " + ex.getReason(), "INVALID_REQUEST"));
    }
}
