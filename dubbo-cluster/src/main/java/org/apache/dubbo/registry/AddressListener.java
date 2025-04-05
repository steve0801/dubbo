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
package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.rpc.cluster.Directory;

import java.util.List;


// 地址监听器接口，用于处理地址列表的变化
@SPI(scope = ExtensionScope.MODULE)
public interface AddressListener {

    /**
     * processing when receiving the address list
     *
     * @param addresses         provider address list
     * @param consumerUrl
     * @param registryDirectory
     */
    // 通知方法，当接收到地址列表时调用
    List<URL> notify(List<URL> addresses, URL consumerUrl, Directory registryDirectory);

    // 销毁方法，默认实现为空
    default void destroy(URL consumerUrl, Directory registryDirectory) {

    }

}
