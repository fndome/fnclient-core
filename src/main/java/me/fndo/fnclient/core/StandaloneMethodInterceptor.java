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
package me.fndo.fnclient.core;

import java.lang.reflect.Method;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone 模式的方法拦截器
 * <p>
 * 负责将远程接口方法调用路由到本地 Controller 方法。
 * 通过预先建立的方法映射表，实现方法调用的精确转发。
 *
 * @author sim
 */
public class StandaloneMethodInterceptor implements MethodInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneMethodInterceptor.class);

    private final Class<?> remoteInterface;
    private final Map<Method, Object> methodControllerMap;
    private final Map<Method, Method> methodMapping;

    public StandaloneMethodInterceptor(Class<?> remoteInterface,
                                       Map<Method, Object> methodControllerMap,
                                       Map<Method, Method> methodMapping) {
        this.remoteInterface = remoteInterface;
        this.methodControllerMap = methodControllerMap;
        this.methodMapping = methodMapping;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method remoteMethod = invocation.getMethod();

        Object controller = methodControllerMap.get(remoteMethod);
        if (controller == null) {
            String errorMessage = String.format(
                    "No standalone controller found for method: %s in remote: %s",
                    remoteMethod.getName(),
                    remoteInterface.getSimpleName()
            );
            LOGGER.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        Method controllerMethod = methodMapping.get(remoteMethod);
        if (controllerMethod == null) {
            String errorMessage = String.format(
                    "No controller method mapping found for: %s",
                    remoteMethod.getName()
            );
            LOGGER.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Invoking controller method: {}.{} with args: {}",
                    controller.getClass().getSimpleName(),
                    controllerMethod.getName(),
                    invocation.getArguments());
        }

        return controllerMethod.invoke(controller, invocation.getArguments());
    }
}
