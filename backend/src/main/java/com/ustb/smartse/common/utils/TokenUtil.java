package com.ustb.smartse.common.utils;

import java.util.UUID;

public class TokenUtil {
    public static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}