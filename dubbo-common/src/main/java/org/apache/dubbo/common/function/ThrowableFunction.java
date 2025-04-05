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
package org.apache.dubbo.common.function;

import java.util.function.Function;

/**
 * {@link Function} with {@link Throwable}
 *
 * @param <T> the source type
 * @param <R> the return type
 * @see Function
 * @see Throwable
 * @since 2.7.5
 */
// 可抛出异常的函数式接口
@FunctionalInterface
public interface ThrowableFunction<T, R> {

    /**
     * 对给定参数应用此函数
     * @param t 函数参数
     * @return 函数结果
     * @throws Throwable 执行过程中遇到的错误
     */
    R apply(T t) throws Throwable;

    /**
     * 执行ThrowableFunction
     * @param t 函数参数
     * @return 函数结果
     * @throws RuntimeException 包装Throwable异常
     */
    default R execute(T t) throws RuntimeException {
        R result = null;
        try {
            result = apply(t);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * 执行ThrowableFunction
     * @param t 函数参数
     * @param function ThrowableFunction实例
     * @param <T> 源类型
     * @param <R> 返回类型
     * @return 执行后的结果
     */
    static <T, R> R execute(T t, ThrowableFunction<T, R> function) {
        return function.execute(t);
    }
}
