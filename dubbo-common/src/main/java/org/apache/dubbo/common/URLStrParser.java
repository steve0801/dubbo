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
package org.apache.dubbo.common;

import org.apache.dubbo.common.url.component.ServiceConfigURL;
import org.apache.dubbo.common.url.component.URLItemCache;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY_PREFIX;
import static org.apache.dubbo.common.utils.StringUtils.EMPTY_STRING;
import static org.apache.dubbo.common.utils.StringUtils.decodeHexByte;
import static org.apache.dubbo.common.utils.Utf8Utils.decodeUtf8;

package org.apache.dubbo.common;

import org.apache.dubbo.common.url.component.ServiceConfigURL;
import org.apache.dubbo.common.url.component.URLItemCache;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY_PREFIX;
import static org.apache.dubbo.common.utils.StringUtils.EMPTY_STRING;
import static org.apache.dubbo.common.utils.StringUtils.decodeHexByte;
import static org.apache.dubbo.common.utils.Utf8Utils.decodeUtf8;

// 定义URL字符串解析器类，提供静态方法用于解析编码和解码后的URL字符串
public final class URLStrParser {
    // 定义已编码的问号常量
    public static final String ENCODED_QUESTION_MARK = "%3F";
    // 定义已编码的时间戳键常量
    public static final String ENCODED_TIMESTAMP_KEY = "timestamp%3D";
    // 定义已编码的PID键常量
    public static final String ENCODED_PID_KEY = "pid%3D";
    // 定义已编码的与符号常量
    public static final String ENCODED_AND_MARK = "%26";
    // 定义空格字符
    private static final char SPACE = 0x20;

    // 创建线程局部变量，存储临时缓冲区对象
    private static final ThreadLocal<TempBuf> DECODE_TEMP_BUF = ThreadLocal.withInitial(() -> new TempBuf(1024));

    // 私有构造函数，防止实例化
    private URLStrParser() {
        //empty
    }

    /**
     * 解析解码后的URL字符串为URL对象
     * @param decodedURLStr 经过{@link URL#decode}处理后的字符串
     *                      格式：protocol://username:password@host:port/path?k1=v1&k2=v2
     *                      [协议://][用户名:密码@][主机:端口]/[路径][?参数1=值1&参数2=值2]
     */
    public static URL parseDecodedStr(String decodedURLStr) {
        // 初始化参数映射
        Map<String, String> parameters = null;
        // 查找路径结束索引
        int pathEndIdx = decodedURLStr.indexOf('?');
        if (pathEndIdx >= 0) {
            // 如果存在查询参数，则解析它们
            parameters = parseDecodedParams(decodedURLStr, pathEndIdx + 1);
        } else {
            // 否则，设置路径结束索引为字符串长度
            pathEndIdx = decodedURLStr.length();
        }

        // 提取解码主体部分
        String decodedBody = decodedURLStr.substring(0, pathEndIdx);
        // 调用辅助方法解析URL主体
        return parseURLBody(decodedURLStr, decodedBody, parameters);
    }

    // 解析解码后的参数字符串
    private static Map<String, String> parseDecodedParams(String str, int from) {
        // 获取字符串长度
        int len = str.length();
        // 如果起始位置超出字符串长度，返回空映射
        if (from >= len) {
            return Collections.emptyMap();
        }

        // 从线程局部变量获取临时缓冲区
        TempBuf tempBuf = DECODE_TEMP_BUF.get();
        // 创建参数映射
        Map<String, String> params = new HashMap<>();
        // 初始化名称开始索引
        int nameStart = from;
        // 初始化值开始索引为-1
        int valueStart = -1;
        // 遍历字符串
        int i;
        for (i = from; i < len; i++) {
            // 获取当前字符
            char ch = str.charAt(i);
            switch (ch) {
                case '=':
                    // 如果名称开始索引等于当前位置，跳过等号
                    if (nameStart == i) {
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) {
                        // 设置值开始索引
                        valueStart = i + 1;
                    }
                    break;
                case ';':
                case '&':
                    // 添加参数到映射中
                    addParam(str, false, nameStart, valueStart, i, params, tempBuf);
                    // 更新名称开始索引
                    nameStart = i + 1;
                    break;
                default:
                    // continue
            }
        }
        // 添加最后一个参数
        addParam(str, false, nameStart, valueStart, i, params, tempBuf);
        // 返回参数映射
        return params;
    }

    /**
     * 解析URL主体部分并创建URL对象
     * @param fullURLStr  完整的URL字符串
     * @param decodedBody 解码后的主体格式：[协议://][用户名:密码@][主机:端口]/[路径]
     * @param parameters  参数映射
     * @return URL 对象
     */
    private static URL parseURLBody(String fullURLStr, String decodedBody, Map<String, String> parameters) {
        // 初始化起始索引
        int starIdx = 0, endIdx = decodedBody.length();
        // 忽略'#'之后的内容
        int poundIndex = decodedBody.indexOf('#');
        if (poundIndex != -1) {
            endIdx = poundIndex;
        }

        // 初始化协议
        String protocol = null;
        // 查找协议结束索引
        int protoEndIdx = decodedBody.indexOf("://");
        if (protoEndIdx >= 0) {
            // 检查是否缺少协议
            if (protoEndIdx == 0) {
                throw new IllegalStateException("url missing protocol: \"" + fullURLStr + "\"");
            }
            // 提取协议
            protocol = decodedBody.substring(0, protoEndIdx);
            starIdx = protoEndIdx + 3;
        } else {
            // 特殊情况处理：file:/path/to/file.txt
            protoEndIdx = decodedBody.indexOf(":/");
            if (protoEndIdx >= 0) {
                if (protoEndIdx == 0) {
                    throw new IllegalStateException("url missing protocol: \"" + fullURLStr + "\"");
                }
                protocol = decodedBody.substring(0, protoEndIdx);
                starIdx = protoEndIdx + 1;
            }
        }

        // 初始化路径
        String path = null;
        // 查找路径开始索引
        int pathStartIdx = indexOf(decodedBody, '/', starIdx, endIdx);
        if (pathStartIdx >= 0) {
            // 提取路径
            path = decodedBody.substring(pathStartIdx + 1, endIdx);
            endIdx = pathStartIdx;
        }

        // 初始化用户名
        String username = null;
        // 初始化密码
        String password = null;
        // 查找密码结束索引
        int pwdEndIdx = lastIndexOf(decodedBody, '@', starIdx, endIdx);
        if (pwdEndIdx > 0) {
            // 查找密码开始索引
            int passwordStartIdx = indexOf(decodedBody, ':', starIdx, pwdEndIdx);
            if (passwordStartIdx != -1) {
                // 提取用户名
                username = decodedBody.substring(starIdx, passwordStartIdx);
                // 提取密码
                password = decodedBody.substring(passwordStartIdx + 1, pwdEndIdx);
            } else {
                // 提取用户名
                username = decodedBody.substring(starIdx, pwdEndIdx);
            }
            // 更新起始索引
            starIdx = pwdEndIdx + 1;
        }

        // 初始化主机
        String host = null;
        // 初始化端口
        int port = 0;
        // 查找主机结束索引
        int hostEndIdx = lastIndexOf(decodedBody, ':', starIdx, endIdx);
        if (hostEndIdx > 0 && hostEndIdx < decodedBody.length() - 1) {
            // 处理IPv6地址带作用域ID的情况
            if (lastIndexOf(decodedBody, '%', starIdx, endIdx) > hostEndIdx) {
                // ignore
            } else {
                // 提取端口号
                port = Integer.parseInt(decodedBody.substring(hostEndIdx + 1, endIdx));
                endIdx = hostEndIdx;
            }
        }

        // 提取主机名
        if (endIdx > starIdx) {
            host = decodedBody.substring(starIdx, endIdx);
        }

        // 使用缓存检查协议
        protocol = URLItemCache.intern(protocol);
        // 使用缓存检查路径
        path = URLItemCache.checkPath(path);

        // 创建并返回ServiceConfigURL对象
        return new ServiceConfigURL(protocol, username, password, host, port, path, parameters);
    }

    // 将原始URL字符串解析为数组
    public static String[] parseRawURLToArrays(String rawURLStr, int pathEndIdx) {
        // 初始化结果数组
        String[] parts = new String[2];
        // 计算参数开始索引
        int paramStartIdx = pathEndIdx + 3;//skip ENCODED_QUESTION_MARK
        if (pathEndIdx == -1) {
            // 如果路径结束索引不存在，查找'?'
            pathEndIdx = rawURLStr.indexOf('?');
            if (pathEndIdx == -1) {
                // 如果没有参数，解码整个字符串
                rawURLStr = URL.decode(rawURLStr);
            } else {
                // 更新参数开始索引
                paramStartIdx = pathEndIdx + 1;
            }
        }
        // 分割URL字符串
        if (pathEndIdx >= 0) {
            parts[0] = rawURLStr.substring(0, pathEndIdx);
            parts[1] = rawURLStr.substring(paramStartIdx);
        } else {
            // 如果没有参数，只包含一个元素
            parts = new String[]{rawURLStr};
        }
        // 返回结果数组
        return parts;
    }

    // 解析参数字符串
    public static Map<String, String> parseParams(String rawParams, boolean encoded) {
        // 根据是否编码选择不同的解析方法
        if (encoded) {
            return parseEncodedParams(rawParams, 0);
        }
        return parseDecodedParams(rawParams, 0);
    }

    /**
     * 解析编码后的URL字符串为URL对象
     * @param encodedURLStr 经过{@link URL#encode(String)}处理后的字符串
     *                      编码后解码格式：protocol://username:password@host:port/path?k1=v1&k2=v2
     *                      [协议://][用户名:密码@][主机:端口]/[路径][?参数1=值1&参数2=值2]
     */
    public static URL parseEncodedStr(String encodedURLStr) {
        // 初始化参数映射
        Map<String, String> parameters = null;
        // 查找路径结束索引（转换为大写）
        int pathEndIdx = encodedURLStr.toUpperCase().indexOf("%3F");// '?'
        if (pathEndIdx >= 0) {
            // 如果存在查询参数，则解析它们
            parameters = parseEncodedParams(encodedURLStr, pathEndIdx + 3);
        } else {
            // 否则，设置路径结束索引为字符串长度
            pathEndIdx = encodedURLStr.length();
        }

        // 解码主体部分
        String decodedBody = decodeComponent(encodedURLStr, 0, pathEndIdx, false, DECODE_TEMP_BUF.get());
        // 调用辅助方法解析URL主体
        return parseURLBody(encodedURLStr, decodedBody, parameters);
    }

    // 解析编码后的参数字符串
    private static Map<String, String> parseEncodedParams(String str, int from) {
        // 获取字符串长度
        int len = str.length();
        // 如果起始位置超出字符串长度，返回空映射
        if (from >= len) {
            return Collections.emptyMap();
        }

        // 从线程局部变量获取临时缓冲区
        TempBuf tempBuf = DECODE_TEMP_BUF.get();
        // 创建参数映射
        Map<String, String> params = new HashMap<>();
        // 初始化名称开始索引
        int nameStart = from;
        // 初始化值开始索引为-1
        int valueStart = -1;
        // 遍历字符串
        int i;
        for (i = from; i < len; i++) {
            // 获取当前字符
            char ch = str.charAt(i);
            // 处理转义序列
            if (ch == '%') {
                if (i + 3 > len) {
                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + str);
                }
                ch = (char) decodeHexByte(str, i + 1);
                i += 2;
            }

            switch (ch) {
                case '=':
                    // 如果名称开始索引等于当前位置，跳过等号
                    if (nameStart == i) {
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) {
                        // 设置值开始索引
                        valueStart = i + 1;
                    }
                    break;
                case ';':
                case '&':
                    // 添加参数到映射中
                    addParam(str, true, nameStart, valueStart, i - 2, params, tempBuf);
                    // 更新名称开始索引
                    nameStart = i + 1;
                    break;
                default:
                    // continue
            }
        }
        // 添加最后一个参数
        addParam(str, true, nameStart, valueStart, i, params, tempBuf);
        // 返回参数映射
        return params;
    }

    // 添加参数到映射中
    private static boolean addParam(String str, boolean isEncoded, int nameStart, int valueStart, int valueEnd, Map<String, String> params,
                                    TempBuf tempBuf) {
        // 检查名称开始索引是否超过值结束索引
        if (nameStart >= valueEnd) {
            return false;
        }

        // 如果值开始索引小于或等于名称开始索引，更新值开始索引
        if (valueStart <= nameStart) {
            valueStart = valueEnd + 1;
        }

        // 根据是否编码选择不同的处理方式
        if (isEncoded) {
            // 解码名称
            String name = decodeComponent(str, nameStart, valueStart - 3, false, tempBuf);
            // 解码值
            String value;
            if (valueStart >= valueEnd) {
                value = name;
            } else {
                value = decodeComponent(str, valueStart, valueEnd, false, tempBuf);
            }
            // 将参数放入映射中
            URLItemCache.putParams(params, name, value);
            // 兼容低版本注册"default."前缀的键
            if (name.startsWith(DEFAULT_KEY_PREFIX)) {
                params.putIfAbsent(name.substring(DEFAULT_KEY_PREFIX.length()), value);
            }
        } else {
            // 直接提取名称
            String name = str.substring(nameStart, valueStart - 1);
            // 提取值
            String value;
            if (valueStart >= valueEnd) {
                value = name;
            } else {
                value = str.substring(valueStart, valueEnd);
            }
            // 将参数放入映射中
            URLItemCache.putParams(params, name, value);
            // 兼容低版本注册"default."前缀的键
            if (name.startsWith(DEFAULT_KEY_PREFIX)) {
                params.putIfAbsent(name.substring(DEFAULT_KEY_PREFIX.length()), value);
            }
        }
        // 返回true表示成功添加
        return true;
    }

    // 解码组件
    private static String decodeComponent(String s, int from, int toExcluded, boolean isPath, TempBuf tempBuf) {
        // 计算长度
        int len = toExcluded - from;
        // 如果长度小于等于0，返回空字符串
        if (len <= 0) {
            return EMPTY_STRING;
        }

        // 查找第一个转义字符
        int firstEscaped = -1;
        for (int i = from
