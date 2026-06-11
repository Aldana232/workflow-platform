package com.workflow.workflowplatform.service;

import com.workflow.workflowplatform.model.Document;
import com.workflow.workflowplatform.model.ShareToken;
import com.workflow.workflowplatform.repository.DocumentRepository;
import com.workflow.workflowplatform.repository.ShareTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShareTokenService {

    private final ShareTokenRepository shareTokenRepository;
    private final DocumentRepository documentRepository;

    public ShareToken generateToken(String tramiteId, String tramiteCode, String userId, String userName) {
        String token = UUID.randomUUID().toString().replace("-", "");
        ShareToken shareToken = ShareToken.builder()
                .token(token)
                .tramiteId(tramiteId)
                .tramiteCode(tramiteCode)
                .createdBy(userId)
                .createdByName(userName)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .active(true)
                .accessCount(0)
                .createdAt(LocalDateTime.now())
                .build();
        return shareTokenRepository.save(shareToken);
    }

    public ShareToken validateToken(String token) {
        ShareToken shareToken = shareTokenRepository.findByTokenAndActiveTrue(token)
                .orElseThrow(() -> new RuntimeException("Token inválido o revocado"));
        if (shareToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El token ha expirado");
        }
        shareToken.setAccessCount(shareToken.getAccessCount() + 1);
        return shareTokenRepository.save(shareToken);
    }

    public List<Document> getDocumentsForSharedToken(String tramiteId) {
        return documentRepository.findByTramiteIdAndActiveTrue(tramiteId);
    }

    public List<ShareToken> getTokensByTramite(String tramiteId) {
        return shareTokenRepository.findByTramiteIdAndActiveTrue(tramiteId);
    }

    public ShareToken revokeToken(String tokenId) {
        ShareToken shareToken = shareTokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token no encontrado: " + tokenId));
        shareToken.setActive(false);
        return shareTokenRepository.save(shareToken);
    }
}
