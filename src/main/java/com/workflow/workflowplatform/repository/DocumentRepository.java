package com.workflow.workflowplatform.repository;

import com.workflow.workflowplatform.model.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends MongoRepository<Document, String> {

    List<Document> findByTramiteIdAndActiveTrue(String tramiteId);

    List<Document> findByTramiteIdAndNodeIdAndActiveTrue(String tramiteId, String nodeId);

    List<Document> findByCompanyIdAndActiveTrue(String companyId);

    List<Document> findByTramiteIdAndCategoryAndActiveTrue(String tramiteId, String category);

    long countByTramiteIdAndActiveTrue(String tramiteId);
}
