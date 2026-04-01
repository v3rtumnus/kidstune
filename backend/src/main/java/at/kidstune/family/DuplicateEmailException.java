package at.kidstune.family;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("E-Mail-Adresse ist bereits registriert");
    }
}
