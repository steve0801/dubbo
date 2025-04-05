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


import javassist.CtClass;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavassistCompiler. (SPI, Singleton, ThreadSafe)
 */
public class JavassistCompiler extends AbstractCompiler {

    // 导入语句的正则表达式模式
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+([\\w\\.\\*]+);\n");

    // 继承语句的正则表达式模式
    private static final Pattern EXTENDS_PATTERN = Pattern.compile("\\s+extends\\s+([\\w\\.]+)[^\\{]*\\{\n");

    // 实现接口语句的正则表达式模式
    private static final Pattern IMPLEMENTS_PATTERN = Pattern.compile("\\s+implements\\s+([\\w\\.]+)\\s*\\{\n");

    // 方法定义的正则表达式模式
    private static final Pattern METHODS_PATTERN = Pattern.compile("\n(private|public|protected)\\s+");

    // 字段定义的正则表达式模式
    private static final Pattern FIELD_PATTERN = Pattern.compile("[^\n]+=[^\n]+;");

    // 编译源代码并返回编译后的类
    @Override
    public Class<?> doCompile(ClassLoader classLoader, String name, String source) throws Throwable {
        // 创建 CtClassBuilder 实例
        CtClassBuilder builder = new CtClassBuilder();
        // 设置类名
        builder.setClassName(name);

        // 处理导入的类
        Matcher matcher = IMPORT_PATTERN.matcher(source);
        while (matcher.find()) {
            // 添加导入的包
            builder.addImports(matcher.group(1).trim());
        }

        // 处理继承的父类
        matcher = EXTENDS_PATTERN.matcher(source);
        if (matcher.find()) {
            // 设置父类名
            builder.setSuperClassName(matcher.group(1).trim());
        }

        // 处理实现的接口
        matcher = IMPLEMENTS_PATTERN.matcher(source);
        if (matcher.find()) {
            // 获取所有实现的接口，并添加到 builder 中
            String[] ifaces = matcher.group(1).trim().split("\\,");
            Arrays.stream(ifaces).forEach(i -> builder.addInterface(i.trim()));
        }

        // 处理构造函数、字段和方法
        String body = source.substring(source.indexOf('{') + 1, source.length() - 1);
        String[] methods = METHODS_PATTERN.split(body);
        String className = ClassUtils.getSimpleClassName(name);
        Arrays.stream(methods).map(String::trim).filter(m -> !m.isEmpty()).forEach(method -> {
            if (method.startsWith(className)) {
                // 添加构造函数
                builder.addConstructor("public " + method);
            } else if (FIELD_PATTERN.matcher(method).matches()) {
                // 添加字段
                builder.addField("private " + method);
            } else {
                // 添加方法
                builder.addMethod("public " + method);
            }
        });

        // 编译构建好的 CtClass
        CtClass cls = builder.build(classLoader);
        // 将 CtClass 转换为 Class 对象
        return cls.toClass(classLoader, JavassistCompiler.class.getProtectionDomain());
    }

}
