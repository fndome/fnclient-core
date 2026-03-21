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

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 类文件扫描工具
 * <p>
 * 用于扫描指定包路径下的所有类，支持 classpath 下的类和 JAR 包中的类。
 *
 * @author sim
 */
public final class ClassFileReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassFileReader.class);

    private static final String CLASSPATH_ALL_URL_PREFIX = "classpath*:";
    private static final String DEFAULT_RESOURCE_PATTERN = "/**/*.class";

    private ClassFileReader() {
        // 工具类，禁止实例化
    }

    /**
     * 获取指定包下的所有类
     *
     * @param packageName 包名（例如：{@code com.example.service}）
     * @return 类集合，如果包不存在或扫描失败则返回空集合
     */
    public static Set<Class<?>> getClasses(String packageName) {
        Set<Class<?>> classes = new HashSet<>();

        if (!StringUtils.hasText(packageName)) {
            LOGGER.debug("Package name is empty, skip scanning");
            return classes;
        }

        String packageSearchPath = CLASSPATH_ALL_URL_PREFIX
                + packageName.replace('.', '/')
                + DEFAULT_RESOURCE_PATTERN;

        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(packageSearchPath);
            MetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory();

            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    loadClass(resource, metadataReaderFactory, classes);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to scan classes from package: {}", packageName, e);
        }

        return classes;
    }

    /**
     * 从资源加载类
     */
    private static void loadClass(Resource resource, MetadataReaderFactory metadataReaderFactory,
                                  Set<Class<?>> classes) {
        try {
            MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
            String className = metadataReader.getClassMetadata().getClassName();

            Class<?> clazz = ClassUtils.forName(className, ClassFileReader.class.getClassLoader());
            classes.add(clazz);

            LOGGER.trace("Loaded class: {} from resource: {}", className, resource.getFilename());
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Failed to load class from resource: {}", resource.getFilename(), e);
        } catch (IOException e) {
            LOGGER.warn("Failed to read metadata from resource: {}", resource.getFilename(), e);
        }
    }
}
