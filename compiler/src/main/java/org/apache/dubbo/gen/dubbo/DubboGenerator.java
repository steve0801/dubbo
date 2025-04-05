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
package org.apache.dubbo.gen.dubbo;

import org.apache.dubbo.gen.AbstractGenerator;

import com.salesforce.jprotoc.ProtocPlugin;

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
package org.apache.dubbo.gen.dubbo;

import org.apache.dubbo.gen.AbstractGenerator;

import com.salesforce.jprotoc.ProtocPlugin;

// Dubbo生成器类，继承自AbstractGenerator
public class DubboGenerator extends AbstractGenerator {

    // 主方法，程序入口
    public static void main(String[] args) {
        // 如果没有参数，则正常生成代码
        if (args.length == 0) {
            ProtocPlugin.generate(new DubboGenerator());
        } else {
            // 如果有参数，则进入调试模式
            ProtocPlugin.debug(new DubboGenerator(), args[0]);
        }
    }

    // 获取类前缀
    @Override
    protected String getClassPrefix() {
        return "";
    }

    // 获取类后缀
    @Override
    protected String getClassSuffix() {
        return "Dubbo";
    }
}
