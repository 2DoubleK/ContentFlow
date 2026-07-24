package com.contentflow.storage;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
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
            ensureBucket();
            client.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(objectKey)
                    .stream(input, size, -1).contentType(contentType).build());
        } catch (Exception exception) {
            throw new IllegalStateException("minio upload failed", exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
        }
    }

    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("minio delete failed", exception);
        }
    }

    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("minio read failed", exception);
        }
    }
}
