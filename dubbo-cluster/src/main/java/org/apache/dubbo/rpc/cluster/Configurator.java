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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.rpc.cluster.Constants.PRIORITY_KEY;

/**
 * Configurator. (SPI, Prototype, ThreadSafe)
 *
 */
// 配置器接口，继承自Comparable接口
public interface Configurator extends Comparable<Configurator> {

    // 获取配置器的URL
    URL getUrl();

    // 配置提供者URL，返回新的提供者URL
    URL configure(URL url);

    // 将覆盖URL列表转换为配置器列表
    static Optional<List<Configurator>> toConfigurators(List<URL> urls) {
        // 如果URL列表为空，返回空Optional
        if (CollectionUtils.isEmpty(urls)) {
            return Optional.empty();
        }

        // 获取配置器工厂的适配扩展
        ConfiguratorFactory configuratorFactory = urls.get(0).getOrDefaultApplicationModel().getExtensionLoader(ConfiguratorFactory.class)
                .getAdaptiveExtension();

        // 创建配置器列表
        List<Configurator> configurators = new ArrayList<>(urls.size());
        for (URL url : urls) {
            // 如果协议为空，清空配置器列表并跳出循环
            if (EMPTY_PROTOCOL.equals(url.getProtocol())) {
                configurators.clear();
                break;
            }
            // 获取URL参数并移除ANYHOST_KEY
            Map<String, String> override = new HashMap<>(url.getParameters());
            override.remove(ANYHOST_KEY);
            // 如果参数为空，跳过当前URL
            if (CollectionUtils.isEmptyMap(override)) {
                continue;
            }
            // 添加配置器到列表
            configurators.add(configuratorFactory.getConfigurator(url));
        }
        // 对配置器列表进行排序
        Collections.sort(configurators);
        return Optional.of(configurators);
    }

    // 比较方法，先按主机名比较，再按优先级比较
    @Override
    default int compareTo(Configurator o) {
        if (o == null) {
            return -1;
        }

        // 比较主机名
        int ipCompare = getUrl().getHost().compareTo(o.getUrl().getHost());
        // 如果主机名相同，比较优先级
        if (ipCompare == 0) {
            int i = getUrl().getParameter(PRIORITY_KEY, 0);
            int j = o.getUrl().getParameter(PRIORITY_KEY, 0);
            return Integer.compare(i, j);
        } else {
            return ipCompare;
        }
    }
}
