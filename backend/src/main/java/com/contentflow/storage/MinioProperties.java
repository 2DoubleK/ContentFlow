package com.contentflow.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contentflow.minio")
public record MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {
}
