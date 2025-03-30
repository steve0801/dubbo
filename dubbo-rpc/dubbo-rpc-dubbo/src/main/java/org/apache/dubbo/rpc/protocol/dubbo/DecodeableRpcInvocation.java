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
package org.apache.dubbo.rpc.protocol.dubbo;


import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.serialize.Cleanable;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Codec;
import org.apache.dubbo.remoting.Decodeable;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.FrameworkServiceRepository;
import org.apache.dubbo.rpc.model.MethodDescriptor;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.model.ProviderModel;
import org.apache.dubbo.rpc.model.ServiceDescriptor;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.dubbo.common.BaseServiceMetadata.keyWithoutGroup;
import static org.apache.dubbo.common.URL.buildKey;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_VERSION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.Constants.SERIALIZATION_ID_KEY;
import static org.apache.dubbo.rpc.Constants.SERIALIZATION_SECURITY_CHECK_KEY;

/**
 * 这段Java代码定义了一个名为`DecodeableRpcInvocation`的类，该类继承自`RpcInvocation`并实现了`Codec`和`Decodeable`接口。这个类主要用于在Dubbo框架中对RPC调用进行解码操作。下面是对这段代码的详细解释：
 *
 * 1. **导入包**：
 *    - 代码开始处导入了多个包，这些包包含了日志、序列化、断言、工具类、通道、编解码器等相关的类和接口。
 *
 * 2. **类声明**：
 *    - `DecodeableRpcInvocation`类继承自`RpcInvocation`，并且实现了`Codec`和`Decodeable`接口。
 *    - 类中定义了一些成员变量，如`Logger`用于日志记录，`Channel`表示通信通道，`byte`类型的`serializationType`表示序列化类型，`InputStream`用于输入流，`Request`表示请求对象，`boolean hasDecoded`表示是否已经解码，`FrameworkModel frameworkModel`表示框架模型，`CallbackServiceCodec callbackServiceCodec`用于回调服务编解码。
 *
 * 3. **构造方法**：
 *    - 构造方法接受`FrameworkModel`、`Channel`、`Request`、`InputStream`和`byte id`作为参数。
 *    - 在构造方法中，通过`Assert.notNull`方法确保传入的参数不为空，并初始化相应的成员变量。
 *
 * 4. **decode方法**：
 *    - `decode`方法实现了`Decodeable`接口中的`decode`方法。
 *    - 该方法首先检查`hasDecoded`标志，如果未解码且通道和输入流不为空，则调用私有方法`decode`进行解码操作。
 *    - 如果解码过程中发生异常，会记录警告日志并将请求标记为损坏，同时设置请求数据为异常信息。
 *    - 最后，将`hasDecoded`标志设置为`true`，表示已经完成解码。
 *
 * 5. **encode方法**：
 *    - `encode`方法实现了`Codec`接口中的`encode`方法。
 *    - 该方法目前抛出`UnsupportedOperationException`异常，表示不支持编码操作。
 *
 * 6. **checkSerializationTypeFromRemote方法**：
 *    - 该方法目前是空实现，没有具体逻辑。
 *
 * 7. **decode(Channel channel, InputStream input)方法**：
 *    - 该方法从输入流中读取数据并进行解码。
 *    - 首先通过`CodecSupport.getSerialization`获取序列化器，并使用该序列化器反序列化输入流。
 *    - 从输入流中读取Dubbo版本号、路径、版本号、方法名和参数类型描述，并设置到当前对象的附件中。
 *    - 如果系统属性`SERIALIZATION_SECURITY_CHECK_KEY`为`true`，则调用`CodecSupport.checkSerialization`进行安全检查。
 *    - 最后，读取方法参数并存储在`args`数组中。
 *
 * 总结来说，`DecodeableRpcInvocation`类主要负责从输入流中解码RPC调用的相关信息，并将其存储在对象中以便后续处理。
 */
public class DecodeableRpcInvocation extends RpcInvocation implements Codec, Decodeable {

    private static final Logger log = LoggerFactory.getLogger(DecodeableRpcInvocation.class);

    private Channel channel;

    private byte serializationType;

    private InputStream inputStream;

    private Request request;

    private volatile boolean hasDecoded;

    protected final FrameworkModel frameworkModel;

    private CallbackServiceCodec callbackServiceCodec;

    public DecodeableRpcInvocation(FrameworkModel frameworkModel, Channel channel, Request request, InputStream is, byte id) {
        this.frameworkModel = frameworkModel;
        Assert.notNull(channel, "channel == null");
        Assert.notNull(request, "request == null");
        Assert.notNull(is, "inputStream == null");
        this.channel = channel;
        this.request = request;
        this.inputStream = is;
        this.serializationType = id;
        callbackServiceCodec = new CallbackServiceCodec(frameworkModel);
    }

    @Override
    public void decode() throws Exception {
        if (!hasDecoded && channel != null && inputStream != null) {
            try {
                decode(channel, inputStream);
            } catch (Throwable e) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode rpc invocation failed: " + e.getMessage(), e);
                }
                request.setBroken(true);
                request.setData(e);
            } finally {
                hasDecoded = true;
            }
        }
    }

    @Override
    public void encode(Channel channel, OutputStream output, Object message) throws IOException {
        throw new UnsupportedOperationException();
    }

    private void checkSerializationTypeFromRemote() {

    }

    @Override
    public Object decode(Channel channel, InputStream input) throws IOException {
        ObjectInput in = CodecSupport.getSerialization(channel.getUrl(), serializationType)
            .deserialize(channel.getUrl(), input);
        this.put(SERIALIZATION_ID_KEY, serializationType);

        String dubboVersion = in.readUTF();
        request.setVersion(dubboVersion);
        setAttachment(DUBBO_VERSION_KEY, dubboVersion);

        String path = in.readUTF();
        setAttachment(PATH_KEY, path);
        String version = in.readUTF();
        setAttachment(VERSION_KEY, version);

        setMethodName(in.readUTF());

        String desc = in.readUTF();
        setParameterTypesDesc(desc);

        ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            if (Boolean.parseBoolean(System.getProperty(SERIALIZATION_SECURITY_CHECK_KEY, "true"))) {
                CodecSupport.checkSerialization(frameworkModel.getServiceRepository(), path, version, serializationType);
            }
            Object[] args = DubboCodec.EMPTY_OBJECT_ARRAY;
            Class<?>[] pts = DubboCodec.EMPTY_CLASS_ARRAY;
            if (desc.length() > 0) {
//                if (RpcUtils.isGenericCall(path, getMethodName()) || RpcUtils.isEcho(path, getMethodName())) {
//                    pts = ReflectUtils.desc2classArray(desc);
//                } else {
                // TODO
                //  解码时：当provider端对请求进行解码时，会解析需要调用的方法签名，包括方法的参数类型、返回类型，
                //  现在这些内容都已经做了缓存，所以无需再重新解析，只要直接从ServiceRepository中获取即可
                FrameworkServiceRepository repository = frameworkModel.getServiceRepository();
                List<ProviderModel> providerModels = repository.lookupExportedServicesWithoutGroup(keyWithoutGroup(path, version));
                ServiceDescriptor serviceDescriptor = null;
                if (CollectionUtils.isNotEmpty(providerModels)) {
                    for (ProviderModel providerModel : providerModels) {
                        serviceDescriptor = providerModel.getServiceModel();
                        if (serviceDescriptor != null) {
                            break;
                        }
                    }
                }
                if (serviceDescriptor == null) {
                    // Unable to find ProviderModel from Exported Services
                    for (ApplicationModel applicationModel : frameworkModel.getApplicationModels()) {
                        for (ModuleModel moduleModel : applicationModel.getModuleModels()) {
                            serviceDescriptor = moduleModel.getServiceRepository().lookupService(path);
                            if (serviceDescriptor != null) {
                                break;
                            }
                        }
                    }
                }

                if (serviceDescriptor != null) {
                    MethodDescriptor methodDescriptor = serviceDescriptor.getMethod(getMethodName(), desc);
                    if (methodDescriptor != null) {
                        pts = methodDescriptor.getParameterClasses();
                        this.setReturnTypes(methodDescriptor.getReturnTypes());

                        // switch TCCL
                        if (CollectionUtils.isNotEmpty(providerModels)) {
                            if (providerModels.size() == 1) {
                                Thread.currentThread().setContextClassLoader(providerModels.get(0).getClassLoader());
                            } else {
                                // try all providerModels' classLoader can load pts, use the first one
                                for (ProviderModel providerModel : providerModels) {
                                    ClassLoader classLoader = providerModel.getClassLoader();
                                    boolean match = true;
                                    for (Class<?> pt : pts) {
                                        try {
                                            if (!pt.equals(classLoader.loadClass(pt.getName()))) {
                                                match = false;
                                            }
                                        } catch (ClassNotFoundException e) {
                                            match = false;
                                        }
                                    }
                                    if (match) {
                                        Thread.currentThread().setContextClassLoader(classLoader);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                if (pts == DubboCodec.EMPTY_CLASS_ARRAY) {
                    if (!RpcUtils.isGenericCall(desc, getMethodName()) && !RpcUtils.isEcho(desc, getMethodName())) {
                        throw new IllegalArgumentException("Service not found:" + path + ", " + getMethodName());
                    }
                    pts = ReflectUtils.desc2classArray(desc);
                }
//                }

                args = new Object[pts.length];
                for (int i = 0; i < args.length; i++) {
                    try {
                        args[i] = in.readObject(pts[i]);
                    } catch (Exception e) {
                        if (log.isWarnEnabled()) {
                            log.warn("Decode argument failed: " + e.getMessage(), e);
                        }
                    }
                }
            }
            setParameterTypes(pts);

            Map<String, Object> map = in.readAttachments();
            if (map != null && map.size() > 0) {
                Map<String, Object> attachment = getObjectAttachments();
                if (attachment == null) {
                    attachment = new HashMap<>();
                }
                attachment.putAll(map);
                setObjectAttachments(attachment);
            }

            //decode argument ,may be callback
            for (int i = 0; i < args.length; i++) {
                args[i] = callbackServiceCodec.decodeInvocationArgument(channel, this, pts, i, args[i]);
            }

            setArguments(args);
            String targetServiceName = buildKey((String) getAttachment(PATH_KEY),
                getAttachment(GROUP_KEY),
                getAttachment(VERSION_KEY));
            setTargetServiceUniqueName(targetServiceName);
        } catch (ClassNotFoundException e) {
            throw new IOException(StringUtils.toString("Read invocation data failed.", e));
        } finally {
            Thread.currentThread().setContextClassLoader(originClassLoader);
            if (in instanceof Cleanable) {
                ((Cleanable) in).cleanup();
            }
        }
        return this;
    }

}
