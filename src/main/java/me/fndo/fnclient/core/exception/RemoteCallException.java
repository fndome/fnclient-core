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
package me.fndo.fnclient.core.exception;

/**
 * 远程调用异常基类
 * <p>
 * 所有远程调用相关的异常都继承此类，包含 HTTP 状态码、请求 URI 和响应体信息。
 *
 * @author sim
 */
public class RemoteCallException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final String uri;
    private final String body;

    /**
     * 构造远程调用异常
     *
     * @param statusCode HTTP 状态码
     * @param uri 请求 URI
     * @param body 响应体内容
     */
    public RemoteCallException(int statusCode, String uri, String body) {
        super(buildMessage(statusCode, uri));
        this.statusCode = statusCode;
        this.uri = uri;
        this.body = body;
    }

    /**
     * 构造远程调用异常（带 cause）
     *
     * @param statusCode HTTP 状态码
     * @param uri 请求 URI
     * @param body 响应体内容
     * @param cause 原始异常
     */
    public RemoteCallException(int statusCode, String uri, String body, Throwable cause) {
        super(buildMessage(statusCode, uri), cause);
        this.statusCode = statusCode;
        this.uri = uri;
        this.body = body;
    }

    /**
     * 构建异常消息
     */
    private static String buildMessage(int statusCode, String uri) {
        return String.format("Remote call failed: uri=%s, status=%d", uri, statusCode);
    }

    /**
     * 获取 HTTP 状态码
     *
     * @return HTTP 状态码
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 获取请求 URI
     *
     * @return 请求 URI
     */
    public String getUri() {
        return uri;
    }

    /**
     * 获取响应体内容
     *
     * @return 响应体内容
     */
    public String getBody() {
        return body;
    }
}
