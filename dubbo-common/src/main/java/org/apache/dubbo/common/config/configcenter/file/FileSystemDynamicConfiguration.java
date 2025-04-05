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
package org.apache.dubbo.common.config.configcenter.file;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigChangeType;
import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.config.configcenter.TreePathDynamicConfiguration;
import org.apache.dubbo.common.function.ThrowableConsumer;
import org.apache.dubbo.common.function.ThrowableFunction;
import org.apache.dubbo.common.lang.ShutdownHookCallbacks;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.model.ScopeModel;
import org.apache.dubbo.rpc.model.ScopeModelUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableMap;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.io.FileUtils.readFileToString;

/**
 * File-System based {@link DynamicConfiguration} implementation
 *
 * @since 2.7.5
 */
public class FileSystemDynamicConfiguration extends TreePathDynamicConfiguration {

    // 配置文件目录参数名
    public static final String CONFIG_CENTER_DIR_PARAM_NAME = PARAM_NAME_PREFIX + "dir";

    // 文件编码参数名
    public static final String CONFIG_CENTER_ENCODING_PARAM_NAME = PARAM_NAME_PREFIX + "encoding";

    // 默认配置文件目录路径
    public static final String DEFAULT_CONFIG_CENTER_DIR_PATH = System.getProperty("user.home") + File.separator
            + ".dubbo" + File.separator + "config-center";

    // 默认线程池大小
    public static final int DEFAULT_THREAD_POOL_SIZE = 1;

    // 默认文件编码
    public static final String DEFAULT_CONFIG_CENTER_ENCODING = "UTF-8";

    // 关注的路径事件类型
    private static final WatchEvent.Kind[] INTEREST_PATH_KINDS = of(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

    // PollingWatchService类名
    private static final String POLLING_WATCH_SERVICE_CLASS_NAME = "sun.nio.fs.PollingWatchService";

    // 线程池大小
    private static final int THREAD_POOL_SIZE = 1;

    // 日志记录器
    private static final Log logger = LogFactory.getLog(FileSystemDynamicConfiguration.class);

    // 配置变更类型映射表
    private static final Map<String, ConfigChangeType> CONFIG_CHANGE_TYPES_MAP =
            unmodifiableMap(new HashMap<String, ConfigChangeType>() {
                {
                    put(ENTRY_CREATE.name(), ConfigChangeType.ADDED);
                    put(ENTRY_DELETE.name(), ConfigChangeType.DELETED);
                    put(ENTRY_MODIFY.name(), ConfigChangeType.MODIFIED);
                }
            });

    // WatchService实例
    private static final Optional<WatchService> watchService;

    // 是否基于轮询的WatchService
    private static final boolean BASED_POOLING_WATCH_SERVICE;

    // WatchEvent修饰符
    private static final WatchEvent.Modifier[] MODIFIERS;

    // 延迟执行时间(秒)
    private static final Integer DELAY;

    // Watch事件循环线程池
    private static final ThreadPoolExecutor WATCH_EVENTS_LOOP_THREAD_POOL;

    // 静态初始化块
    static {
        watchService = newWatchService();
        BASED_POOLING_WATCH_SERVICE = detectPoolingBasedWatchService(watchService);
        MODIFIERS = initWatchEventModifiers();
        DELAY = initDelay(MODIFIERS);
        WATCH_EVENTS_LOOP_THREAD_POOL = newWatchEventsLoopThreadPool();
    }

    // 配置中心根目录
    private final File rootDirectory;

    // 文件编码
    private final String encoding;

    // 正在处理的目录集合
    private final Set<File> processingDirectories;

    // 监听器仓库
    private final Map<File, List<ConfigurationListener>> listenersRepository;

    // 作用域模型
    private ScopeModel scopeModel;

    // 是否已注册关闭钩子
    private AtomicBoolean hasRegisteredShutdownHook = new AtomicBoolean();

    // 默认构造函数
    public FileSystemDynamicConfiguration() {
        this(new File(DEFAULT_CONFIG_CENTER_DIR_PATH));
    }

    // 带根目录参数的构造函数
    public FileSystemDynamicConfiguration(File rootDirectory) {
        this(rootDirectory, DEFAULT_CONFIG_CENTER_ENCODING);
    }

    // 带根目录和编码参数的构造函数
    public FileSystemDynamicConfiguration(File rootDirectory, String encoding) {
        this(rootDirectory, encoding, DEFAULT_THREAD_POOL_PREFIX);
    }

    // 带根目录、编码和线程池前缀参数的构造函数
    public FileSystemDynamicConfiguration(File rootDirectory, String encoding, String threadPoolPrefixName) {
        this(rootDirectory, encoding, threadPoolPrefixName, DEFAULT_THREAD_POOL_SIZE);
    }

    // 带根目录、编码、线程池前缀和大小参数的构造函数
    public FileSystemDynamicConfiguration(File rootDirectory, String encoding, String threadPoolPrefixName,
                                          int threadPoolSize) {
        this(rootDirectory, encoding, threadPoolPrefixName, threadPoolSize, DEFAULT_THREAD_POOL_KEEP_ALIVE_TIME);
    }

    // 带根目录、编码、线程池前缀、大小和存活时间参数的构造函数
    public FileSystemDynamicConfiguration(File rootDirectory, String encoding,
                                          String threadPoolPrefixName,
                                          int threadPoolSize,
                                          long keepAliveTime) {
        super(rootDirectory.getAbsolutePath(), threadPoolPrefixName, threadPoolSize, keepAliveTime, DEFAULT_GROUP, -1L);
        this.rootDirectory = rootDirectory;
        this.encoding = encoding;
        this.processingDirectories = initProcessingDirectories();
        this.listenersRepository = new HashMap<>();
        registerDubboShutdownHook();
    }

    // 带作用域模型的构造函数
    public FileSystemDynamicConfiguration(File rootDirectory, String encoding,
                                          String threadPoolPrefixName,
                                          int threadPoolSize,
                                          long keepAliveTime,
                                          ScopeModel scopeModel) {
        super(rootDirectory.getAbsolutePath(), threadPoolPrefixName, threadPoolSize, keepAliveTime, DEFAULT_GROUP, -1L);
        this.rootDirectory = rootDirectory;
        this.encoding = encoding;
        this.processingDirectories = initProcessingDirectories();
        this.listenersRepository = new HashMap<>();
        this.scopeModel = scopeModel;
        registerDubboShutdownHook();
    }

    // 带URL参数的构造函数
    public FileSystemDynamicConfiguration(URL url) {
        this(initDirectory(url), getEncoding(url), getThreadPoolPrefixName(url), getThreadPoolSize(url),
                getThreadPoolKeepAliveTime(url), url.getScopeModel());
    }

    // 初始化处理目录集合
    private Set<File> initProcessingDirectories() {
        return isBasedPoolingWatchService() ? new LinkedHashSet<>() : emptySet();
    }

    // 获取配置文件对象
    public File configFile(String key, String group) {
        return new File(buildPathKey(group, key));
    }

    // 在监听器中执行操作
    private void doInListener(String configFilePath, BiConsumer<File, List<ConfigurationListener>> consumer) {
        watchService.ifPresent(watchService -> {
            File configFile = new File(configFilePath);
            executeMutually(configFile.getParentFile(), () -> {
                if (!isProcessingWatchEvents()) {
                    processWatchEvents(watchService);
                }

                List<ConfigurationListener> listeners = getListeners(configFile);
                consumer.accept(configFile, listeners);

                return null;
            });
        });
    }

    // 注册Dubbo关闭钩子
    private void registerDubboShutdownHook() {
        if (!hasRegisteredShutdownHook.compareAndSet(false, true)) {
            return;
        }
        ShutdownHookCallbacks shutdownHookCallbacks = ScopeModelUtil.getApplicationModel(scopeModel).getBeanFactory().getBean(ShutdownHookCallbacks.class);
        shutdownHookCallbacks.addCallback(() -> {
            watchService.ifPresent(w -> {
                try {
                    w.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            getWatchEventsLoopThreadPool().shutdown();
        });
    }

    // 判断是否正在处理Watch事件
    private static boolean isProcessingWatchEvents() {
        return getWatchEventsLoopThreadPool().getActiveCount() > 0;
    }

    // 处理Watch事件循环
    private void processWatchEvents(WatchService watchService) {
        getWatchEventsLoopThreadPool().execute(() -> {
            while (true) {
                WatchKey watchKey = null;
                try {
                    watchKey = watchService.take();
                    if (watchKey.isValid()) {
                        for (WatchEvent event : watchKey.pollEvents()) {
                            WatchEvent.Kind kind = event.kind();
                            ConfigChangeType configChangeType = CONFIG_CHANGE_TYPES_MAP.get(kind.name());
                            if (configChangeType != null) {
                                Path configDirectoryPath = (Path) watchKey.watchable();
                                Path currentPath = (Path) event.context();
                                Path configFilePath = configDirectoryPath.resolve(currentPath);
                                File configDirectory = configDirectoryPath.toFile();
                                executeMutually(configDirectory, () -> {
                                    fireConfigChangeEvent(configDirectory, configFilePath.toFile(), configChangeType);
                                    signalConfigDirectory(configDirectory);
                                    return null;
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    return;
                } finally {
                    if (watchKey != null) {
                        watchKey.reset();
                    }
                }
            }
        });
    }

    // 通知配置目录
    private void signalConfigDirectory(File configDirectory) {
        if (isBasedPoolingWatchService()) {
            removeProcessingDirectory(configDirectory);
            notifyProcessingDirectory(configDirectory);
            if (logger.isDebugEnabled()) {
                logger.debug(format("The config rootDirectory[%s] is signalled...", configDirectory.getName()));
            }
        }
    }

    // 从处理目录集合中移除目录
    private void removeProcessingDirectory(File configDirectory) {
        processingDirectories.remove(configDirectory);
    }

    // 通知处理目录
    private void notifyProcessingDirectory(File configDirectory) {
        configDirectory.notifyAll();
    }

    // 获取监听器列表
    private List<ConfigurationListener> getListeners(File configFile) {
        return listenersRepository.computeIfAbsent(configFile, p -> new LinkedList<>());
    }

    // 触发配置变更事件
    private void fireConfigChangeEvent(File configDirectory, File configFile, ConfigChangeType configChangeType) {
        String key = configFile.getName();
        String value = getConfig(configFile);
        getListeners(configFile).forEach(listener -> {
            try {
                listener.process(new ConfigChangedEvent(key, configDirectory.getName(), value, configChangeType));
            } catch (Throwable e) {
                if (logger.isErrorEnabled()) {
                    logger.error(e.getMessage(), e);
                }
            }
        });
    }

    // 判断文件是否可读
    private boolean canRead(File file) {
        return file.exists() && file.canRead();
    }

    @Override
    public Object getInternalProperty(String key) {
        return null;
    }

    @Override
    protected boolean doPublishConfig(String pathKey, String content) throws Exception {
        return delay(pathKey, configFile -> {
            FileUtils.write(configFile, content, getEncoding());
            return true;
        });
    }

    @Override
    protected String doGetConfig(String pathKey) throws Exception {
        File configFile = new File(pathKey);
        return getConfig(configFile);
    }

    @Override
    protected boolean doRemoveConfig(String pathKey) throws Exception {
        delay(pathKey, configFile -> {
            String content = getConfig(configFile);
            FileUtils.deleteQuietly(configFile);
            return content;
        });
        return true;
    }

    @Override
    protected Collection<String> doGetConfigKeys(String groupPath) {
        File[] files = new File(groupPath).listFiles(File::isFile);
        if (files == null) {
            return new TreeSet<>();
        } else {
            return Stream.of(files)
                    .map(File::getName)
                    .collect(Collectors.toList());
        }
    }

    @Override
    protected void doAddListener(String pathKey, ConfigurationListener listener) {
        doInListener(pathKey, (configFilePath, listeners) -> {
            if (listeners.isEmpty()) {
                ThrowableConsumer.execute(configFilePath, configFile -> {
                    FileUtils.forceMkdirParent(configFile);
                    File configDirectory = configFile.getParentFile();
                    if (configDirectory != null) {
                        configDirectory.toPath().register(watchService.get(), INTEREST_PATH_KINDS, MODIFIERS);
                    }
                });
            }
            listeners.add(listener);
        });
    }

    @Override
    protected void doRemoveListener(String pathKey, ConfigurationListener listener) {
        doInListener(pathKey, (file, listeners) -> {
            listeners.remove(listener);
        });
    }

    // 延迟执行操作
    protected <V> V delay(String configFilePath, ThrowableFunction<File, V> function) {
        File configFile = new File(configFilePath);
        if (isBasedPoolingWatchService()) {
            File configDirectory = configFile.getParentFile();
            executeMutually(configDirectory, () -> {
                if (hasListeners(configFile) && isProcessing(configDirectory)) {
                    Integer delay = getDelay();
                    if (delay != null) {
                        long timeout = SECONDS.toMillis(delay);
                        if (logger.isDebugEnabled()) {
                            logger.debug(format("The config[path : %s] is about to delay in %d ms.",
                                    configFilePath, timeout));
                        }
                        configDirectory.wait(timeout);
                    }
                }
                addProcessing(configDirectory);
                return null;
            });
        }

        V value = null;
        try {
            value = function.apply(configFile);
        } catch (Throwable e) {
            if (logger.isErrorEnabled()) {
                logger.error(e.getMessage(), e);
            }
        }
        return value;
    }

    // 判断是否有监听器
    private boolean hasListeners(File configFile) {
        return getListeners(configFile).size() > 0;
    }

    // 判断目录是否正在处理
    private boolean isProcessing(File configDirectory) {
        return processingDirectories.contains(configDirectory);
    }

    // 添加处理目录
    private void addProcessing(File configDirectory) {
        processingDirectories.add(configDirectory);
    }

    // 获取配置组集合
    public Set<String> getConfigGroups() {
        return Stream.of(getRootDirectory().listFiles())
                .filter(File::isDirectory)
                .map(File::getName)
                .collect(Collectors.toSet());
    }

    // 获取配置文件内容
    protected String getConfig(File configFile) {
        return ThrowableFunction.execute(configFile,
                file -> canRead(configFile) ? readFileToString(configFile, getEncoding()) : null);
    }

    @Override
    protected void doClose() throws Exception {
    }

    // 获取根目录
    public File getRootDirectory() {
        return rootDirectory;
    }

    // 获取文件编码
    public String getEncoding() {
        return encoding;
    }

    // 获取延迟时间
    protected Integer getDelay() {
        return DELAY;
    }

    // 判断是否基于轮询的WatchService
    protected static boolean isBasedPoolingWatchService() {
        return BASED_POOLING_WATCH_SERVICE;
    }

    // 获取Watch事件循环线程池
    protected static ThreadPoolExecutor getWatchEventsLoopThreadPool() {
        return WATCH_EVENTS_LOOP_THREAD_POOL;
    }

    // 获取工作线程池
    protected ThreadPoolExecutor getWorkersThreadPool() {
        return super.getWorkersThreadPool();
    }

    // 互斥执行操作
    private <V> V executeMutually(final Object mutex, Callable<V> callable) {
        V value = null;
        synchronized (mutex) {
            try {
                value = callable.call();
            } catch (Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
        return value;
    }

    // 可变参数辅助方法
    private static <T> T[] of(T... values) {
        return values;
    }

    // 初始化延迟时间
    private static Integer initDelay(WatchEvent.Modifier[] modifiers) {
        if (isBasedPoolingWatchService()) {
            return 2;
        } else {
            return null;
        }
    }

    // 初始化WatchEvent修饰符
    private static WatchEvent.Modifier[] initWatchEventModifiers() {
        return of();
    }

    // 检测是否基于轮询的WatchService
    private static boolean detectPoolingBasedWatchService(Optional<WatchService> watchService) {
        String className = watchService.map(Object::getClass).map(Class::getName).orElse(null);
        return POLLING_WATCH_SERVICE_CLASS_NAME.equals(className);
    }

    // 创建新的WatchService
    private static Optional<WatchService> newWatchService() {
        Optional<WatchService> watchService = null;
        FileSystem fileSystem = FileSystems.getDefault();
        try {
            watchService = Optional.of(fileSystem.newWatchService());
        } catch (IOException e) {
            if (logger.isErrorEnabled()) {
                logger.error(e.getMessage(), e);
            }
            watchService = Optional.empty();
        }
        return watchService;
    }

    // 初始化配置目录
    protected static File initDirectory(URL url) {
        String directoryPath = getParameter(url, CONFIG_CENTER_DIR_PARAM_NAME, url == null ? null : url.getPath());
        File rootDirectory = null;
        if (!StringUtils.isBlank(directoryPath)) {
            rootDirectory = new File("/" + directoryPath);
        }

        if (directoryPath == null || !rootDirectory.exists()) {
            rootDirectory = new File(DEFAULT_CONFIG_CENTER_DIR_PATH);
        }

        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
            throw new IllegalStateException(format("Dubbo config center rootDirectory[%s] can't be created!",
                    rootDirectory.getAbsolutePath()));
        }
        return rootDirectory;
    }

    // 获取文件编码
    protected static String getEncoding(URL url) {
        return getParameter(url, CONFIG_CENTER_ENCODING_PARAM_NAME, DEFAULT_CONFIG_CENTER_ENCODING);
    }

    // 创建Watch事件循环线程池
    private static ThreadPoolExecutor newWatchEventsLoopThreadPool() {
        return new ThreadPoolExecutor(THREAD_POOL_SIZE, THREAD_POOL_SIZE,
                0L, MILLISECONDS,
                new SynchronousQueue(),
                new NamedThreadFactory("dubbo-config-center-watch-events-loop", true));
    }
}
