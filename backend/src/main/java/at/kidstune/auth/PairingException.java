package at.kidstune.auth;

import org.springframework.http.HttpStatus;

public class PairingException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public PairingException(String message, String code, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
