package com.workflow.workflowplatform.repository;

import com.workflow.workflowplatform.model.Process;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessRepository extends MongoRepository<Process, String> {
    java.util.List<Process> findByCompanyId(String companyId);
}
