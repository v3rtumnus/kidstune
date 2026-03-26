package at.kidstune.profile;

import org.springframework.http.HttpStatus;

public class ProfileException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ProfileException(String message, String code) {
        this(message, code, HttpStatus.BAD_REQUEST);
    }

    public ProfileException(String message, String code, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
