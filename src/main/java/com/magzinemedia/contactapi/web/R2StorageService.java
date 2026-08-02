package com.magzinemedia.contactapi.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
public class R2StorageService {

    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public R2StorageService(
        S3Presigner presigner,
        @Value("${app.r2.bucket}") String bucket,
        @Value("${app.r2.public-base-url}") String publicBaseUrl
    ) {
        this.presigner = presigner;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    public String buildKey(String prefix, String fileName) {
        String sanitized = fileName == null ? "file" : fileName.replaceAll("[^A-Za-z0-9._-]", "-");
        return prefix + "/" + UUID.randomUUID() + "-" + sanitized;
    }

    public PresignedUpload presignUpload(String key, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(10))
            .putObjectRequest(objectRequest)
            .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);

        return new PresignedUpload(presigned.url().toString(), publicBaseUrl + "/" + key);
    }

    public record PresignedUpload(String uploadUrl, String publicUrl) {
    }
}
