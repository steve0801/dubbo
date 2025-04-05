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
package org.apache.dubbo.common.compiler.support;

import org.apache.dubbo.common.compiler.Compiler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract compiler. (SPI, Prototype, ThreadSafe)
 */
// 定义一个抽象类AbstractCompiler，实现Compiler接口
public abstract class AbstractCompiler implements Compiler {

    // 定义正则表达式模式，用于匹配包声明
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([$_a-zA-Z][$_a-zA-Z0-9\\.]*);");

    // 定义正则表达式模式，用于匹配类声明
    private static final Pattern CLASS_PATTERN = Pattern.compile("class\\s+([$_a-zA-Z][$_a-zA-Z0-9]*)\\s+");

    // 定义一个ConcurrentHashMap，用于存储正在创建的类的锁
    private static final Map<String, Lock> CLASS_IN_CREATION_MAP = new ConcurrentHashMap<>();

    // 重写compile方法
    @Override
    public Class<?> compile(String code, ClassLoader classLoader) {
        // 去除代码字符串两端的空白字符
        code = code.trim();
        // 创建一个Matcher对象，用于查找包声明
        Matcher matcher = PACKAGE_PATTERN.matcher(code);
        String pkg;
        // 如果找到包声明，则获取包名
        if (matcher.find()) {
            pkg = matcher.group(1);
        } else {
            // 如果没有找到包声明，则包名为空字符串
            pkg = "";
        }
        // 创建一个新的Matcher对象，用于查找类声明
        matcher = CLASS_PATTERN.matcher(code);
        String cls;
        // 如果找到类声明，则获取类名
        if (matcher.find()) {
            cls = matcher.group(1);
        } else {
            // 如果没有找到类声明，则抛出异常
            throw new IllegalArgumentException("No such class name in " + code);
        }
        // 根据包名和类名构建完整的类名
        String className = pkg != null && pkg.length() > 0 ? pkg + "." + cls : cls;
        // 获取当前类名对应的锁
        Lock lock = CLASS_IN_CREATION_MAP.get(className);
        // 如果锁为空，则尝试放入一个新的锁
        if (lock == null) {
            CLASS_IN_CREATION_MAP.putIfAbsent(className, new ReentrantLock());
            // 再次获取锁
            lock = CLASS_IN_CREATION_MAP.get(className);
        }
        try {
            // 加锁
            lock.lock();
            // 尝试通过类加载器加载类
            return Class.forName(className, true, classLoader);
        } catch (ClassNotFoundException e) {
            // 如果Java代码不以"}"结尾，则抛出异常
            if (!code.endsWith("}")) {
                throw new IllegalStateException("The java code not endsWith \"}\", code: \n" + code + "\n");
            }
            try {
                // 调用doCompile方法进行编译
                return doCompile(classLoader, className, code);
            } catch (RuntimeException t) {
                // 重新抛出运行时异常
                throw t;
            } catch (Throwable t) {
                // 如果编译失败，则抛出异常，并包含详细的错误信息
                throw new IllegalStateException("Failed to compile class, cause: " + t.getMessage() + ", class: " + className + ", code: \n" + code + "\n, stack: " + ClassUtils.toString(t));
            }
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    // 定义一个抽象方法doCompile，由子类实现具体的编译逻辑
    protected abstract Class<?> doCompile(ClassLoader classLoader, String name, String source) throws Throwable;

}
