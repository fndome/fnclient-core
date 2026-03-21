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
 * 远程调用 3xx 异常
 * <p>
 * 表示重定向状态码（300-399）。
 *
 * @author sim
 */
public class Remote3xxException extends RemoteCallException {

    private static final long serialVersionUID = 1L;

    private static final String ERROR_MESSAGE_PREFIX = "Remote 3xx Redirection Error";

    public Remote3xxException(int statusCode, String uri, String body) {
        super(statusCode, uri, body);
    }

    public Remote3xxException(int statusCode, String uri, String body, Throwable cause) {
        super(statusCode, uri, body, cause);
    }

    @Override
    public String getMessage() {
        return formatMessage(ERROR_MESSAGE_PREFIX, getStatusCode(), getUri(), getBody());
    }

    /**
     * 格式化异常消息
     */
    private static String formatMessage(String prefix, int statusCode, String uri, String body) {
        return String.format("%s: uri=%s, status=%d, body=%s", prefix, uri, statusCode, body);
    }
}
