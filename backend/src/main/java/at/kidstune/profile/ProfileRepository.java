package at.kidstune.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileRepository extends JpaRepository<ChildProfile, String> {

    List<ChildProfile> findByFamilyId(String familyId);

    long countByFamilyId(String familyId);

    boolean existsByFamilyIdAndName(String familyId, String name);

    boolean existsByFamilyIdAndNameAndIdNot(String familyId, String name, String id);

    boolean existsByIdAndFamilyId(String id, String familyId);
}
