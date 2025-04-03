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

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.status.Status;
import org.apache.dubbo.common.status.StatusChecker;
import org.apache.dubbo.remoting.RemotingServer;
import org.apache.dubbo.rpc.ProtocolServer;
import org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol;

import java.util.List;

/**
 * ServerStatusChecker
 */
/**
 * ServerStatusChecker
 * 该类用于检查Dubbo协议服务器的状态
 */
@Activate
public class ServerStatusChecker implements StatusChecker {

    /**
     * 检查所有Dubbo协议服务器的状态
     * @return 返回一个包含服务器状态信息的Status对象
     */
    @Override
    public Status check() {
        // 获取所有的Dubbo协议服务器
        List<ProtocolServer> servers = DubboProtocol.getDubboProtocol().getServers();
        // 如果没有服务器，则返回未知状态
        if (servers == null || servers.isEmpty()) {
            return new Status(Status.Level.UNKNOWN);
        }
        // 初始状态为OK
        Status.Level level = Status.Level.OK;
        // 用于存储服务器状态信息的字符串构建器
        StringBuilder buf = new StringBuilder();
        // 遍历所有服务器
        for (ProtocolServer protocolServer : servers) {
            // 获取远程服务器实例
            RemotingServer server = protocolServer.getRemotingServer();
            // 如果服务器未绑定，则将状态设置为ERROR并记录地址
            if (!server.isBound()) {
                level = Status.Level.ERROR;
                // 清空之前的状态信息
                buf.setLength(0);
                // 记录未绑定服务器的本地地址
                buf.append(server.getLocalAddress());
                // 发现未绑定的服务器，跳出循环
                break;
            }
            // 如果已经有服务器信息，添加分隔符
            if (buf.length() > 0) {
                buf.append(',');
            }
            // 记录服务器地址
            buf.append(server.getLocalAddress());
            buf.append("(clients:");
            // 记录连接到该服务器的客户端数量
            buf.append(server.getChannels().size());
            buf.append(')');
        }
        // 返回包含状态级别和详细信息的Status对象
        return new Status(level, buf.toString());
    }

}
