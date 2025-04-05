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
import java.io.InputStream;

/**
 * UnsafeByteArrayInputStream.
 */
// 非线程安全的字节数组输入流实现
public class UnsafeByteArrayInputStream extends InputStream {
    // 内部字节数组
    protected byte[] mData;
    // 当前位置、限制位置和标记位置
    protected int mPosition, mLimit, mMark = 0;

    // 构造函数，使用整个字节数组
    public UnsafeByteArrayInputStream(byte[] buf) {
        this(buf, 0, buf.length);
    }

    // 构造函数，从指定偏移量开始
    public UnsafeByteArrayInputStream(byte[] buf, int offset) {
        this(buf, offset, buf.length - offset);
    }

    // 构造函数，指定字节数组、偏移量和长度
    public UnsafeByteArrayInputStream(byte[] buf, int offset, int length) {
        mData = buf;
        mPosition = mMark = offset;
        mLimit = Math.min(offset + length, buf.length);
    }

    // 读取单个字节
    @Override
    public int read() {
        return (mPosition < mLimit) ? (mData[mPosition++] & 0xff) : -1;
    }

    // 读取字节到指定数组
    @Override
    public int read(byte[] b, int off, int len) {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (mPosition >= mLimit) {
            return -1;
        }
        if (mPosition + len > mLimit) {
            len = mLimit - mPosition;
        }
        if (len <= 0) {
            return 0;
        }
        System.arraycopy(mData, mPosition, b, off, len);
        mPosition += len;
        return len;
    }

    // 跳过指定字节数
    @Override
    public long skip(long len) {
        if (mPosition + len > mLimit) {
            len = mLimit - mPosition;
        }
        if (len <= 0) {
            return 0;
        }
        mPosition += len;
        return len;
    }

    // 获取剩余可读字节数
    @Override
    public int available() {
        return mLimit - mPosition;
    }

    // 是否支持标记功能
    @Override
    public boolean markSupported() {
        return true;
    }

    // 设置标记位置
    @Override
    public void mark(int readAheadLimit) {
        mMark = mPosition;
    }

    // 重置到标记位置
    @Override
    public void reset() {
        mPosition = mMark;
    }

    // 关闭流(空实现)
    @Override
    public void close() throws IOException {
    }

    // 获取当前位置
    public int position() {
        return mPosition;
    }

    // 设置新位置
    public void position(int newPosition) {
        mPosition = newPosition;
    }

    // 获取字节数组总长度
    public int size() {
        return mData == null ? 0 : mData.length;
    }
}
