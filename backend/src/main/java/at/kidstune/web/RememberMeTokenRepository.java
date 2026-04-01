package at.kidstune.web;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RememberMeTokenRepository extends JpaRepository<RememberMeToken, String> {

    Optional<RememberMeToken> findBySeries(String series);

    void deleteByFamilyId(String familyId);
}