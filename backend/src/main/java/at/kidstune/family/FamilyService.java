package at.kidstune.family;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final PasswordEncoder  passwordEncoder;

    public FamilyService(FamilyRepository familyRepository, PasswordEncoder passwordEncoder) {
        this.familyRepository = familyRepository;
        this.passwordEncoder  = passwordEncoder;
    }

    /**
     * Creates a new family account.
     *
     * @param email    login e-mail (must be unique)
     * @param password plaintext password (will be BCrypt-hashed)
     * @return the new family's ID
     * @throws DuplicateEmailException if the e-mail is already in use
     */
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    @Transactional
    public String register(String email, String password) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email address");
        }
        if (familyRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException();
        }
        Family family = new Family();
        family.setEmail(email);
        family.setPasswordHash(passwordEncoder.encode(password));
        family = familyRepository.save(family);
        return family.getId();
    }

    /**
     * Authenticates a family by e-mail and password.
     *
     * @param email    login e-mail
     * @param password plaintext password
     * @return the family's ID
     * @throws InvalidCredentialsException if the e-mail is unknown or the password is wrong
     */
    public String authenticate(String email, String password) {
        Family family = familyRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, family.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return family.getId();
    }
}