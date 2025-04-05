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

import java.util.function.Predicate;

import static java.util.stream.Stream.of;

/**
 * The utilities class for Java {@link Predicate}
 *
 * @since 2.7.5
 */
// 谓词工具类，提供常用的谓词操作
public interface Predicates {

    // 空谓词数组常量
    Predicate[] EMPTY_ARRAY = new Predicate[0];

    /**
     * 总是返回true的谓词
     * @param <T> 测试类型
     * @return 总是返回true的谓词
     */
    static <T> Predicate<T> alwaysTrue() {
        return e -> true;
    }

    /**
     * 总是返回false的谓词
     * @param <T> 测试类型
     * @return 总是返回false的谓词
     */
    static <T> Predicate<T> alwaysFalse() {
        return e -> false;
    }

    /**
     * 多个谓词的逻辑与组合(短路与)
     * @param predicates 要组合的谓词数组
     * @param <T> 测试类型
     * @return 组合后的谓词
     */
    static <T> Predicate<T> and(Predicate<T>... predicates) {
        return of(predicates).reduce((a, b) -> a.and(b)).orElseGet(Predicates::alwaysTrue);
    }

    /**
     * 多个谓词的逻辑或组合(短路或)
     * @param predicates 要组合的谓词数组
     * @param <T> 测试类型
     * @return 组合后的谓词
     */
    static <T> Predicate<T> or(Predicate<T>... predicates) {
        return of(predicates).reduce((a, b) -> a.or(b)).orElse(e -> true);
    }

}
