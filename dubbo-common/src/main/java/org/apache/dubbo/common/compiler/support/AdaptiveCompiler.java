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
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ScopeModelAware;

/**
 * AdaptiveCompiler. (SPI, Singleton, ThreadSafe)
 */
// 使用@Adaptive注解，表示这是一个自适应的实现类
@Adaptive
public class AdaptiveCompiler implements Compiler, ScopeModelAware {
    // 定义一个FrameworkModel对象，用于存储框架模型
    private FrameworkModel frameworkModel;

    // 实现ScopeModelAware接口的setFrameworkModel方法
    @Override
    public void setFrameworkModel(FrameworkModel frameworkModel) {
        // 设置frameworkModel
        this.frameworkModel = frameworkModel;
    }

    // 定义一个volatile类型的静态变量DEFAULT_COMPILER，用于存储默认编译器名称
    private static volatile String DEFAULT_COMPILER;

    // 提供一个静态方法来设置默认编译器名称
    public static void setDefaultCompiler(String compiler) {
        // 设置默认编译器名称
        DEFAULT_COMPILER = compiler;
    }

    // 重写Compiler接口的compile方法
    @Override
    public Class<?> compile(String code, ClassLoader classLoader) {
        // 声明一个Compiler对象
        Compiler compiler;
        // 获取Compiler的扩展加载器
        ExtensionLoader<Compiler> loader = frameworkModel.getExtensionLoader(Compiler.class);
        // 复制DEFAULT_COMPILER的引用
        String name = DEFAULT_COMPILER; // copy reference
        // 如果name不为空且长度大于0，则获取指定名称的Compiler扩展
        if (name != null && name.length() > 0) {
            compiler = loader.getExtension(name);
        } else {
            // 否则，获取默认的Compiler扩展
            compiler = loader.getDefaultExtension();
        }
        // 调用获取到的Compiler对象的compile方法进行编译
        return compiler.compile(code, classLoader);
    }

}
