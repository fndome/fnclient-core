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
package me.fndo.fnclient.core.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.client.support.RestTemplateAdapter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import jakarta.servlet.http.HttpServletRequest;
import me.fndo.fnclient.core.exception.Remote1xxException;
import me.fndo.fnclient.core.exception.Remote3xxException;
import me.fndo.fnclient.core.exception.Remote4xxException;
import me.fndo.fnclient.core.exception.Remote5xxException;
import me.fndo.fnclient.core.util.ErrorBodyLogUtil;

/**
 * HTTP 远程调用 Bean 工厂
 * <p>
 * 创建基于 HTTP 的远程服务代理，支持请求头透传和异常分类处理。
 * <p>
 * <b>请求头处理逻辑：</b>
 * <ul>
 *     <li>如果 {@code @HttpExchange.contentType} 配置了值，使用静态头模式（只透传 Content-Type）</li>
 *     <li>如果 {@code @HttpExchange.contentType} 未配置，透传当前请求的所有头</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * // 场景 1：默认透传上下文头（适用于微服务内部调用）
 * {@code @HttpExchange}("http://shop-service/shop")
 * public interface ShopRemote { ... }
 *
 * // 场景 2：使用静态 Content-Type（适用于调用外网或老项目）
 * {@code @HttpExchange}(value = "http://external-api.com", contentType = "application/x-www-form-urlencoded")
 * public interface ExternalRemote { ... }
 * </pre>
 *
 * @param <T> 远程接口类型
 * @author sim
 */
public class HttpBeanFactory<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpBeanFactory.class);

    private static final int BUFFER_SIZE = 4096;
    private static final int MAX_ERROR_BODY_BYTES = 64 * 1024;
    private static final String TRUNCATED_SUFFIX = "...[TRUNCATED]";

    private static final String HEADER_CONTENT_LENGTH = "content-length";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String HEADER_AUTHORIZATION = "authorization";
    private static final String HEADER_PROXY_AUTHORIZATION = "proxy-authorization";
    private static final String HEADER_COOKIE = "cookie";
    private static final String HEADER_SET_COOKIE = "set-cookie";
    private static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    private static final String REST_TEMPLATE_CLASS_NAME = "org.springframework.web.client.RestTemplate";
    private static final String REST_CLIENT_CLASS_NAME = "org.springframework.web.client.RestClient";

    private final Class<T> interfaceType;
    private final String configuredContentType;
    private final boolean useStaticHeadersOnly;
    private final String httpClientClassName;

    public HttpBeanFactory(Class<T> interfaceType) {
        this(interfaceType, REST_TEMPLATE_CLASS_NAME);
    }

    public HttpBeanFactory(Class<T> interfaceType, String httpClientClassName) {
        this.interfaceType = interfaceType;
        HttpExchangeConfig config = loadHttpExchangeConfig();
        this.configuredContentType = config.contentType;
        this.useStaticHeadersOnly = config.useStaticHeadersOnly;
        this.httpClientClassName = (httpClientClassName == null || httpClientClassName.trim().isEmpty())
                ? REST_TEMPLATE_CLASS_NAME
                : httpClientClassName.trim();
    }

    /**
     * 加载 {@code @HttpExchange} 配置
     * <p>
     * 如果用户配置了 contentType，则使用静态头（不透传上下文头）
     * 如果用户未配置 contentType，则透传上下文头
     */
    private HttpExchangeConfig loadHttpExchangeConfig() {
        HttpExchange annotation = interfaceType.getAnnotation(HttpExchange.class);

        if (annotation == null) {
            return HttpExchangeConfig.DEFAULT;
        }

        // 检查用户是否配置了 contentType
        String contentType = annotation.contentType();
        if (contentType != null && !contentType.isEmpty()) {
            // 用户配置了 contentType，使用静态头模式并应用用户值
            return new HttpExchangeConfig(contentType, true);
        }

        return HttpExchangeConfig.DEFAULT;
    }

    /**
     * 创建 HTTP 远程服务代理
     *
     * @return 远程服务代理实例
     */
    public T getObject() {
        HttpServiceProxyFactory factory;
        if (REST_CLIENT_CLASS_NAME.equals(httpClientClassName)) {
            RestClient restClient = createRestClient();
            factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
        } else {
            RestTemplate restTemplate = createRestTemplate();
            factory = HttpServiceProxyFactory.builderFor(RestTemplateAdapter.create(restTemplate)).build();
        }
        return factory.createClient(interfaceType);
    }

    /**
     * 创建并配置 RestTemplate
     */
    private RestTemplate createRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(createHeaderPropagationInterceptor());
        restTemplate.getInterceptors().add(createResponseErrorHandler());
        return restTemplate;
    }

    /**
     * 创建并配置 RestClient
     */
    private RestClient createRestClient() {
        return RestClient.builder()
                .requestInterceptor(createHeaderPropagationInterceptor())
                .requestInterceptor(createResponseErrorHandler())
                .build();
    }

    /**
     * 创建请求头处理拦截器
     * <p>
     * - 如果配置了静态头，使用静态头
     * - 否则透传上下文头
     */
    private ClientHttpRequestInterceptor createHeaderPropagationInterceptor() {
        return (request, body, execution) -> {
            if (useStaticHeadersOnly) {
                // 使用静态头配置
                applyStaticHeaders(request);
            } else {
                // 透传上下文头
                propagateHeadersFromContext(request);
            }
            return execution.execute(request, body);
        };
    }

    /**
     * 应用静态头配置
     */
    private void applyStaticHeaders(org.springframework.http.HttpRequest request) {
        if (configuredContentType != null && !configuredContentType.trim().isEmpty()) {
            request.getHeaders().set(HEADER_CONTENT_TYPE, configuredContentType.trim());
        }
    }

    /**
     * 透传上下文请求头
     */
    private void propagateHeadersFromContext(org.springframework.http.HttpRequest request) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            return;
        }

        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) requestAttributes;
        HttpServletRequest servletRequest = servletRequestAttributes.getRequest();

        propagateHeaders(servletRequest, request);
    }

    /**
     * 透传请求头
     */
    private void propagateHeaders(HttpServletRequest source, org.springframework.http.HttpRequest target) {
        Enumeration<String> headerNames = source.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = source.getHeader(headerName);

            if (shouldSkipHeader(headerName, headerValue)) {
                continue;
            }

            if (HEADER_CONTENT_TYPE.equalsIgnoreCase(headerName)) {
                target.getHeaders().set(headerName, CONTENT_TYPE_JSON);
            } else {
                target.getHeaders().set(headerName, headerValue);
            }
        }
    }

    /**
     * 判断是否应该跳过某个请求头
     */
    private boolean shouldSkipHeader(String headerName, String headerValue) {
        if (HEADER_CONTENT_LENGTH.equalsIgnoreCase(headerName)) {
            return true;
        }
        if (HEADER_AUTHORIZATION.equalsIgnoreCase(headerName)) {
            return true;
        }
        if (HEADER_PROXY_AUTHORIZATION.equalsIgnoreCase(headerName)) {
            return true;
        }
        if (HEADER_COOKIE.equalsIgnoreCase(headerName)) {
            return true;
        }
        if (HEADER_SET_COOKIE.equalsIgnoreCase(headerName)) {
            return true;
        }
        return headerValue == null || headerValue.trim().isEmpty();
    }

    /**
     * 创建响应错误处理拦截器
     */
    private ClientHttpRequestInterceptor createResponseErrorHandler() {
        return (request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            if (response.getStatusCode().is2xxSuccessful()) {
                return response;
            }

            String uri = buildUriString(request.getURI());
            String responseBody = readResponseBody(response.getBody());

            String safeBodySummary = ErrorBodyLogUtil.toSafeSummary(responseBody);
            LOGGER.error("Request failed: uri={}, status={}, bodySummary={}",
                    uri,
                    response.getStatusCode().value(),
                    safeBodySummary);

            throwExceptionByStatus(response.getStatusCode().value(), uri, responseBody);
            return response;
        };
    }

    /**
     * 根据状态码抛出对应类型的异常
     */
    private void throwExceptionByStatus(int statusCode, String uri, String body) {
        if (statusCode >= 400 && statusCode < 500) {
            throw new Remote4xxException(statusCode, uri, body);
        } else if (statusCode >= 500 && statusCode < 600) {
            throw new Remote5xxException(statusCode, uri, body);
        } else if (statusCode >= 300 && statusCode < 400) {
            throw new Remote3xxException(statusCode, uri, body);
        } else if (statusCode >= 100 && statusCode < 200) {
            throw new Remote1xxException(statusCode, uri, body);
        } else {
            throw new RuntimeException("Request failed: " + uri + ", status: " + statusCode);
        }
    }

    /**
     * 构建 URI 字符串
     */
    private String buildUriString(java.net.URI uri) {
        return uri.getHost() + ":" + uri.getPort() + uri.getPath();
    }

    /**
     * 读取响应体内容
     */
    private String readResponseBody(InputStream inputStream) {
        if (inputStream == null) {
            return "";
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            int bytesRead;
            int totalBytes = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (totalBytes >= MAX_ERROR_BODY_BYTES) {
                    break;
                }
                int writableBytes = Math.min(bytesRead, MAX_ERROR_BODY_BYTES - totalBytes);
                output.write(buffer, 0, writableBytes);
                totalBytes += writableBytes;
            }
            String body = output.toString(UTF_8.name());
            if (totalBytes >= MAX_ERROR_BODY_BYTES) {
                return body + TRUNCATED_SUFFIX;
            }
            return body;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read response body", e);
        }
    }

    /**
     * HTTP Exchange 配置
     */
    private static class HttpExchangeConfig {
        static final HttpExchangeConfig DEFAULT = new HttpExchangeConfig(null, false);

        final String contentType;
        final boolean useStaticHeadersOnly;

        HttpExchangeConfig(String contentType, boolean useStaticHeadersOnly) {
            this.contentType = contentType;
            this.useStaticHeadersOnly = useStaticHeadersOnly;
        }
    }
}
