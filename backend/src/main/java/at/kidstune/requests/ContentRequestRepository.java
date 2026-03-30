package at.kidstune.requests;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentRequestRepository extends JpaRepository<ContentRequest, String> {

    long countByProfileIdInAndStatus(List<String> profileIds, ContentRequestStatus status);
}