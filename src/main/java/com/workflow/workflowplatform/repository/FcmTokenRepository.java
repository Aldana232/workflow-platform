package com.workflow.workflowplatform.repository;

import com.workflow.workflowplatform.model.FcmToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FcmTokenRepository extends MongoRepository<FcmToken, String> {

    List<FcmToken> findByTramiteCode(String tramiteCode);

    Optional<FcmToken> findByTramiteCodeAndFcmToken(String tramiteCode, String fcmToken);

    void deleteByFcmToken(String fcmToken);
}
