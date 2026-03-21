/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.fndo.fnclient.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * 异常响应体日志工具
 * <p>
 * 负责对响应体做脱敏和截断，避免日志泄露敏感信息。
 */
public final class ErrorBodyLogUtil {

    private static final int MAX_PREVIEW_CHARS = 512;

    private static final Pattern AUTHORIZATION_PATTERN =
            Pattern.compile("(?i)(authorization\\s*[=:]\\s*)([^,\\s\\\"}]+)");
    private static final Pattern COOKIE_PATTERN =
            Pattern.compile("(?i)(cookie\\s*[=:]\\s*)([^,\\s\\\"}]+)");
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("(?i)(password\\s*[=:]\\s*)([^,\\s\\\"}]+)");
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("(?i)(token\\s*[=:]\\s*)([^,\\s\\\"}]+)");

    private ErrorBodyLogUtil() {
    }

    /**
     * 生成安全日志摘要。
     *
     * @param body 原始响应体
     * @return 摘要字符串（长度、哈希、预览）
     */
    public static String toSafeSummary(String body) {
        String value = body == null ? "" : body;
        String masked = maskSensitive(value);
        String preview = truncate(masked, MAX_PREVIEW_CHARS);
        String sha256 = sha256Hex(value);
        return "len=" + value.length() + ",sha256=" + sha256 + ",preview=" + preview;
    }

    private static String maskSensitive(String input) {
        String value = input;
        value = AUTHORIZATION_PATTERN.matcher(value).replaceAll("$1***");
        value = COOKIE_PATTERN.matcher(value).replaceAll("$1***");
        value = PASSWORD_PATTERN.matcher(value).replaceAll("$1***");
        value = TOKEN_PATTERN.matcher(value).replaceAll("$1***");
        return value;
    }

    private static String truncate(String input, int maxChars) {
        if (input.length() <= maxChars) {
            return input;
        }
        return input.substring(0, maxChars) + "...[TRUNCATED]";
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "NA";
        }
    }
}
