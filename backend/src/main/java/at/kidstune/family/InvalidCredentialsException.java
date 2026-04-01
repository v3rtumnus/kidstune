package at.kidstune.family;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("E-Mail oder Passwort falsch");
    }
}