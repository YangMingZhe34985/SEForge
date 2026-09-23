package com.ustb.seforge.content.support;

import com.ustb.seforge.content.infrastructure.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemoryObjectStorage implements ObjectStorage {
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String objectKey, InputStream input, long size, String contentType) throws IOException {
        objects.put(objectKey, input.readAllBytes());
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        byte[] data = objects.get(objectKey);
        if (data == null) throw new IOException("Object not found");
        return new ByteArrayInputStream(data);
    }

    @Override
    public void delete(String objectKey) {
        objects.remove(objectKey);
    }
}
