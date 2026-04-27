package com.workflow.workflowplatform.repository;

import com.workflow.workflowplatform.model.Tramite;
import com.workflow.workflowplatform.model.enums.TramiteStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TramiteRepository extends MongoRepository<Tramite, String> {
    List<Tramite> findByClienteInfoEmail(String email);
    List<Tramite> findByClienteInfoCi(String ci);
    List<Tramite> findByCreatedBy(String createdBy);
    List<Tramite> findByCurrentNodeId(String currentNodeId);
    Optional<Tramite> findByCode(String code);
    List<Tramite> findByProcessIdAndStatus(String processId, TramiteStatus status);
    List<Tramite> findByProcessId(String processId);
    List<Tramite> findByCreatedByAndStatus(String createdBy, TramiteStatus status);
    List<Tramite> findByUserIdAndStatus(String userId, TramiteStatus status);
}
