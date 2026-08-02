package com.magzinemedia.contactapi.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
public class R2StorageService {

    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public R2StorageService(
        S3Client s3Client,
        S3Presigner presigner,
        @Value("${app.r2.bucket}") String bucket,
        @Value("${app.r2.public-base-url}") String publicBaseUrl
    ) {
        this.s3Client = s3Client;
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

    public void deleteByPublicUrl(String publicUrl) {
        String key = keyFromPublicUrl(publicUrl);
        if (key == null) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("Failed to delete R2 object '{}': {}", key, e.getMessage());
        }
    }

    public byte[] download(String publicUrl) {
        String key = keyFromPublicUrl(publicUrl);
        if (key == null) {
            throw new IllegalArgumentException("URL is not an R2 object: " + publicUrl);
        }

        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
            GetObjectRequest.builder().bucket(bucket).key(key).build()
        );
        return response.asByteArray();
    }

    public String uploadBytes(String key, byte[] data, String contentType) {
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
            RequestBody.fromBytes(data)
        );
        return publicBaseUrl + "/" + key;
    }

    private String keyFromPublicUrl(String publicUrl) {
        String prefix = publicBaseUrl + "/";
        if (publicUrl == null || !publicUrl.startsWith(prefix)) {
            return null;
        }
        return publicUrl.substring(prefix.length());
    }

    public record PresignedUpload(String uploadUrl, String publicUrl) {
    }
}
