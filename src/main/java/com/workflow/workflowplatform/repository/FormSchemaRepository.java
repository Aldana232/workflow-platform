package com.workflow.workflowplatform.repository;

import com.workflow.workflowplatform.model.FormSchema;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FormSchemaRepository extends MongoRepository<FormSchema, String> {
    List<FormSchema> findByProcessId(String processId);
    Optional<FormSchema> findByProcessIdAndNodeId(String processId, String nodeId);
}
