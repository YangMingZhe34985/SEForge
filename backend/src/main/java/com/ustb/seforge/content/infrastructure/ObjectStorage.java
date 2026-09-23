package com.ustb.seforge.content.infrastructure;

import java.io.IOException;
import java.io.InputStream;

public interface ObjectStorage {
    void put(String objectKey, InputStream input, long size, String contentType) throws IOException;
    InputStream open(String objectKey) throws IOException;
    void delete(String objectKey) throws IOException;
}
