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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.beans.support.InstantiationStrategy;
import org.apache.dubbo.common.config.Environment;
import org.apache.dubbo.common.context.ApplicationExt;
import org.apache.dubbo.common.context.Lifecycle;
import org.apache.dubbo.common.extension.support.ActivateComparator;
import org.apache.dubbo.common.extension.support.WrapperComparator;
import org.apache.dubbo.common.lang.Prioritized;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.ClassLoaderResourceLoader;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.NativeUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.model.ScopeModel;
import org.apache.dubbo.rpc.model.ScopeModelAccessor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.sort;
import static java.util.ServiceLoader.load;
import static java.util.stream.StreamSupport.stream;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOVE_VALUE_PREFIX;

/**
 * {@link org.apache.dubbo.rpc.model.ApplicationModel}, {@code DubboBootstrap} and this class are
 * at present designed to be singleton or static (by itself totally static or uses some static fields).
 * So the instances returned from them are of process or classloader scope. If you want to support
 * multiple dubbo servers in a single process, you may need to refactor these three classes.
 * <p>
 * Load dubbo extensions
 * <ul>
 * <li>auto inject dependency extension </li>
 * <li>auto wrap extension in wrapper </li>
 * <li>default extension is an adaptive instance</li>
 * </ul>
 *
 * @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/jar/jar.html#Service%20Provider">Service Provider in Java 5</a>
 * @see org.apache.dubbo.common.extension.SPI
 * @see org.apache.dubbo.common.extension.Adaptive
 * @see org.apache.dubbo.common.extension.Activate
 */
// Dubbo扩展点加载器核心类，负责加载和管理扩展点实现
public class ExtensionLoader<T> {

    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    // 名称分隔符正则表达式
    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");

    // 扩展点实例缓存
    private final ConcurrentMap<Class<?>, Object> extensionInstances = new ConcurrentHashMap<>(64);

    // 扩展点接口类型
    private final Class<?> type;

    // 扩展点注入器
    private final ExtensionInjector injector;

    // 扩展点类名缓存
    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<>();

    // 扩展点类缓存
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    // 激活扩展点缓存
    private final Map<String, Object> cachedActivates = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, Set<String>> cachedActivateGroups = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, String[]> cachedActivateValues = Collections.synchronizedMap(new LinkedHashMap<>());

    // 扩展点实例缓存
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();

    // 自适应扩展点实例缓存
    private final Holder<Object> cachedAdaptiveInstance = new Holder<>();

    // 自适应扩展点类缓存
    private volatile Class<?> cachedAdaptiveClass = null;

    // 默认扩展点名称
    private String cachedDefaultName;

    // 创建自适应实例错误
    private volatile Throwable createAdaptiveInstanceError;

    // 包装类缓存
    private Set<Class<?>> cachedWrapperClasses;

    // 异常信息缓存
    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<>();

    // 加载策略
    private static volatile LoadingStrategy[] strategies = loadLoadingStrategies();

    // 不可接受的异常集合
    private Set<String> unacceptableExceptions = new ConcurrentHashSet<>();

    // 扩展点目录
    private ExtensionDirector extensionDirector;

    // 扩展点后处理器列表
    private List<ExtensionPostProcessor> extensionPostProcessors;

    // 实例化策略
    private InstantiationStrategy instantiationStrategy;

    // 环境变量
    private Environment environment;

    // 激活比较器
    private ActivateComparator activateComparator;

    // 作用域模型
    private ScopeModel scopeModel;

    // 设置加载策略
    public static void setLoadingStrategies(LoadingStrategy... strategies) {
        if (ArrayUtils.isNotEmpty(strategies)) {
            ExtensionLoader.strategies = strategies;
        }
    }

    // 加载所有优先级的加载策略
    private static LoadingStrategy[] loadLoadingStrategies() {
        return stream(load(LoadingStrategy.class).spliterator(), false)
            .sorted()
            .toArray(LoadingStrategy[]::new);
    }

    // 获取所有加载策略
    public static List<LoadingStrategy> getLoadingStrategies() {
        return asList(strategies);
    }

    // 构造函数
    ExtensionLoader(Class<?> type, ExtensionDirector extensionDirector, ScopeModel scopeModel) {
        this.type = type;
        this.extensionDirector = extensionDirector;
        this.extensionPostProcessors = extensionDirector.getExtensionPostProcessors();
        initInstantiationStrategy();
        this.injector = (type == ExtensionInjector.class ? null : extensionDirector.getExtensionLoader(ExtensionInjector.class)
            .getAdaptiveExtension());
        this.activateComparator = new ActivateComparator(extensionDirector);
        this.scopeModel = scopeModel;
    }

    // 初始化实例化策略
    private void initInstantiationStrategy() {
        for (ExtensionPostProcessor extensionPostProcessor : extensionPostProcessors) {
            if (extensionPostProcessor instanceof ScopeModelAccessor) {
                instantiationStrategy = new InstantiationStrategy((ScopeModelAccessor) extensionPostProcessor);
                break;
            }
        }
        if (instantiationStrategy == null) {
            instantiationStrategy = new InstantiationStrategy();
        }
    }

    // 获取扩展点加载器(已废弃)
    @Deprecated
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        return ApplicationModel.defaultModel().getDefaultModule().getExtensionLoader(type);
    }

    // 重置扩展点加载器(测试用)
    public static void resetExtensionLoader(Class type) {
        // 实现代码省略...
    }

    // 销毁扩展点加载器
    public void destroy() {
        extensionInstances.forEach((type, instance) -> {
            if (instance instanceof Lifecycle) {
                try {
                    ((Lifecycle) instance).destroy();
                } catch (Exception e) {
                    logger.error("Error destroying extension " + instance, e);
                }
            }
        });
        extensionInstances.clear();
    }

    // 查找类加载器
    private static ClassLoader findClassLoader() {
        return ClassUtils.getClassLoader(ExtensionLoader.class);
    }

    // 获取扩展点名称
    public String getExtensionName(T extensionInstance) {
        return getExtensionName(extensionInstance.getClass());
    }

    // 获取扩展点类名
    public String getExtensionName(Class<?> extensionClass) {
        getExtensionClasses(); // 加载类
        return cachedNames.get(extensionClass);
    }

    // 获取激活的扩展点列表
    public List<T> getActivateExtension(URL url, String key) {
        return getActivateExtension(url, key, null);
    }

    // 获取激活的扩展点列表
    public List<T> getActivateExtension(URL url, String[] values) {
        return getActivateExtension(url, values, null);
    }

    // 获取激活的扩展点列表
    public List<T> getActivateExtension(URL url, String key, String group) {
        String value = url.getParameter(key);
        return getActivateExtension(url, StringUtils.isEmpty(value) ? null : COMMA_SPLIT_PATTERN.split(value), group);
    }

    // 核心方法：获取激活的扩展点列表
    public List<T> getActivateExtension(URL url, String[] values, String group) {
        // 实现代码省略...
    }

    // 获取所有激活的扩展点
    public List<T> getActivateExtensions() {
        // 实现代码省略...
    }

    // 判断是否匹配分组
    private boolean isMatchGroup(String group, Set<String> groups) {
        // 实现代码省略...
    }

    // 判断是否激活
    private boolean isActive(String[] keys, URL url) {
        // 实现代码省略...
    }

    // 获取已加载的扩展点
    public T getLoadedExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Holder<Object> holder = getOrCreateHolder(name);
        return (T) holder.get();
    }

    // 获取或创建Holder
    private Holder<Object> getOrCreateHolder(String name) {
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }
        return holder;
    }

    // 获取已加载的扩展点名称集合
    public Set<String> getLoadedExtensions() {
        return Collections.unmodifiableSet(new TreeSet<>(cachedInstances.keySet()));
    }

    // 获取已加载的扩展点实例列表
    public List<T> getLoadedExtensionInstances() {
        List<T> instances = new ArrayList<>();
        cachedInstances.values().forEach(holder -> instances.add((T) holder.get()));
        return instances;
    }

    // 获取已加载的自适应扩展点实例
    public Object getLoadedAdaptiveExtensionInstances() {
        return cachedAdaptiveInstance.get();
    }

    // 获取扩展点实例
    public T getExtension(String name) {
        T extension = getExtension(name, true);
        if (extension == null) {
            throw new IllegalArgumentException("Not find extension: " + name);
        }
        return extension;
    }

    // 获取扩展点实例(可选择是否包装)
    public T getExtension(String name, boolean wrap) {
        // 实现代码省略...
    }

    // 获取扩展点实例或默认实例
    public T getOrDefaultExtension(String name) {
        return containsExtension(name) ? getExtension(name) : getDefaultExtension();
    }

    // 获取默认扩展点实例
    public T getDefaultExtension() {
        getExtensionClasses();
        if (StringUtils.isBlank(cachedDefaultName) || "true".equals(cachedDefaultName)) {
            return null;
        }
        return getExtension(cachedDefaultName);
    }

    // 判断是否有指定名称的扩展点
    public boolean hasExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Class<?> c = this.getExtensionClass(name);
        return c != null;
    }

    // 获取支持的扩展点名称集合
    public Set<String> getSupportedExtensions() {
        Map<String, Class<?>> clazzes = getExtensionClasses();
        return Collections.unmodifiableSet(new TreeSet<>(clazzes.keySet()));
    }

    // 获取支持的扩展点实例集合
    public Set<T> getSupportedExtensionInstances() {
        List<T> instances = new LinkedList<>();
        Set<String> supportedExtensions = getSupportedExtensions();
        if (CollectionUtils.isNotEmpty(supportedExtensions)) {
            for (String name : supportedExtensions) {
                instances.add(getExtension(name));
            }
        }
        sort(instances, Prioritized.COMPARATOR);
        return new LinkedHashSet<>(instances);
    }

    // 获取默认扩展点名称
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return cachedDefaultName;
    }

    // 添加扩展点
    public void addExtension(String name, Class<?> clazz) {
        // 实现代码省略...
    }

    // 替换扩展点
    @Deprecated
    public void replaceExtension(String name, Class<?> clazz) {
        // 实现代码省略...
    }

    // 获取自适应扩展点实例
    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        // 实现代码省略...
    }

    // 查找异常信息
    private IllegalStateException findException(String name) {
        // 实现代码省略...
    }

    // 创建扩展点实例
    @SuppressWarnings("unchecked")
    private T createExtension(String name, boolean wrap) {
        // 实现代码省略...
    }

    // 创建扩展点实例
    private Object createExtensionInstance(Class<?> type) throws ReflectiveOperationException {
        return instantiationStrategy.instantiate(type);
    }

    // 初始化前处理
    private T postProcessBeforeInitialization(T instance, String name) throws Exception {
        // 实现代码省略...
    }

    // 初始化后处理
    private T postProcessAfterInitialization(T instance, String name) throws Exception {
        // 实现代码省略...
    }

    // 判断是否包含指定名称的扩展点
    private boolean containsExtension(String name) {
        return getExtensionClasses().containsKey(name);
    }

    // 注入扩展点依赖
    private T injectExtension(T instance) {
        // 实现代码省略...
    }

    // 初始化扩展点
    private void initExtension(T instance) {
        if (instance instanceof Lifecycle) {
            ((Lifecycle) instance).initialize();
        }
    }

    // 获取setter属性名
    private String getSetterProperty(Method method) {
        return method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
    }

    // 判断是否是setter方法
    private boolean isSetter(Method method) {
        return method.getName().startsWith("set")
            && method.getParameterTypes().length == 1
            && Modifier.isPublic(method.getModifiers());
    }

    // 获取扩展点类
    private Class<?> getExtensionClass(String name) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (name == null) {
            throw new IllegalArgumentException("Extension name == null");
        }
        return getExtensionClasses().get(name);
    }

    // 获取扩展点类集合
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    // 加载扩展点类
    private Map<String, Class<?>> loadExtensionClasses() {
        cacheDefaultExtensionName();
        Map<String, Class<?>> extensionClasses = new HashMap<>();
        for (LoadingStrategy strategy : strategies) {
            loadDirectory(extensionClasses, strategy, type.getName());
            if (this.type == ExtensionInjector.class) {
                loadDirectory(extensionClasses, strategy, ExtensionFactory.class.getName());
            }
        }
        return extensionClasses;
    }

    // 加载目录下的扩展点
    private void loadDirectory(Map<String, Class<?>> extensionClasses, LoadingStrategy strategy, String type) {
        // 实现代码省略...
    }

    // 缓存默认扩展点名称
    private void cacheDefaultExtensionName() {
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        if (defaultAnnotation == null) {
            return;
        }
        String value = defaultAnnotation.value();
        if ((value = value.trim()).length() > 0) {
            String[] names = NAME_SEPARATOR.split(value);
            if (names.length > 1) {
                throw new IllegalStateException("More than 1 default extension name on extension " + type.getName()
                    + ": " + Arrays.toString(names));
            }
            if (names.length == 1) {
                cachedDefaultName = names[0];
            }
        }
    }

    // 加载目录下的资源
    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type) {
        loadDirectory(extensionClasses, dir, type, false, false, new String[]{}, new String[]{});
    }

    // 加载目录下的资源
    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type,
                               boolean extensionLoaderClassLoaderFirst, boolean overridden,
                               String[] excludedPackages, String[] onlyExtensionClassLoaderPackages) {
        // 实现代码省略...
    }

    // 从类加载资源
    private void loadFromClass(Map<String, Class<?>> extensionClasses, boolean overridden, Set<java.net.URL> urls, ClassLoader classLoader,
                               String[] excludedPackages, String[] onlyExtensionClassLoaderPackages) {
        // 实现代码省略...
    }

    // 加载资源
    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader,
                              java.net.URL resourceURL, boolean overridden, String[] excludedPackages, String[] onlyExtensionClassLoaderPackages) {
        // 实现代码省略...
    }

    // 判断是否在排除包中
    private boolean isExcluded(String className, String... excludedPackages) {
        // 实现代码省略...
    }

    // 判断是否在类加载器排除列表中
    private boolean isExcludedByClassLoader(String className, ClassLoader classLoader, String... onlyExtensionClassLoaderPackages) {
        // 实现代码省略...
    }

    // 加载类
    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name,
                           boolean overridden) throws NoSuchMethodException {
        // 实现代码省略...
    }

    // 缓存名称
    private void cacheName(Class<?> clazz, String name) {
        if (!cachedNames.containsKey(clazz)) {
            cachedNames.put(clazz, name);
        }
    }

    // 保存到扩展点类集合
    private void saveInExtensionClass(Map<String, Class<?>> extensionClasses, Class<?> clazz, String name, boolean overridden) {
        // 实现代码省略...
    }

    // 缓存激活类
    private void cacheActivateClass(Class<?> clazz, String name) {
        // 实现代码省略...
    }

    // 缓存自适应类
    private void cacheAdaptiveClass(Class<?> clazz, boolean overridden) {
        // 实现代码省略...
    }

    // 缓存包装类
    private void cacheWrapperClass(Class<?> clazz) {
        if (cachedWrapperClasses == null) {
            cachedWrapperClasses = new ConcurrentHashSet<>();
        }
        cachedWrapperClasses.add(clazz);
    }

    // 判断是否是包装类
    private boolean isWrapperClass(Class<?> clazz) {
        try {
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    // 查找注解名称
    @SuppressWarnings("deprecation")
    private String findAnnotationName(Class<?> clazz) {
        // 实现代码省略...
    }

    // 创建自适应扩展点
    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        // 实现代码省略...
    }

    // 获取自适应扩展点类
    private Class<?> getAdaptiveExtensionClass() {
        getExtensionClasses();
        if (cachedAdaptiveClass != null) {
            return cachedAdaptiveClass;
        }
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }

    // 创建自适应扩展点类
    private Class<?> createAdaptiveExtensionClass() {
        // 实现代码省略...
    }

    // 获取环境变量
    private Environment getEnvironment() {
        if (environment == null) {
            environment = (Environment) extensionDirector.getExtensionLoader(ApplicationExt.class).getExtension(Environment.NAME);
        }
        return environment;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + type.getName() + "]";
    }
}
