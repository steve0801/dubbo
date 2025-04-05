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
package org.apache.dubbo.common.bytecode;

/**
 * NoSuchPropertyException.
 */

public class NoSuchPropertyException extends RuntimeException {
    // 定义序列化版本UID
    private static final long serialVersionUID = -2725364246023268766L;

    // 无参构造函数，调用父类的无参构造函数
    public NoSuchPropertyException() {
        super();
    }

    // 带有错误信息的构造函数，调用父类带有错误信息的构造函数
    public NoSuchPropertyException(String msg) {
        super(msg);
    }
}
