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
 * A function interface for action with {@link Throwable}
 *
 * @see Function
 * @see Throwable
 * @since 2.7.5
 */
// 可抛出异常的函数式接口
@FunctionalInterface
public interface ThrowableAction {

    /**
     * 执行操作，可能抛出异常
     * @throws Throwable 执行过程中遇到的错误
     */
    void execute() throws Throwable;

    /**
     * 执行ThrowableAction操作
     * @param action 要执行的ThrowableAction
     * @throws RuntimeException 将异常包装为RuntimeException抛出
     */
    static void execute(ThrowableAction action) throws RuntimeException {
        try {
            action.execute();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
