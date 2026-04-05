package at.kidstune.device;

import org.springframework.http.HttpStatus;

public class DeviceException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public DeviceException(String message, String code, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
