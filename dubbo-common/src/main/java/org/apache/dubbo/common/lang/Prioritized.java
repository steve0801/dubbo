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
package org.apache.dubbo.common.lang;

import java.util.Comparator;

import static java.lang.Integer.compare;

/**
 * {@code Prioritized} interface can be implemented by objects that
 * should be sorted, for example the tasks in executable queue.
 *
 * @since 2.7.5
 */
// 优先级接口，定义优先级比较相关功能
public interface Prioritized extends Comparable<Prioritized> {

    /**
     * 优先级比较器
     * 比较规则：
     * 1. 如果只有one实现了Prioritized，返回-1
     * 2. 如果只有two实现了Prioritized，返回1
     * 3. 如果都实现了Prioritized，调用compareTo方法比较
     * 4. 如果都没实现Prioritized，返回0
     */
    Comparator<Object> COMPARATOR = (one, two) -> {
        boolean b1 = one instanceof Prioritized;
        boolean b2 = two instanceof Prioritized;
        if (b1 && !b2) {        // one实现了Prioritized，two没有
            return -1;
        } else if (b2 && !b1) { // two实现了Prioritized，one没有
            return 1;
        } else if (b1 && b2) {  // 两者都实现了Prioritized
            return ((Prioritized) one).compareTo((Prioritized) two);
        } else {                // 都没有实现Prioritized
            return 0;
        }
    };

    /**
     * 最高优先级
     */
    int MAX_PRIORITY = Integer.MIN_VALUE;

    /**
     * 最低优先级
     */
    int MIN_PRIORITY = Integer.MAX_VALUE;

    /**
     * 普通优先级
     */
    int NORMAL_PRIORITY = 0;

    /**
     * 获取优先级
     * @return 默认返回普通优先级
     */
    default int getPriority() {
        return NORMAL_PRIORITY;
    }

    /**
     * 比较优先级
     * @param that 要比较的对象
     * @return 比较结果
     */
    @Override
    default int compareTo(Prioritized that) {
        return compare(this.getPriority(), that.getPriority());
    }
}
