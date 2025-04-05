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

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CtClassBuilder is builder for CtClass
 * <p>
 * contains all the information, including:
 * <p>
 * class name, imported packages, super class name, implemented interfaces, constructors, fields, methods.
 */
public class CtClassBuilder {

    // 类名
    private String className;

    // 父类名，默认为 java.lang.Object
    private String superClassName = "java.lang.Object";

    // 导入的包列表
    private final List<String> imports = new ArrayList<>();

    // 完整类名映射表
    private final Map<String, String> fullNames = new HashMap<>();

    // 实现的接口列表
    private final List<String> ifaces = new ArrayList<>();

    // 构造函数列表
    private final List<String> constructors = new ArrayList<>();

    // 字段列表
    private final List<String> fields = new ArrayList<>();

    // 方法列表
    private final List<String> methods = new ArrayList<>();

    // 获取类名
    public String getClassName() {
        return className;
    }

    // 设置类名
    public void setClassName(String className) {
        this.className = className;
    }

    // 获取父类名
    public String getSuperClassName() {
        return superClassName;
    }

    // 设置父类名，并获取其全限定名
    public void setSuperClassName(String superClassName) {
        this.superClassName = getQualifiedClassName(superClassName);
    }

    // 获取导入的包列表
    public List<String> getImports() {
        return imports;
    }

    // 添加导入的包，同时处理完整类名映射
    public void addImports(String pkg) {
        int pi = pkg.lastIndexOf('.');
        if (pi > 0) {
            String pkgName = pkg.substring(0, pi);
            this.imports.add(pkgName);
            if (!pkg.endsWith(".*")) {
                fullNames.put(pkg.substring(pi + 1), pkg);
            }
        }
    }

    // 获取实现的接口列表
    public List<String> getInterfaces() {
        return ifaces;
    }

    // 添加实现的接口，并获取其全限定名
    public void addInterface(String iface) {
        this.ifaces.add(getQualifiedClassName(iface));
    }

    // 获取构造函数列表
    public List<String> getConstructors() {
        return constructors;
    }

    // 添加构造函数
    public void addConstructor(String constructor) {
        this.constructors.add(constructor);
    }

    // 获取字段列表
    public List<String> getFields() {
        return fields;
    }

    // 添加字段
    public void addField(String field) {
        this.fields.add(field);
    }

    // 获取方法列表
    public List<String> getMethods() {
        return methods;
    }

    // 添加方法
    public void addMethod(String method) {
        this.methods.add(method);
    }

    /**
     * 获取全限定类名
     *
     * @param className 超类名，可能是全限定或非全限定
     */
    protected String getQualifiedClassName(String className) {
        if (className.contains(".")) {
            return className;
        }

        if (fullNames.containsKey(className)) {
            return fullNames.get(className);
        }

        return ClassUtils.forName(imports.toArray(new String[0]), className).getName();
    }

    /**
     * 构建 CtClass 对象
     */
    public CtClass build(ClassLoader classLoader) throws NotFoundException, CannotCompileException {
        // 创建 ClassPool 实例
        ClassPool pool = new ClassPool(true);

        // 将 ClassLoader 插入到 ClassPath 中
        pool.insertClassPath(new LoaderClassPath(classLoader));

        // 创建类
        CtClass ctClass = pool.makeClass(className, pool.get(superClassName));

        // 添加导入的包
        imports.forEach(pool::importPackage);

        // 添加实现的接口
        for (String iface : ifaces) {
            ctClass.addInterface(pool.get(iface));
        }

        // 添加构造函数
        for (String constructor : constructors) {
            ctClass.addConstructor(CtNewConstructor.make(constructor, ctClass));
        }

        // 添加字段
        for (String field : fields) {
            ctClass.addField(CtField.make(field, ctClass));
        }

        // 添加方法
        for (String method : methods) {
            ctClass.addMethod(CtNewMethod.make(method, ctClass));
        }

        // 返回构建好的 CtClass 对象
        return ctClass;
    }

}
