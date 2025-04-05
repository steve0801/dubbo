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
package org.apache.dubbo.common.io;

import java.io.IOException;
import java.io.Writer;

/**
 * Thread-unsafe StringWriter.
 */
// 非线程安全的字符串写入器实现
public class UnsafeStringWriter extends Writer {
    // 内部字符串缓冲区
    private StringBuilder mBuffer;

    // 默认构造函数，初始化缓冲区
    public UnsafeStringWriter() {
        lock = mBuffer = new StringBuilder();
    }

    // 指定初始大小的构造函数
    public UnsafeStringWriter(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative buffer size");
        }

        lock = mBuffer = new StringBuilder();
    }

    // 写入单个字符
    @Override
    public void write(int c) {
        mBuffer.append((char) c);
    }

    // 写入字符数组
    @Override
    public void write(char[] cs) throws IOException {
        mBuffer.append(cs, 0, cs.length);
    }

    // 写入字符数组的指定部分
    @Override
    public void write(char[] cs, int off, int len) throws IOException {
        if ((off < 0) || (off > cs.length) || (len < 0) ||
                ((off + len) > cs.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }

        if (len > 0) {
            mBuffer.append(cs, off, len);
        }
    }

    // 写入字符串
    @Override
    public void write(String str) {
        mBuffer.append(str);
    }

    // 写入字符串的指定部分
    @Override
    public void write(String str, int off, int len) {
        mBuffer.append(str, off, off + len);
    }

    // 追加字符序列
    @Override
    public Writer append(CharSequence csq) {
        if (csq == null) {
            write("null");
        } else {
            write(csq.toString());
        }
        return this;
    }

    // 追加字符序列的指定部分
    @Override
    public Writer append(CharSequence csq, int start, int end) {
        CharSequence cs = (csq == null ? "null" : csq);
        write(cs.subSequence(start, end).toString());
        return this;
    }

    // 追加单个字符
    @Override
    public Writer append(char c) {
        mBuffer.append(c);
        return this;
    }

    // 关闭写入器(空实现)
    @Override
    public void close() {
    }

    // 刷新缓冲区(空实现)
    @Override
    public void flush() {
    }

    // 获取缓冲区内容字符串
    @Override
    public String toString() {
        return mBuffer.toString();
    }
}
