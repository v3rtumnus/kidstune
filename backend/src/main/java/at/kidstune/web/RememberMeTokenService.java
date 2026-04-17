package at.kidstune.web;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thin transactional wrapper around {@link RememberMeTokenRepository} delete
 * operations.  Required because JPA {@code EntityManager.remove()} demands an
 * active thread-bound transaction; calling the repository directly from inside
 * a {@code Mono.fromCallable} on {@code Schedulers.boundedElastic()} without
 * this wrapper fails with "No EntityManager with actual transaction available".
 */
@Service
public class RememberMeTokenService {

    private final RememberMeTokenRepository tokenRepository;

    public RememberMeTokenService(RememberMeTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public void deleteToken(RememberMeToken token) {
        tokenRepository.delete(token);
    }

    @Transactional
    public void deleteAllForFamily(String familyId) {
        tokenRepository.deleteByFamilyId(familyId);
    }
}
