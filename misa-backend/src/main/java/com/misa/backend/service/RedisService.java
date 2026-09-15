package com.misa.backend.service;

import java.util.concurrent.TimeUnit;

public interface RedisService {

    String get(String key);

    void save(String key, String value, long timeout, TimeUnit unit);

    void delete(String key);
}
