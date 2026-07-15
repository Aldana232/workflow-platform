package com.workflow.workflowplatform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
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

    /** Sube el archivo a la key exacta dada, sin derivar carpeta por rol. Usado para resubir un documento ya existente (ej. guardado de OnlyOffice). */
    public String uploadFileAtKey(String key, InputStream inputStream, String contentType) throws IOException {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, inputStream.available()));

            String fileUrl = "https://" + bucket + ".s3.amazonaws.com/" + key;
            log.info("Archivo resubido a S3 [key exacta]: {}", fileUrl);
            return fileUrl;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al resubir archivo a S3 [bucket={}, key={}]: {}", bucket, key, e.getMessage(), e);
            throw new IOException("Error S3: " + e.getMessage(), e);
        }
    }

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
        String key = resolveExistingKey(fileName);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        String url = presigned.url().toString();
        log.info("URL prefirmada generada para {}: expira en {} minutos", key, expirationMinutes);
        return url;
    }

    /** Busca en qué carpeta (raíz, funcionarios/, administradores/, documentos/) existe realmente el archivo. */
    private String resolveExistingKey(String fileName) {
        // Si ya tiene carpeta incluida, úsalo directo
        if (fileName.contains("/")) {
            log.info("resolveExistingKey: fileName ya tiene carpeta: {}", fileName);
            if (keyExists(fileName)) return fileName;
            throw new RuntimeException("Archivo no encontrado en S3: " + fileName);
        }

        // Probar todas las carpetas
        for (String folder : new String[]{"administradores/", "funcionarios/", "documentos/", ""}) {
            String candidate = folder.isEmpty() ? fileName : folder + fileName;
            log.info("resolveExistingKey: probando key '{}'", candidate);
            if (keyExists(candidate)) {
                log.info("resolveExistingKey: encontrado en '{}'", candidate);
                return candidate;
            }
        }
        throw new RuntimeException("Archivo no encontrado en S3: " + fileName);
    }

    private boolean keyExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (Exception e) {
            return false;
        }
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
