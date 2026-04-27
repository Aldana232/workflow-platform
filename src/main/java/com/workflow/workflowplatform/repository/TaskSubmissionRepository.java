package com.workflow.workflowplatform.repository;

import com.workflow.workflowplatform.model.TaskSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskSubmissionRepository extends MongoRepository<TaskSubmission, String> {
    List<TaskSubmission> findByTramiteId(String tramiteId);
    List<TaskSubmission> findByNodeIdAndProcessId(String nodeId, String processId);
    Optional<TaskSubmission> findByTramiteIdAndNodeIdAndCompletedAtIsNull(String tramiteId, String nodeId);
    Optional<TaskSubmission> findFirstByTramiteIdAndNodeIdOrderByCompletedAtDesc(String tramiteId, String nodeId);
    List<TaskSubmission> findByDepartmentIdAndCompletedAtIsNull(String departmentId);
    List<TaskSubmission> findByCompletedAtIsNull();

    /** Tareas del departamento indicado O sin departamento asignado, aún pendientes */
    @Query("{ '$and': [ { 'completedAt': null }, { '$or': [ { 'departmentId': ?0 }, { 'departmentId': null }, { 'departmentId': '' } ] } ] }")
    List<TaskSubmission> findPendingByDepartmentOrUnassigned(String departmentId);

    /** Tareas YA COMPLETADAS del departamento (para historial read-only) */
    List<TaskSubmission> findByDepartmentIdAndCompletedAtIsNotNull(String departmentId);
}
