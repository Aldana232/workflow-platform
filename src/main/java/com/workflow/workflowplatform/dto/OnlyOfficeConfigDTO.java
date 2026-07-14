package com.workflow.workflowplatform.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OnlyOfficeConfigDTO {
    private String documentType;
    private Document document;
    private EditorConfig editorConfig;
    private String token;

    @Data
    @Builder
    public static class Document {
        private String fileType;
        private String key;
        private String title;
        private String url;
    }

    @Data
    @Builder
    public static class EditorConfig {
        private String callbackUrl;
        private String lang;
        private String mode;
        private User user;
    }

    @Data
    @Builder
    public static class User {
        private String id;
        private String name;
    }
}