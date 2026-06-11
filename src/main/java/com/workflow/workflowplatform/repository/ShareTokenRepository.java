package com.workflow.workflowplatform.repository;

import com.workflow.workflowplatform.model.ShareToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShareTokenRepository extends MongoRepository<ShareToken, String> {
    Optional<ShareToken> findByTokenAndActiveTrue(String token);
    List<ShareToken> findByTramiteIdAndActiveTrue(String tramiteId);
    void deleteByExpiresAtBefore(LocalDateTime now);
}
