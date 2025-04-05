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
package org.apache.dubbo.gen;

import com.google.common.base.Strings;
import com.google.common.html.HtmlEscapers;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.compiler.PluginProtos;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtoTypeMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
package org.apache.dubbo.gen;

import com.google.common.base.Strings;
import com.google.common.html.HtmlEscapers;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.compiler.PluginProtos;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtoTypeMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 抽象生成器基类，继承自Generator
public abstract class AbstractGenerator extends Generator {

    // 服务路径数量常量
    private static final int SERVICE_NUMBER_OF_PATHS = 2;
    // 方法路径数量常量
    private static final int METHOD_NUMBER_OF_PATHS = 4;

    // 获取类前缀的抽象方法
    protected abstract String getClassPrefix();

    // 获取类后缀的抽象方法
    protected abstract String getClassSuffix();

    // 获取单个模板文件名
    protected String getSingleTemplateFileName () {
        return getTemplateFileName();
    }

    // 获取模板文件名
    protected String getTemplateFileName () {
        return getClassPrefix() + getClassSuffix() + "Stub.mustache";
    }

    // 获取接口模板文件名
    protected String getInterfaceTemplateFileName () {
        return getClassPrefix() + getClassSuffix() + "InterfaceStub.mustache";
    }

    // 获取服务JavaDoc前缀
    private String getServiceJavaDocPrefix() {
        return "    ";
    }

    // 获取方法JavaDoc前缀
    private String getMethodJavaDocPrefix() {
        return "        ";
    }

    // 生成文件的主方法
    @Override
    public List<PluginProtos.CodeGeneratorResponse.File> generateFiles(PluginProtos.CodeGeneratorRequest request) throws GeneratorException {
        // 创建Proto类型映射
        final ProtoTypeMap typeMap = ProtoTypeMap.of(request.getProtoFileList());

        // 过滤需要生成的proto文件
        List<FileDescriptorProto> protosToGenerate = request.getProtoFileList().stream()
                .filter(protoFile -> request.getFileToGenerateList().contains(protoFile.getName()))
                .collect(Collectors.toList());

        // 查找服务并生成文件
        List<ServiceContext> services = findServices(protosToGenerate, typeMap);
        return generateFiles(services);
    }

    // 查找服务并构建服务上下文
    private List<ServiceContext> findServices(List<FileDescriptorProto> protos, ProtoTypeMap typeMap) {
        List<ServiceContext> contexts = new ArrayList<>();

        // 遍历所有proto文件
        protos.forEach(fileProto -> {
            // 遍历文件中的每个服务
            for (int serviceNumber = 0; serviceNumber < fileProto.getServiceCount(); serviceNumber++) {
                // 构建服务上下文
                ServiceContext serviceContext = buildServiceContext(
                    fileProto.getService(serviceNumber),
                    typeMap,
                    fileProto.getSourceCodeInfo().getLocationList(),
                    serviceNumber
                );
                // 设置服务上下文属性
                serviceContext.protoName = fileProto.getName();
                serviceContext.packageName = extractPackageName(fileProto);
                serviceContext.commonPackageName = extractCommonPackageName(fileProto);
                serviceContext.multipleFiles = fileProto.getOptions() != null && fileProto.getOptions().getJavaMultipleFiles();
                contexts.add(serviceContext);
            }
        });

        return contexts;
    }

    // 提取包名
    private String extractPackageName(FileDescriptorProto proto) {
        FileOptions options = proto.getOptions();
        if (options != null) {
            String javaPackage = options.getJavaPackage();
            if (!Strings.isNullOrEmpty(javaPackage)) {
                return javaPackage;
            }
        }

        return Strings.nullToEmpty(proto.getPackage());
    }

    // 提取通用包名
    private String extractCommonPackageName(FileDescriptorProto proto) {
        return Strings.nullToEmpty(proto.getPackage());
    }

    // 构建服务上下文
    private ServiceContext buildServiceContext(ServiceDescriptorProto serviceProto, ProtoTypeMap typeMap, List<Location> locations, int serviceNumber) {
        ServiceContext serviceContext = new ServiceContext();
        // 设置文件名
        serviceContext.fileName = getClassPrefix() + serviceProto.getName() + getClassSuffix() + ".java";
        // 设置类名
        serviceContext.className = getClassPrefix() + serviceProto.getName() + getClassSuffix();

        // 设置接口文件名和类名
        serviceContext.interfaceFileName = serviceProto.getName()+ ".java";
        serviceContext.interfaceClassName = serviceProto.getName();
        // 设置服务名
        serviceContext.serviceName = serviceProto.getName();
        // 设置是否弃用
        serviceContext.deprecated = serviceProto.getOptions() != null && serviceProto.getOptions().getDeprecated();

        // 过滤服务的所有位置信息
        List<Location> allLocationsForService = locations.stream()
                .filter(location ->
                    location.getPathCount() >= 2 &&
                       location.getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER &&
                       location.getPath(1) == serviceNumber
                )
                .collect(Collectors.toList());

        // 获取服务的位置信息
        Location serviceLocation = allLocationsForService.stream()
                .filter(location -> location.getPathCount() == SERVICE_NUMBER_OF_PATHS)
                .findFirst()
                .orElseGet(Location::getDefaultInstance);
        // 生成JavaDoc
        serviceContext.javaDoc = getJavaDoc(getComments(serviceLocation), getServiceJavaDocPrefix());

        // 遍历服务中的所有方法
        for (int methodNumber = 0; methodNumber < serviceProto.getMethodCount(); methodNumber++) {
            // 构建方法上下文
            MethodContext methodContext = buildMethodContext(
                serviceProto.getMethod(methodNumber),
                typeMap,
                locations,
                methodNumber
            );

            // 添加方法上下文
            serviceContext.methods.add(methodContext);
            // 添加方法类型
            serviceContext.methodTypes.add(methodContext.inputType);
            serviceContext.methodTypes.add(methodContext.outputType);
        }
        return serviceContext;
    }

    // 构建方法上下文
    private MethodContext buildMethodContext(MethodDescriptorProto methodProto, ProtoTypeMap typeMap, List<Location> locations, int methodNumber) {
        MethodContext methodContext = new MethodContext();
        // 设置方法名（首字母小写）
        methodContext.methodName = lowerCaseFirst(methodProto.getName());
        // 设置输入输出类型
        methodContext.inputType = typeMap.toJavaTypeName(methodProto.getInputType());
        methodContext.outputType = typeMap.toJavaTypeName(methodProto.getOutputType());
        // 设置是否弃用
        methodContext.deprecated = methodProto.getOptions() != null && methodProto.getOptions().getDeprecated();
        // 设置是否为多输入/输出
        methodContext.isManyInput = methodProto.getClientStreaming();
        methodContext.isManyOutput = methodProto.getServerStreaming();
        // 设置方法编号
        methodContext.methodNumber = methodNumber;

        // 获取方法的位置信息
        Location methodLocation = locations.stream()
                .filter(location ->
                    location.getPathCount() == METHOD_NUMBER_OF_PATHS &&
                        location.getPath(METHOD_NUMBER_OF_PATHS - 1) == methodNumber
                )
                .findFirst()
                .orElseGet(Location::getDefaultInstance);
        // 生成JavaDoc
        methodContext.javaDoc = getJavaDoc(getComments(methodLocation), getMethodJavaDocPrefix());

        // 根据方法类型设置调用方法名
        if (!methodProto.getClientStreaming() && !methodProto.getServerStreaming()) {
            methodContext.reactiveCallsMethodName = "oneToOne";
            methodContext.grpcCallsMethodName = "asyncUnaryCall";
        }
        if (!methodProto.getClientStreaming() && methodProto.getServerStreaming()) {
            methodContext.reactiveCallsMethodName = "oneToMany";
            methodContext.grpcCallsMethodName = "asyncServerStreamingCall";
        }
        if (methodProto.getClientStreaming() && !methodProto.getServerStreaming()) {
            methodContext.reactiveCallsMethodName = "manyToOne";
            methodContext.grpcCallsMethodName = "asyncClientStreamingCall";
        }
        if (methodProto.getClientStreaming() && methodProto.getServerStreaming()) {
            methodContext.reactiveCallsMethodName = "manyToMany";
            methodContext.grpcCallsMethodName = "asyncBidiStreamingCall";
        }
        return methodContext;
    }

    // 将字符串首字母小写
    private String lowerCaseFirst(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    // 生成所有服务文件
    private List<PluginProtos.CodeGeneratorResponse.File> generateFiles(List<ServiceContext> services) {
        List<PluginProtos.CodeGeneratorResponse.File> allServiceFiles = new ArrayList<>();
        // 遍历所有服务上下文
        for (ServiceContext context : services) {
            // 构建文件
            List<PluginProtos.CodeGeneratorResponse.File> files = buildFile(context);
            allServiceFiles.addAll(files);
        }
        return allServiceFiles;
    }

    // 构建单个服务文件
    private List<PluginProtos.CodeGeneratorResponse.File> buildFile(ServiceContext context) {
        List<PluginProtos.CodeGeneratorResponse.File> files = new ArrayList<>();

        // 如果允许多文件
        if (context.multipleFiles) {
            // 应用模板生成内容
            String content = applyTemplate(getTemplateFileName(), context);
            String dir = absoluteDir(context);

            // 添加主文件
            files.add(PluginProtos.CodeGeneratorResponse.File
                .newBuilder()
                .setName(getFileName(dir, context.fileName))
                .setContent(content)
                .build());

            // 应用接口模板生成内容
            content = applyTemplate(getInterfaceTemplateFileName(), context);
            // 添加接口文件
            files.add(PluginProtos.CodeGeneratorResponse.File
                .newBuilder()
                .setName(getFileName(dir, context.interfaceFileName))
                .setContent(content)
                .build());
        } else {
            // 单文件模式
            String content = applyTemplate(getSingleTemplateFileName(), context);
            String dir = absoluteDir(context);

            // 添加文件
            files.add(PluginProtos.CodeGeneratorResponse.File
                .newBuilder()
                .setName(getFileName(dir, context.fileName))
                .setContent(content)
                .build());
        }

        return files;
    }

    // 获取绝对目录路径
    private String absoluteDir(ServiceContext ctx) {
        return ctx.packageName.replace('.', '/');
//        if (Strings.isNullOrEmpty(dir)) {
//            return ctx.fileName;
//        } else {
//            return dir + "/" + ctx.fileName;
//        }if ()
    }

    // 获取文件名
    private String getFileName(String dir, String fileName) {
        if (Strings.isNullOrEmpty(dir)) {
            return fileName;
        }
        return dir + "/" + fileName;
    }

    // 获取注释内容
    private String getComments(Location location) {
        return location.getLeadingComments().isEmpty() ? location.getTrailingComments() : location.getLeadingComments();
    }

    // 生成JavaDoc
    private String getJavaDoc(String comments, String prefix) {
        if (!comments.isEmpty()) {
            StringBuilder builder = new StringBuilder("/**\n")
                    .append(prefix).append(" * <pre>\n");
            Arrays.stream(HtmlEscapers.htmlEscaper().escape(comments).split("\n"))
                    .map(line -> line.replace("*/", "&#42;&#47;").replace("*", "&#42;"))
                    .forEach(line -> builder.append(prefix).append(" * ").append(line).append("\n"));
            builder
                    .append(prefix).append(" * </pre>\n")
                    .append(prefix).append(" */");
            return builder.toString();
        }
        return null;
    }

    /**
     * Template class for proto Service objects.
     */
    private class ServiceContext {
        // CHECKSTYLE DISABLE VisibilityModifier FOR 8 LINES
        public String fileName;
        public String interfaceFileName;
        public String protoName;
        public String packageName;
        public String commonPackageName;
        public String className;
        public String interfaceClassName;
        public String serviceName;
        public boolean deprecated;
        public String javaDoc;
        public boolean multipleFiles;
        public List<MethodContext> methods = new ArrayList<>();

        public Set<String> methodTypes = new HashSet<>();

        // 获取单向请求方法
        public List<MethodContext> unaryRequestMethods() {
            return methods.stream().filter(m -> !m.isManyInput).collect(Collectors.toList());
        }

        // 获取单向方法
        public List<MethodContext> unaryMethods() {
            return methods.stream().filter(m -> (!m.isManyInput && !m.isManyOutput)).collect(Collectors.toList());
        }

        // 获取服务器流式方法
        public List<MethodContext> serverStreamingMethods() {
            return methods.stream().filter(m -> !m.isManyInput && m.isManyOutput).collect(Collectors.toList());
        }

        // 获取双向流式方法
        public List<MethodContext> biStreamingMethods() {
            return methods.stream().filter(m -> m.isManyInput).collect(Collectors.toList());
        }
    }

    /**
     * Template class for proto RPC objects.
     */
    private class MethodContext {
        // CHECKSTYLE DISABLE VisibilityModifier FOR 10 LINES
        public String methodName;
        public String inputType;
        public String outputType;
        public boolean deprecated;
        public boolean isManyInput;
        public boolean isManyOutput;
        public String reactiveCallsMethodName;
        public String grpcCallsMethodName;
        public int methodNumber;
        public String javaDoc;

        // 将方法名转换为大写加下划线格式
        public String methodNameUpperUnderscore() {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < methodName.length(); i++) {
                char c = methodName.charAt(i);
                s.append(Character.toUpperCase(c));
                if ((i < methodName.length() - 1) && Character.isLowerCase(c) && Character.isUpperCase(methodName.charAt(i + 1))) {
                    s.append('_');
                }
            }
            return s.toString();
        }

        // 将方法名转换为PascalCase格式
        public String methodNamePascalCase() {
            String mn = methodName.replace("_", "");
            return String.valueOf(Character.toUpperCase(mn.charAt(0))) + mn.substring(1);
        }

        // 将方法名转换为camelCase格式
        public String methodNameCamelCase() {
            String mn = methodName.replace("_", "");
            return String.valueOf(Character.toLowerCase(mn.charAt(0))) + mn.substring(1);
        }
    }
}

