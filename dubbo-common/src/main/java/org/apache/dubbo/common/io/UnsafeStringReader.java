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
import java.io.Reader;

/**
 * Thread-unsafe StringReader.
 */
// 非线程安全的字符串读取器实现
public class UnsafeStringReader extends Reader {
    // 存储要读取的字符串
    private String mString;
    // 当前位置、限制位置和标记位置
    private int mPosition, mLimit, mMark;

    // 构造函数，初始化字符串读取器
    public UnsafeStringReader(String str) {
        mString = str;
        mLimit = str.length();
        mPosition = mMark = 0;
    }

    // 读取单个字符
    @Override
    public int read() throws IOException {
        ensureOpen();
        if (mPosition >= mLimit) {
            return -1;
        }

        return mString.charAt(mPosition++);
    }

    // 读取字符到数组
    @Override
    public int read(char[] cs, int off, int len) throws IOException {
        ensureOpen();
        // 参数校验
        if ((off < 0) || (off > cs.length) || (len < 0) ||
            ((off + len) > cs.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }

        if (len == 0) {
            return 0;
        }

        if (mPosition >= mLimit) {
            return -1;
        }

        // 计算实际可读取的字符数
        int n = Math.min(mLimit - mPosition, len);
        mString.getChars(mPosition, mPosition + n, cs, off);
        mPosition += n;
        return n;
    }

    // 跳过指定数量的字符
    @Override
    public long skip(long ns) throws IOException {
        ensureOpen();
        if (mPosition >= mLimit) {
            return 0;
        }

        // 计算实际可跳过的字符数
        long n = Math.min(mLimit - mPosition, ns);
        n = Math.max(-mPosition, n);
        mPosition += n;
        return n;
    }

    // 检查流是否准备好读取
    @Override
    public boolean ready() throws IOException {
        ensureOpen();
        return true;
    }

    // 是否支持标记功能
    @Override
    public boolean markSupported() {
        return true;
    }

    // 设置标记位置
    @Override
    public void mark(int readAheadLimit) throws IOException {
        if (readAheadLimit < 0) {
            throw new IllegalArgumentException("Read-ahead limit < 0");
        }

        ensureOpen();
        mMark = mPosition;
    }

    // 重置到标记位置
    @Override
    public void reset() throws IOException {
        ensureOpen();
        mPosition = mMark;
    }

    // 关闭流
    @Override
    public void close() throws IOException {
        mString = null;
    }

    // 确保流已打开
    private void ensureOpen() throws IOException {
        if (mString == null) {
            throw new IOException("Stream closed");
        }
    }
}

