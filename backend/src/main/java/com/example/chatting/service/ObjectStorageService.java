package com.example.chatting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class ObjectStorageService {
    private final S3Client s3Client;

    @Value("${app.storage.bucket:}")
    private String bucket;

    public boolean enabled() {
        return bucket != null && !bucket.isBlank();
    }

    public void put(String key, MultipartFile file) throws IOException {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    }

    public StoredObject get(String key) {
        try {
            var response = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
            return new StoredObject(response.readAllBytes(), response.response().contentType());
        } catch (NoSuchKeyException exception) {
            return null;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read object from S3", exception);
        }
    }

    public void delete(String key) {
        s3Client.deleteObject(builder -> builder.bucket(bucket).key(key));
    }

    public record StoredObject(byte[] content, String contentType) {}
}
