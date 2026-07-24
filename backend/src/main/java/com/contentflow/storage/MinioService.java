package com.contentflow.storage;

import java.io.InputStream;

public interface MinioService {
    void upload(InputStream input, long size, String contentType, String objectKey);
    void delete(String objectKey);
    InputStream get(String objectKey);
}
