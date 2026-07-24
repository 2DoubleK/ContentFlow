package com.contentflow.storage;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import org.springframework.stereotype.Service;

@Service
public class MinioServiceImpl implements MinioService {
    private final MinioClient client;
    private final MinioProperties properties;

    public MinioServiceImpl(MinioClient client, MinioProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public void upload(InputStream input, long size, String contentType, String objectKey) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(objectKey)
                    .stream(input, size, -1).contentType(contentType).build());
        } catch (Exception exception) {
            throw new IllegalStateException("minio upload failed", exception);
        }
    }

    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("minio delete failed", exception);
        }
    }
}
