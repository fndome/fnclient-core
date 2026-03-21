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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.springframework.util.StringUtils;

/**
 * 异常处理工具类
 * <p>
 * 提供异常消息提取和堆栈信息格式化功能，
 * 用于生成适合返回给客户端的简洁错误信息。
 *
 * @author sim
 */
public final class ResponseableExceptionUtil {

    private static final String UNKNOWN_ERROR = "Unknown error";

    private ResponseableExceptionUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 获取异常的可响应消息
     * <p>
     * 优先返回异常的直接消息，如果消息为空则返回异常类名。
     * 避免向客户端暴露敏感信息。
     *
     * @param e 异常对象
     * @return 简洁的异常消息
     */
    public static String getMessage(Throwable e) {
        if (e == null) {
            return UNKNOWN_ERROR;
        }

        String message = e.getMessage();
        if (StringUtils.hasText(message)) {
            return message.trim();
        }

        return e.getClass().getSimpleName();
    }

    /**
     * 获取异常的完整堆栈信息
     * <p>
     * 用于日志记录，包含完整的异常堆栈。
     *
     * @param e 异常对象
     * @return 完整堆栈字符串
     */
    public static String getFullStackTrace(Throwable e) {
        if (e == null) {
            return "";
        }

        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            e.printStackTrace(printWriter);
            return writer.toString();
        }
    }

    /**
     * 获取异常的简洁堆栈信息
     * <p>
     * 只包含异常类名、消息和第一层 cause，
     * 适合用于日志摘要。
     *
     * @param e 异常对象
     * @return 简洁堆栈字符串
     */
    public static String getShortStackTrace(Throwable e) {
        if (e == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        appendExceptionInfo(sb, e);

        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            sb.append(" Caused by: ");
            appendExceptionInfo(sb, cause);
        }

        return sb.toString();
    }

    /**
     * 追加异常信息到 StringBuilder
     */
    private static void appendExceptionInfo(StringBuilder sb, Throwable e) {
        sb.append(e.getClass().getName());
        if (StringUtils.hasText(e.getMessage())) {
            sb.append(": ").append(e.getMessage());
        }
    }
}
