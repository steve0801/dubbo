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
package org.apache.dubbo.rpc.protocol.dubbo.status;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.status.Status;
import org.apache.dubbo.common.status.StatusChecker;
import org.apache.dubbo.common.store.DataStore;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ThreadPoolStatusChecker
 */
/**
 * ThreadPoolStatusChecker
 * 该类用于检查线程池的状态，实现了StatusChecker接口
 */
@Activate
public class ThreadPoolStatusChecker implements StatusChecker {

    /**
     * 检查线程池的状态
     * @return 返回一个包含线程池状态信息的Status对象
     */
    @Override
    public Status check() {
        // 通过扩展加载器获取DataStore的默认扩展实例
        DataStore dataStore = ExtensionLoader.getExtensionLoader(DataStore.class).getDefaultExtension();
        // 从DataStore中获取所有的执行器服务
        Map<String, Object> executors = dataStore.get(CommonConstants.EXECUTOR_SERVICE_COMPONENT_KEY);

        // 用于存储线程池状态信息的字符串构建器
        StringBuilder msg = new StringBuilder();
        // 初始状态为OK
        Status.Level level = Status.Level.OK;
        // 遍历所有执行器服务
        for (Map.Entry<String, Object> entry : executors.entrySet()) {
            // 获取服务端口
            String port = entry.getKey();
            // 获取执行器服务
            ExecutorService executor = (ExecutorService) entry.getValue();

            // 如果执行器服务是线程池执行器
            if (executor instanceof ThreadPoolExecutor) {
                // 将执行器服务转换为线程池执行器
                ThreadPoolExecutor tp = (ThreadPoolExecutor) executor;
                // 检查活动线程数是否小于最大线程数减1
                boolean ok = tp.getActiveCount() < tp.getMaximumPoolSize() - 1;
                // 初始状态为OK
                Status.Level lvl = Status.Level.OK;
                // 如果活动线程数不小于最大线程数减1，将状态设置为WARN
                if (!ok) {
                    level = Status.Level.WARN;
                    lvl = Status.Level.WARN;
                }

                // 如果已经有线程池状态信息，添加分隔符
                if (msg.length() > 0) {
                    msg.append(';');
                }
                // 记录线程池状态信息
                msg.append("Pool status:").append(lvl).append(", max:").append(tp.getMaximumPoolSize()).append(", core:")
                        .append(tp.getCorePoolSize()).append(", largest:").append(tp.getLargestPoolSize()).append(", active:")
                        .append(tp.getActiveCount()).append(", task:").append(tp.getTaskCount()).append(", service port: ").append(port);
            }
        }
        // 如果没有线程池状态信息，返回未知状态；否则返回包含状态信息的Status对象
        return msg.length() == 0 ? new Status(Status.Level.UNKNOWN) : new Status(level, msg.toString());
    }

}
