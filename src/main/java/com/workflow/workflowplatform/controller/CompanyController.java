package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.model.Company;
import com.workflow.workflowplatform.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
public class CompanyController {

    private final CompanyRepository companyRepository;

    /**
     * Crea una nueva empresa
     * Solo administradores
     * 
     * @param company Objeto Company
     * @return Empresa creada con 201 CREATED
     */
    @PostMapping
    public ResponseEntity<Company> createCompany(@RequestBody Company company) {
        try {
            company.setActive(true);
            company.setCreatedAt(LocalDateTime.now());
            company.setUpdatedAt(LocalDateTime.now());

            Company savedCompany = companyRepository.save(company);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedCompany);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
