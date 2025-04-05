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
package org.apache.dubbo.common.logger;

import org.apache.dubbo.common.logger.jcl.JclLoggerAdapter;
import org.apache.dubbo.common.logger.jdk.JdkLoggerAdapter;
import org.apache.dubbo.common.logger.log4j.Log4jLoggerAdapter;
import org.apache.dubbo.common.logger.log4j2.Log4j2LoggerAdapter;
import org.apache.dubbo.common.logger.slf4j.Slf4jLoggerAdapter;
import org.apache.dubbo.common.logger.support.FailsafeLogger;
import org.apache.dubbo.rpc.model.FrameworkModel;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Logger factory
 */
// 日志工厂类，用于获取和管理日志记录器
public class LoggerFactory {

    // 缓存日志记录器的并发Map
    private static final ConcurrentMap<String, FailsafeLogger> LOGGERS = new ConcurrentHashMap<>();
    // 当前使用的日志适配器
    private static volatile LoggerAdapter LOGGER_ADAPTER;

    // 静态初始化块，初始化日志适配器
    static {
        // 从系统属性获取指定的日志框架
        String logger = System.getProperty("dubbo.application.logger", "");
        switch (logger) {
            case "slf4j":
                setLoggerAdapter(new Slf4jLoggerAdapter());
                break;
            case "jcl":
                setLoggerAdapter(new JclLoggerAdapter());
                break;
            case "log4j":
                setLoggerAdapter(new Log4jLoggerAdapter());
                break;
            case "jdk":
                setLoggerAdapter(new JdkLoggerAdapter());
                break;
            case "log4j2":
                setLoggerAdapter(new Log4j2LoggerAdapter());
                break;
            default:
                // 默认按顺序尝试加载支持的日志框架
                List<Class<? extends LoggerAdapter>> candidates = Arrays.asList(
                        Log4jLoggerAdapter.class,
                        Slf4jLoggerAdapter.class,
                        Log4j2LoggerAdapter.class,
                        JclLoggerAdapter.class,
                        JdkLoggerAdapter.class
                );
                for (Class<? extends LoggerAdapter> clazz : candidates) {
                    try {
                        setLoggerAdapter(clazz.newInstance());
                        break;
                    } catch (Throwable ignored) {
                    }
                }
        }
    }

    // 私有构造方法，防止实例化
    private LoggerFactory() {
    }

    // 通过框架模型设置日志适配器
    public static void setLoggerAdapter(FrameworkModel frameworkModel, String loggerAdapter) {
        if (loggerAdapter != null && loggerAdapter.length() > 0) {
            setLoggerAdapter(frameworkModel.getExtensionLoader(LoggerAdapter.class).getExtension(loggerAdapter));
        }
    }

    /**
     * 设置日志适配器
     * @param loggerAdapter 日志适配器实例
     */
    public static void setLoggerAdapter(LoggerAdapter loggerAdapter) {
        if (loggerAdapter != null) {
            if (loggerAdapter == LOGGER_ADAPTER) {
                return;
            }
            // 初始化适配器
            loggerAdapter.getLogger(LoggerFactory.class.getName());
            LoggerFactory.LOGGER_ADAPTER = loggerAdapter;
            // 更新所有缓存的日志记录器
            for (Map.Entry<String, FailsafeLogger> entry : LOGGERS.entrySet()) {
                entry.getValue().setLogger(LOGGER_ADAPTER.getLogger(entry.getKey()));
            }
        }
    }

    /**
     * 获取类对应的日志记录器
     * @param key 类对象
     * @return 日志记录器实例
     */
    public static Logger getLogger(Class<?> key) {
        return LOGGERS.computeIfAbsent(key.getName(), name -> new FailsafeLogger(LOGGER_ADAPTER.getLogger(name)));
    }

    /**
     * 获取名称对应的日志记录器
     * @param key 日志记录器名称
     * @return 日志记录器实例
     */
    public static Logger getLogger(String key) {
        return LOGGERS.computeIfAbsent(key, k -> new FailsafeLogger(LOGGER_ADAPTER.getLogger(k)));
    }

    /**
     * 获取当前日志级别
     * @return 日志级别
     */
    public static Level getLevel() {
        return LOGGER_ADAPTER.getLevel();
    }

    /**
     * 设置日志级别
     * @param level 要设置的日志级别
     */
    public static void setLevel(Level level) {
        LOGGER_ADAPTER.setLevel(level);
    }

    /**
     * 获取日志文件
     * @return 日志文件对象
     */
    public static File getFile() {
        return LOGGER_ADAPTER.getFile();
    }
}
