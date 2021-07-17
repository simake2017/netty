/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The {@link PlatformDependent} operations which requires access to {@code sun.misc.*}.
 */
/*
    访问sun包的内容
 */
final class PlatformDependent0 {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PlatformDependent0.class);
    private static final long ADDRESS_FIELD_OFFSET;
    private static final long BYTE_ARRAY_BASE_OFFSET;
    private static final Constructor<?> DIRECT_BUFFER_CONSTRUCTOR;
    private static final boolean IS_EXPLICIT_NO_UNSAFE = explicitNoUnsafe0();
    private static final Method ALLOCATE_ARRAY_METHOD;
    private static final int JAVA_VERSION = javaVersion0();
    private static final boolean IS_ANDROID = isAndroid0();

    private static final Object INTERNAL_UNSAFE;
    static final Unsafe UNSAFE;

    // constants borrowed from murmur3
    static final int HASH_CODE_ASCII_SEED = 0xc2b2ae35;
    static final int HASH_CODE_C1 = 0xcc9e2d51;
    static final int HASH_CODE_C2 = 0x1b873593;

    /**
     * Limits the number of bytes to copy per {@link Unsafe#copyMemory(long, long, long)} to allow safepoint polling
     * during a large copy.
     */
    private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    //判断系统架构是否是x86系列或者amd系列
    //这里不对，这里判断的是对齐和非对齐方式，具体作用暂未知
    private static final boolean UNALIGNED;

    static {
        final ByteBuffer direct;
        Field addressField = null;
        Method allocateArrayMethod = null;
        Unsafe unsafe;
        Object internalUnsafe = null;

        if (isExplicitNoUnsafe()) {
            direct = null;
            addressField = null;
            unsafe = null;
            internalUnsafe = null;
        } else {
            direct = ByteBuffer.allocateDirect(1);//分配1字节直接内存对象

            // attempt to access field Unsafe#theUnsafe
            final Object maybeUnsafe = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                        Throwable cause = ReflectionUtil.trySetAccessible(unsafeField);//返回Throwable
                        if (cause != null) {
                            return cause;
                        }
                        // the unsafe instance
                        return unsafeField.get(null);
                    } catch (NoSuchFieldException e) {
                        return e;
                    } catch (SecurityException e) {
                        return e;
                    } catch (IllegalAccessException e) {
                        return e;
                    }
                }
            });

            // the conditional check here can not be replaced with checking that maybeUnsafe
            // is an instanceof Unsafe and reversing the if and else blocks; this is because an
            // instanceof check against Unsafe will trigger a class load and we might not have
            // the runtime permission accessClassInPackage.sun.misc
            if (maybeUnsafe instanceof Exception) {
                unsafe = null;
                logger.debug("sun.misc.Unsafe.theUnsafe: unavailable", (Exception) maybeUnsafe);
            } else {
                unsafe = (Unsafe) maybeUnsafe;
                logger.debug("sun.misc.Unsafe.theUnsafe: available");
            }

            // ensure the unsafe supports all necessary methods to work around the mistake in the latest OpenJDK
            // https://github.com/netty/netty/issues/1061
            // http://www.mail-archive.com/jdk6-dev@openjdk.java.net/msg00698.html
            if (unsafe != null) {
                final Unsafe finalUnsafe = unsafe;
                final Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try {
                            //判断是否有这个方法 copyMemory
                            finalUnsafe.getClass().getDeclaredMethod(
                                    "copyMemory", Object.class, long.class, Object.class, long.class, long.class);
                            return null;
                        } catch (NoSuchMethodException e) {
                            return e;
                        } catch (SecurityException e) {
                            return e;
                        }
                    }
                });


                if (maybeException == null) {
                    logger.debug("sun.misc.Unsafe.copyMemory: available");
                } else {
                    // Unsafe.copyMemory(Object, long, Object, long, long) unavailable.
                    //没有这个方法 设置为 null
                    unsafe = null;
                    logger.debug("sun.misc.Unsafe.copyMemory: unavailable", (Throwable) maybeException);
                }
            }

            if (unsafe != null) {
                final Unsafe finalUnsafe = unsafe;

                /*
                    使用特权的方式访问 相应 安全类，如果使用securityManager，那么需要使用
                    下面这种方法 并且 授予安全策略 当中
                 */
                // attempt to access field Buffer#address
                final Object maybeAddressField = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try {
                            //获取Buffer类的address字段的Field
                            //这个字段表示对应内存类的内存地址，不管是直接内存类还是堆对内存类，都继承子Buffer类，会对这个字段赋值
                            final Field field = Buffer.class.getDeclaredField("address");
                            // Use Unsafe to read value of the address field. This way it will not fail on JDK9+ which
                            // will forbid changing the access level via reflection.

                            /*
                                首先用unsafe类获取这个字段的偏移量
                                然后获取 直接内存 这个偏移 位置的值
                             */
                            final long offset = finalUnsafe.objectFieldOffset(field);//获取address字段的偏移量 在Buffer类中的偏移量
                            //根据偏移量找到直接内存对应的地址
                            final long address = finalUnsafe.getLong(direct, offset);//获取这个类这个偏移位置的字段值（long类型）

                            // if direct really is a direct buffer, address will be non-zero
                            if (address == 0) {
                                return null;
                            }
                            return field;
                        } catch (NoSuchFieldException e) {
                            return e;
                        } catch (SecurityException e) {
                            return e;
                        }
                    }
                });

                if (maybeAddressField instanceof Field) {
                    addressField = (Field) maybeAddressField;
                    logger.debug("java.nio.Buffer.address: available");
                } else {
                    logger.debug("java.nio.Buffer.address: unavailable", (Throwable) maybeAddressField);

                    // If we cannot access the address of a direct buffer, there's no point of using unsafe.
                    // Let's just pretend unsafe is unavailable for overall simplicity.
                    unsafe = null;
                }
            }

            if (unsafe != null) {
                // There are assumptions made where ever BYTE_ARRAY_BASE_OFFSET is used (equals, hashCodeAscii, and
                // primitive accessors) that arrayIndexScale == 1, and results are undefined if this is not the case.
                long byteArrayIndexScale = unsafe.arrayIndexScale(byte[].class);
                if (byteArrayIndexScale != 1) {
                    logger.debug("unsafe.arrayIndexScale is {} (expected: 1). Not using unsafe.", byteArrayIndexScale);
                    unsafe = null;
                }
            }
        }
        UNSAFE = unsafe;//至此，判断unsafe类真正可用，进行赋值

        if (unsafe == null) {
            ADDRESS_FIELD_OFFSET = -1;
            BYTE_ARRAY_BASE_OFFSET = -1;
            UNALIGNED = false;
            DIRECT_BUFFER_CONSTRUCTOR = null;
            ALLOCATE_ARRAY_METHOD = null;
        } else {
            Constructor<?> directBufferConstructor;
            long address = -1;
            try {
                final Object maybeDirectBufferConstructor =
                        AccessController.doPrivileged(new PrivilegedAction<Object>() {
                            @Override
                            public Object run() {
                                try {
                                    //获取直接内存构造类的构造函数
                                    final Constructor<?> constructor =
                                            direct.getClass().getDeclaredConstructor(long.class, int.class);//获取这样的一个构造函数
                                    Throwable cause = ReflectionUtil.trySetAccessible(constructor);
                                    if (cause != null) {
                                        return cause;
                                    }
                                    return constructor;
                                } catch (NoSuchMethodException e) {
                                    return e;
                                } catch (SecurityException e) {
                                    return e;
                                }
                            }
                        });

                if (maybeDirectBufferConstructor instanceof Constructor<?>) {
                    //首先使用Unsafe类分配一个内存为1字节的内存，然后返回对应地址
                    address = UNSAFE.allocateMemory(1);//分配直接内存，返回地址
                    // try to use the constructor now
                    try {
                        //判断是否能正确构造直接内存,--对应的类，DirectByteBuffer
                        ((Constructor<?>) maybeDirectBufferConstructor).newInstance(address, 1);//然后使用直接内存类占据这块内存
                        directBufferConstructor = (Constructor<?>) maybeDirectBufferConstructor;
                        logger.debug("direct buffer constructor: available");
                    } catch (InstantiationException e) {
                        directBufferConstructor = null;
                    } catch (IllegalAccessException e) {
                        directBufferConstructor = null;
                    } catch (InvocationTargetException e) {
                        directBufferConstructor = null;
                    }
                } else {
                    logger.debug(
                            "direct buffer constructor: unavailable",
                            (Throwable) maybeDirectBufferConstructor);
                    directBufferConstructor = null;
                }
            } finally {
                if (address != -1) {
                    UNSAFE.freeMemory(address);
                }
            }
            DIRECT_BUFFER_CONSTRUCTOR = directBufferConstructor;//直接内存的构造函数 DirectByteBuffer的构造函数
            ADDRESS_FIELD_OFFSET = objectFieldOffset(addressField);//获取字段的偏移量
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);//获取字节数组的的第一个元素偏移量
            boolean unaligned;
            Object maybeUnaligned = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        Class<?> bitsClass =
                                Class.forName("java.nio.Bits", false, getSystemClassLoader());
                        Method unalignedMethod = bitsClass.getDeclaredMethod("unaligned");
                        Throwable cause = ReflectionUtil.trySetAccessible(unalignedMethod);
                        if (cause != null) {
                            return cause;
                        }
                        return unalignedMethod.invoke(null);
                    } catch (NoSuchMethodException e) {
                        return e;
                    } catch (SecurityException e) {
                        return e;
                    } catch (IllegalAccessException e) {
                        return e;
                    } catch (ClassNotFoundException e) {
                        return e;
                    } catch (InvocationTargetException e) {
                        return e;
                    }
                }
            });

            if (maybeUnaligned instanceof Boolean) {
                unaligned = (Boolean) maybeUnaligned;
                logger.debug("java.nio.Bits.unaligned: available, {}", unaligned);
            } else {
                String arch = SystemPropertyUtil.get("os.arch", "");
                //noinspection DynamicRegexReplaceableByCompiledPattern
                unaligned = arch.matches("^(i[3-6]86|x86(_64)?|x64|amd64)$");
                Throwable t = (Throwable) maybeUnaligned;
                logger.debug("java.nio.Bits.unaligned: unavailable {}", unaligned, t);
            }

            UNALIGNED = unaligned;

            if (javaVersion() >= 9) {
                Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try {
                            // Java9 has jdk.internal.misc.Unsafe and not all methods are propagated to
                            // sun.misc.Unsafe
                            Class<?> internalUnsafeClass = getClassLoader(PlatformDependent0.class)
                                    .loadClass("jdk.internal.misc.Unsafe");//jdk1.9 可以加载jdk.internal.misc.Unsafe类，有自己的Unsafe类，
                            Method method = internalUnsafeClass.getDeclaredMethod("getUnsafe");//获取getUnsafe方法
                            //当调用的方法是静态方法时，不需要传递具体参数
                            return method.invoke(null);//
                        } catch (Throwable e) {
                            return e;
                        }
                    }
                });
                if (!(maybeException instanceof Throwable)) { //判断方法调用未出现异常
                    internalUnsafe = maybeException;
                    final Object finalInternalUnsafe = internalUnsafe;
                    maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        @Override
                        public Object run() {
                            try {
                                return finalInternalUnsafe.getClass().getDeclaredMethod(
                                        "allocateUninitializedArray", Class.class, int.class);
                            } catch (NoSuchMethodException e) {
                                return e;
                            } catch (SecurityException e) {
                                return e;
                            }
                        }
                    });

                    if (maybeException instanceof Method) {
                        try {
                            Method m = (Method) maybeException;
                            byte[] bytes = (byte[]) m.invoke(finalInternalUnsafe, byte.class, 8);
                            assert bytes.length == 8;
                            allocateArrayMethod = m;
                        } catch (IllegalAccessException e) {
                            maybeException = e;
                        } catch (InvocationTargetException e) {
                            maybeException = e;
                        }
                    }
                }

                if (maybeException instanceof Throwable) {
                    logger.debug("jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable",
                            (Throwable) maybeException);
                } else {
                    //判断java9的分配数组方法可用
                    logger.debug("jdk.internal.misc.Unsafe.allocateUninitializedArray(int): available");
                }
            } else {
                logger.debug("jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable prior to Java9");
            }
            ALLOCATE_ARRAY_METHOD = allocateArrayMethod;
        }

        INTERNAL_UNSAFE = internalUnsafe;

        logger.debug("java.nio.DirectByteBuffer.<init>(long, int): {}",
                DIRECT_BUFFER_CONSTRUCTOR != null ? "available" : "unavailable");
    }

    /**
     * 判断是否用参数设置了不用unsafe类
     * @return
     */
    static boolean isExplicitNoUnsafe() {
        return IS_EXPLICIT_NO_UNSAFE;
    }

    /**
     * 判断是否使用unsafe类（用参数设置是否使用unsafe类）
     * @return
     */
    private static boolean explicitNoUnsafe0() {
        //是否使用onUnsafe类
        final boolean noUnsafe = SystemPropertyUtil.getBoolean("io.netty.noUnsafe", false);
        logger.debug("-Dio.netty.noUnsafe: {}", noUnsafe);

        if (noUnsafe) {
            logger.debug("sun.misc.Unsafe: unavailable (io.netty.noUnsafe)");
            return true;
        }

        // Legacy properties
        boolean tryUnsafe;
        if (SystemPropertyUtil.contains("io.netty.tryUnsafe")) {
            tryUnsafe = SystemPropertyUtil.getBoolean("io.netty.tryUnsafe", true);
        } else {
            tryUnsafe = SystemPropertyUtil.getBoolean("org.jboss.netty.tryUnsafe", true);
        }

        /*
            这里上面默认返回true
         */
        if (!tryUnsafe) {
            logger.debug("sun.misc.Unsafe: unavailable (io.netty.tryUnsafe/org.jboss.netty.tryUnsafe)");
            return true;
        }

        return false;//默认返回false
    }

    static boolean isUnaligned() {
        return UNALIGNED;
    }

    static boolean hasUnsafe() {
        return UNSAFE != null;
    }

    static boolean unalignedAccess() {
        return UNALIGNED;
    }

    static void throwException(Throwable cause) {
        // JVM has been observed to crash when passing a null argument. See https://github.com/netty/netty/issues/4131.
        UNSAFE.throwException(checkNotNull(cause, "cause"));
    }

    /**
     * 是否有直接内存构造函数，用该函数构造直接内存的类
     * 是否有 cleaner  无cleaner的情况使用freeMemory释放内存
     * @return
     */
    static boolean hasDirectBufferNoCleanerConstructor() {
        return DIRECT_BUFFER_CONSTRUCTOR != null;
    }

    /**
     * 重新分配内存
     * @param buffer
     * @param capacity
     * @return
     */
    static ByteBuffer reallocateDirectNoCleaner(ByteBuffer buffer, int capacity) {
        //directBufferAddress，找到这个buffer的地址，然后使用这个地址重新分配一块内存，返回一个地址，
        //然后新建一个新的直接内存类，将这块内存占住
        return newDirectBuffer(UNSAFE.reallocateMemory(directBufferAddress(buffer), capacity), capacity);
    }

    /**
     * 分配内存，并且不进行清理，不用原来的地址进行分配，
     * @param capacity
     * @return
     */
    static ByteBuffer allocateDirectNoCleaner(int capacity) {
        return newDirectBuffer(UNSAFE.allocateMemory(capacity), capacity);
    }

    static boolean hasAllocateArrayMethod() {
        return ALLOCATE_ARRAY_METHOD != null;
    }

    /**
     *
     * @param size
     * @return
     */
    static byte[] allocateUninitializedArray(int size) {
        try {
            return (byte[]) ALLOCATE_ARRAY_METHOD.invoke(INTERNAL_UNSAFE, byte.class, size);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw new Error(e);
        }
    }

    /**
     * 创建直接内存对象，
     * @param address -->内存地址
     * @param capacity -->内存容量
     * @return
     */
    static ByteBuffer newDirectBuffer(long address, int capacity) {
        ObjectUtil.checkPositiveOrZero(capacity, "capacity");

        try {
            return (ByteBuffer) DIRECT_BUFFER_CONSTRUCTOR.newInstance(address, capacity);
        } catch (Throwable cause) {
            // Not expected to ever throw!
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new Error(cause);
        }
    }

    /**
     * 找到直接内存类在这个偏移位置的变量，address
     * @param buffer
     * @return
     */
    static long directBufferAddress(ByteBuffer buffer) {
        return getLong(buffer, ADDRESS_FIELD_OFFSET);
    }

    static long byteArrayBaseOffset() {
        return BYTE_ARRAY_BASE_OFFSET;
    }

    static Object getObject(Object object, long fieldOffset) {
        return UNSAFE.getObject(object, fieldOffset);
    }

    /**
     * 获取某个对象的字段偏移，使用unsafe类
     * @param object
     * @param fieldOffset
     * @return
     */
    static int getInt(Object object, long fieldOffset) {
        return UNSAFE.getInt(object, fieldOffset);
    }

    /**
     * 获取偏移的Long类型对象
     * @param object
     * @param fieldOffset
     * @return
     */
    private static long getLong(Object object, long fieldOffset) {
        return UNSAFE.getLong(object, fieldOffset);
    }

    /**
     * 获取一个字段在一个类中的偏移量
     * @param field
     * @return
     */
    static long objectFieldOffset(Field field) {
        return UNSAFE.objectFieldOffset(field);
    }

    static byte getByte(long address) {
        return UNSAFE.getByte(address);
    }

    static short getShort(long address) {
        return UNSAFE.getShort(address);
    }

    static int getInt(long address) {
        return UNSAFE.getInt(address);
    }

    static long getLong(long address) {
        return UNSAFE.getLong(address);
    }

    static byte getByte(byte[] data, int index) {
        return UNSAFE.getByte(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static short getShort(byte[] data, int index) {
        return UNSAFE.getShort(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static int getInt(byte[] data, int index) {
        return UNSAFE.getInt(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static long getLong(byte[] data, int index) {
        return UNSAFE.getLong(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static void putByte(long address, byte value) {
        UNSAFE.putByte(address, value);
    }

    static void putShort(long address, short value) {
        UNSAFE.putShort(address, value);
    }

    static void putInt(long address, int value) {
        UNSAFE.putInt(address, value);
    }

    static void putLong(long address, long value) {
        UNSAFE.putLong(address, value);
    }

    static void putByte(byte[] data, int index, byte value) {
        UNSAFE.putByte(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putShort(byte[] data, int index, short value) {
        UNSAFE.putShort(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putInt(byte[] data, int index, int value) {
        UNSAFE.putInt(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putLong(byte[] data, int index, long value) {
       UNSAFE.putLong(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    /**
     * 内存拷贝，将某个地址， length大小的内存进行拷贝
     * @param srcAddr
     * @param dstAddr
     * @param length
     */
    static void copyMemory(long srcAddr, long dstAddr, long length) {
        //UNSAFE.copyMemory(srcAddr, dstAddr, length);
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            UNSAFE.copyMemory(srcAddr, dstAddr, size);
            length -= size;
            srcAddr += size;
            dstAddr += size;
        }
    }

    /**
     * 内存拷贝
     * @param src
     * @param srcOffset
     * @param dst
     * @param dstOffset
     * @param length
     */
    static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length) {
        //UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, length);
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, size);
            length -= size;
            srcOffset += size;
            dstOffset += size;
        }
    }

    static void setMemory(long address, long bytes, byte value) {
        UNSAFE.setMemory(address, bytes, value);
    }

    static void setMemory(Object o, long offset, long bytes, byte value) {
        UNSAFE.setMemory(o, offset, bytes, value);
    }

    static boolean equals(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        if (length <= 0) {
            return true;
        }
        final long baseOffset1 = BYTE_ARRAY_BASE_OFFSET + startPos1;
        final long baseOffset2 = BYTE_ARRAY_BASE_OFFSET + startPos2;
        int remainingBytes = length & 7;
        final long end = baseOffset1 + remainingBytes;
        for (long i = baseOffset1 - 8 + length, j = baseOffset2 - 8 + length; i >= end; i -= 8, j -= 8) {
            if (UNSAFE.getLong(bytes1, i) != UNSAFE.getLong(bytes2, j)) {
                return false;
            }
        }

        if (remainingBytes >= 4) {
            remainingBytes -= 4;
            if (UNSAFE.getInt(bytes1, baseOffset1 + remainingBytes) !=
                UNSAFE.getInt(bytes2, baseOffset2 + remainingBytes)) {
                return false;
            }
        }
        if (remainingBytes >= 2) {
            return UNSAFE.getChar(bytes1, baseOffset1) == UNSAFE.getChar(bytes2, baseOffset2) &&
                   (remainingBytes == 2 || bytes1[startPos1 + 2] == bytes2[startPos2 + 2]);
        }
        return bytes1[startPos1] == bytes2[startPos2];
    }

    static int equalsConstantTime(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        long result = 0;
        final long baseOffset1 = BYTE_ARRAY_BASE_OFFSET + startPos1;
        final long baseOffset2 = BYTE_ARRAY_BASE_OFFSET + startPos2;
        final int remainingBytes = length & 7;
        final long end = baseOffset1 + remainingBytes;
        for (long i = baseOffset1 - 8 + length, j = baseOffset2 - 8 + length; i >= end; i -= 8, j -= 8) {
            result |= UNSAFE.getLong(bytes1, i) ^ UNSAFE.getLong(bytes2, j);
        }
        switch (remainingBytes) {
            case 7:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getInt(bytes1, baseOffset1 + 3) ^ UNSAFE.getInt(bytes2, baseOffset2 + 3)) |
                        (UNSAFE.getChar(bytes1, baseOffset1 + 1) ^ UNSAFE.getChar(bytes2, baseOffset2 + 1)) |
                        (UNSAFE.getByte(bytes1, baseOffset1) ^ UNSAFE.getByte(bytes2, baseOffset2)), 0);
            case 6:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getInt(bytes1, baseOffset1 + 2) ^ UNSAFE.getInt(bytes2, baseOffset2 + 2)) |
                        (UNSAFE.getChar(bytes1, baseOffset1) ^ UNSAFE.getChar(bytes2, baseOffset2)), 0);
            case 5:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getInt(bytes1, baseOffset1 + 1) ^ UNSAFE.getInt(bytes2, baseOffset2 + 1)) |
                        (UNSAFE.getByte(bytes1, baseOffset1) ^ UNSAFE.getByte(bytes2, baseOffset2)), 0);
            case 4:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getInt(bytes1, baseOffset1) ^ UNSAFE.getInt(bytes2, baseOffset2)), 0);
            case 3:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getChar(bytes1, baseOffset1 + 1) ^ UNSAFE.getChar(bytes2, baseOffset2 + 1)) |
                        (UNSAFE.getByte(bytes1, baseOffset1) ^ UNSAFE.getByte(bytes2, baseOffset2)), 0);
            case 2:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getChar(bytes1, baseOffset1) ^ UNSAFE.getChar(bytes2, baseOffset2)), 0);
            case 1:
                return ConstantTimeUtils.equalsConstantTime(result |
                        (UNSAFE.getByte(bytes1, baseOffset1) ^ UNSAFE.getByte(bytes2, baseOffset2)), 0);
            default:
                return ConstantTimeUtils.equalsConstantTime(result, 0);
        }
    }

    static boolean isZero(byte[] bytes, int startPos, int length) {
        if (length <= 0) {
            return true;
        }
        final long baseOffset = BYTE_ARRAY_BASE_OFFSET + startPos;
        int remainingBytes = length & 7;
        final long end = baseOffset + remainingBytes;
        for (long i = baseOffset - 8 + length; i >= end; i -= 8) {
            if (UNSAFE.getLong(bytes, i) != 0) {
                return false;
            }
        }

        if (remainingBytes >= 4) {
            remainingBytes -= 4;
            if (UNSAFE.getInt(bytes, baseOffset + remainingBytes) != 0) {
                return false;
            }
        }
        if (remainingBytes >= 2) {
            return UNSAFE.getChar(bytes, baseOffset) == 0 &&
                    (remainingBytes == 2 || bytes[startPos + 2] == 0);
        }
        return bytes[startPos] == 0;
    }

    static int hashCodeAscii(byte[] bytes, int startPos, int length) {
        int hash = HASH_CODE_ASCII_SEED;
        final long baseOffset = BYTE_ARRAY_BASE_OFFSET + startPos;
        final int remainingBytes = length & 7;
        final long end = baseOffset + remainingBytes;
        for (long i = baseOffset - 8 + length; i >= end; i -= 8) {
            hash = hashCodeAsciiCompute(UNSAFE.getLong(bytes, i), hash);
        }
        switch(remainingBytes) {
        case 7:
            return ((hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getByte(bytes, baseOffset)))
                          * HASH_CODE_C2 + hashCodeAsciiSanitize(UNSAFE.getShort(bytes, baseOffset + 1)))
                          * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getInt(bytes, baseOffset + 3));
        case 6:
            return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getShort(bytes, baseOffset)))
                         * HASH_CODE_C2 + hashCodeAsciiSanitize(UNSAFE.getInt(bytes, baseOffset + 2));
        case 5:
            return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getByte(bytes, baseOffset)))
                         * HASH_CODE_C2 + hashCodeAsciiSanitize(UNSAFE.getInt(bytes, baseOffset + 1));
        case 4:
            return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getInt(bytes, baseOffset));
        case 3:
            return (hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getByte(bytes, baseOffset)))
                         * HASH_CODE_C2 + hashCodeAsciiSanitize(UNSAFE.getShort(bytes, baseOffset + 1));
        case 2:
            return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getShort(bytes, baseOffset));
        case 1:
            return hash * HASH_CODE_C1 + hashCodeAsciiSanitize(UNSAFE.getByte(bytes, baseOffset));
        default:
            return hash;
        }
    }

    static int hashCodeAsciiCompute(long value, int hash) {
        // masking with 0x1f reduces the number of overall bits that impact the hash code but makes the hash
        // code the same regardless of character case (upper case or lower case hash is the same).
        return hash * HASH_CODE_C1 +
                // Low order int
                hashCodeAsciiSanitize((int) value) * HASH_CODE_C2 +
                // High order int
                (int) ((value & 0x1f1f1f1f00000000L) >>> 32);
    }

    static int hashCodeAsciiSanitize(int value) {
        return value & 0x1f1f1f1f;
    }

    static int hashCodeAsciiSanitize(short value) {
        return value & 0x1f1f;
    }

    static int hashCodeAsciiSanitize(byte value) {
        return value & 0x1f;
    }

    static ClassLoader getClassLoader(final Class<?> clazz) {
        if (System.getSecurityManager() == null) {
            return clazz.getClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return clazz.getClassLoader();
                }
            });
        }
    }

    static ClassLoader getContextClassLoader() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return Thread.currentThread().getContextClassLoader();
                }
            });
        }
    }

    static ClassLoader getSystemClassLoader() {
        if (System.getSecurityManager() == null) {
            return ClassLoader.getSystemClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return ClassLoader.getSystemClassLoader();
                }
            });
        }
    }

    static int addressSize() {
        return UNSAFE.addressSize();
    }

    static long allocateMemory(long size) {
        return UNSAFE.allocateMemory(size);
    }

    static void freeMemory(long address) {
        UNSAFE.freeMemory(address);
    }

    static long reallocateMemory(long address, long newSize) {
        return UNSAFE.reallocateMemory(address, newSize);
    }

    static boolean isAndroid() {
        return IS_ANDROID;
    }

    private static boolean isAndroid0() {
        boolean android;
        try {
            Class.forName("android.app.Application", false, getSystemClassLoader());
            android = true;
        } catch (Throwable ignored) {
            // Failed to load the class uniquely available in Android.
            android = false;
        }

        if (android) {
            logger.debug("Platform: Android");
        }
        return android;
    }

    static int javaVersion() {
        return JAVA_VERSION;
    }

    private static int javaVersion0() {
        final int majorVersion;

        if (isAndroid0()) {
            majorVersion = 6;
        } else {
            majorVersion = majorVersionFromJavaSpecificationVersion();
        }

        logger.debug("Java version: {}", majorVersion);

        return majorVersion;
    }

    // Package-private for testing only
    static int majorVersionFromJavaSpecificationVersion() {
        return majorVersion(SystemPropertyUtil.get("java.specification.version", "1.6"));
    }

    // Package-private for testing only
    static int majorVersion(final String javaSpecVersion) {
        final String[] components = javaSpecVersion.split("\\.");
        final int[] version = new int[components.length];
        for (int i = 0; i < components.length; i++) {
            version[i] = Integer.parseInt(components[i]);
        }

        if (version[0] == 1) {
            assert version[1] >= 6;
            return version[1];
        } else {
            return version[0];
        }
    }

    private PlatformDependent0() {
    }

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {


        /*
            这里的theUnsafe是静态字段   ，   所以下面在访问的时候get()时候参数可以为null
            使用这种方式返回unsafe类
         */
        final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        Throwable cause = ReflectionUtil.trySetAccessible(unsafeField);//返回Throwable
        if (cause != null) {
            System.out.println(cause);
        }

        // the unsafe instance
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        System.out.println(unsafe);

        System.out.println(unsafe.arrayIndexScale(byte[].class)); //1
        System.out.println(unsafe.arrayBaseOffset(byte[].class)); //16
        System.out.println(unsafe.arrayIndexScale(Object[].class)); //4



    }
}
