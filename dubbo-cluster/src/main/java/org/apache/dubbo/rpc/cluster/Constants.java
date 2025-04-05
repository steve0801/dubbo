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

// 集群相关常量接口
public interface Constants {

    // 失败重试任务键
    String FAIL_BACK_TASKS_KEY = "failbacktasks";

    // 默认失败重试任务数
    int DEFAULT_FAILBACK_TASKS = 100;

    // 默认并行调用数
    int DEFAULT_FORKS = 2;

    // 权重键
    String WEIGHT_KEY = "weight";

    // 默认权重值
    int DEFAULT_WEIGHT = 100;

    // Mock协议
    String MOCK_PROTOCOL = "mock";

    // 强制键
    String FORCE_KEY = "force";

    // 原始规则键
    String RAW_RULE_KEY = "rawRule";

    // 有效键
    String VALID_KEY = "valid";

    // 启用键
    String ENABLED_KEY = "enabled";

    // 动态键
    String DYNAMIC_KEY = "dynamic";

    // 范围键
    String SCOPE_KEY = "scope";

    // 键名
    String KEY_KEY = "key";

    // 条件键
    String CONDITIONS_KEY = "conditions";

    // 标签键
    String TAGS_KEY = "tags";

    /**
     * To decide whether to exclude unavailable invoker from the cluster
     */
    // 集群可用性检查键
    String CLUSTER_AVAILABLE_CHECK_KEY = "cluster.availablecheck";

    /**
     * The default value of cluster.availablecheck
     *
     * @see #CLUSTER_AVAILABLE_CHECK_KEY
     */
    // 默认集群可用性检查值
    boolean DEFAULT_CLUSTER_AVAILABLE_CHECK = true;

    /**
     * To decide whether to enable sticky strategy for cluster
     */
    // 集群粘性策略键
    String CLUSTER_STICKY_KEY = "sticky";

    /**
     * The default value of sticky
     *
     * @see #CLUSTER_STICKY_KEY
     */
    // 默认粘性策略值
    boolean DEFAULT_CLUSTER_STICKY = false;

    // 地址键
    String ADDRESS_KEY = "address";

    /**
     * When this attribute appears in invocation's attachment, mock invoker will be used
     */
    // 调用需要Mock键
    String INVOCATION_NEED_MOCK = "invocation.need.mock";

    /**
     * when ROUTER_KEY's value is set to ROUTER_TYPE_CLEAR, RegistryDirectory will clean all current routers
     */
    // 清除路由类型
    String ROUTER_TYPE_CLEAR = "clean";

    // 默认脚本类型键
    String DEFAULT_SCRIPT_TYPE_KEY = "javascript";

    // 优先级键
    String PRIORITY_KEY = "priority";

    // 规则键
    String RULE_KEY = "rule";

    // 类型键
    String TYPE_KEY = "type";

    // 运行时键
    String RUNTIME_KEY = "runtime";

    // 预热键
    String WARMUP_KEY = "warmup";

    // 默认预热时间
    int DEFAULT_WARMUP = 10 * 60 * 1000;

    // 配置版本键
    String CONFIG_VERSION_KEY = "configVersion";

    // 覆盖提供者键
    String OVERRIDE_PROVIDERS_KEY = "providerAddresses";

    /**
     * key for router type, for e.g., "script"/"file",  corresponding to ScriptRouterFactory.NAME, FileRouterFactory.NAME
     */
    // 路由键
    String ROUTER_KEY = "router";

    /**
     * The key for state router
     */
    // 状态路由键
    String STATE_ROUTER_KEY = "stateRouter";
    /**
     * The key name for reference URL in register center
     */
    // 引用键
    String REFER_KEY = "refer";

    // 属性键
    String ATTRIBUTE_KEY = "attribute";

    /**
     * The key name for export URL in register center
     */
    // 导出键
    String EXPORT_KEY = "export";

    // 对等键
    String PEER_KEY = "peer";

    // 消费者URL键
    String CONSUMER_URL_KEY = "CONSUMER_URL";

    /**
     * prefix of arguments router key
     */
    // 参数前缀
    String ARGUMENTS = "arguments";

    // 需要重新导出键
    String NEED_REEXPORT = "need-reexport";

    /**
     * The key of shortestResponseSlidePeriod
     */
    // 最短响应滑动周期键
    String SHORTEST_RESPONSE_SLIDE_PERIOD = "shortestResponseSlidePeriod";
}
