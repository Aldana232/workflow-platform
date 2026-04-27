package com.workflow.workflowplatform.repository;

import com.workflow.workflowplatform.model.User;
import com.workflow.workflowplatform.model.enums.UserRole;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    List<User> findByCompanyId(String companyId);
    List<User> findByDepartmentId(String departmentId);
    List<User> findByCompanyIdAndRole(String companyId, UserRole role);
}
