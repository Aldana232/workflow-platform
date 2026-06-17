package com.workflow.workflowplatform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket:workflow-saguapac-docs}")
    private String bucket;

    public String uploadFile(String storedFileName, InputStream inputStream, String contentType, String userRole) throws IOException {
        String folder = switch (userRole != null ? userRole.toUpperCase() : "") {
            case "FUNCIONARIO" -> "funcionarios/";
            case "ADMIN", "SUPERADMIN" -> "administradores/";
            default -> "documentos/";
        };
        String key = folder + storedFileName;
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, inputStream.available()));

            String fileUrl = "https://" + bucket + ".s3.amazonaws.com/" + key;
            log.info("Archivo subido a S3 [{}]: {}", folder, fileUrl);
            return fileUrl;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al subir archivo a S3 [bucket={}, key={}]: {}", bucket, key, e.getMessage(), e);
            throw new IOException("Error S3: " + e.getMessage(), e);
        }
    }

    public String generatePresignedUrl(String fileName, int expirationMinutes) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        String url = presigned.url().toString();
        log.info("URL prefirmada generada para {}: expira en {} minutos", fileName, expirationMinutes);
        return url;
    }

    public byte[] downloadFile(String storedFileName) {
        String key = storedFileName.contains("/") ? storedFileName : "documentos/" + storedFileName;
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
        } catch (Exception e) {
            for (String folder : new String[]{"funcionarios/", "administradores/", "documentos/"}) {
                try {
                    GetObjectRequest req = GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(folder + storedFileName)
                            .build();
                    return s3Client.getObjectAsBytes(req).asByteArray();
                } catch (Exception ignored) {}
            }
            throw new RuntimeException("Archivo no encontrado en S3: " + storedFileName);
        }
    }

    public void deleteFile(String fileName) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .build();

        s3Client.deleteObject(request);
        log.info("Archivo eliminado de S3: {}", fileName);
    }
}
