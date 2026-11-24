/*
 * Copyright 2021 The Netty Project
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
package org.jboss.netty.util.internal;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility that performs operations which must be implemented differently
 * depending on the capabilities of the current runtime environment.
 *
 * @see <a href="https://github.com/netty/netty/security/advisories/GHSA-5mcr-gq6c-3hq2">CVE-2021-21290</a>
 */
public final class PlatformDependent {

    /** {@code java.nio.file.Files.createTempFile(String, String, FileAttribute...)} (Java 7+) */
    private static final Method FILES_CREATE_TEMP_FILE;
    /** {@code java.nio.file.Files.createTempFile(Path, String, String, FileAttribute...)} (Java 7+) */
    private static final Method FILES_CREATE_TEMP_FILE_IN_DIR;
    /** {@code java.io.File.toPath()} (Java 7+) */
    private static final Method FILE_TO_PATH;
    /** {@code java.nio.file.Path.toFile()} (Java 7+) */
    private static final Method PATH_TO_FILE;
    /** An empty {@code java.nio.file.attribute.FileAttribute[]} (Java 7+) */
    private static final Object EMPTY_FILE_ATTRIBUTES;
    /** {@code java.io.File.setReadable(boolean, boolean)} (Java 6+) */
    private static final Method FILE_SET_READABLE;

    static {
        Method createTempFile = null;
        Method createTempFileInDir = null;
        Method toPath = null;
        Method toFile = null;
        Object emptyFileAttributes = null;
        if (DetectionUtil.javaVersion() >= 7) {
            try {
                Class<?> filesClass = Class.forName("java.nio.file.Files");
                Class<?> pathClass = Class.forName("java.nio.file.Path");
                Class<?> fileAttributeClass = Class.forName("java.nio.file.attribute.FileAttribute");
                Object noAttributes = Array.newInstance(fileAttributeClass, 0);
                Method m1 = filesClass.getMethod(
                        "createTempFile", String.class, String.class, noAttributes.getClass());
                Method m2 = filesClass.getMethod(
                        "createTempFile", pathClass, String.class, String.class, noAttributes.getClass());
                Method m3 = File.class.getMethod("toPath");
                Method m4 = pathClass.getMethod("toFile");

                createTempFile = m1;
                createTempFileInDir = m2;
                toPath = m3;
                toFile = m4;
                emptyFileAttributes = noAttributes;
            } catch (Exception e) {
                // NIO.2 is not usable: use File.createTempFile and adjust the permissions instead.
            }
        }
        FILES_CREATE_TEMP_FILE = createTempFile;
        FILES_CREATE_TEMP_FILE_IN_DIR = createTempFileInDir;
        FILE_TO_PATH = toPath;
        PATH_TO_FILE = toFile;
        EMPTY_FILE_ATTRIBUTES = emptyFileAttributes;

        Method setReadable = null;
        try {
            setReadable = File.class.getMethod("setReadable", boolean.class, boolean.class);
        } catch (Exception e) {
            // Java 5: the permissions of a file cannot be adjusted.
        }
        FILE_SET_READABLE = setReadable;
    }

    /**
     * Creates a new empty file in the given directory (or the default temporary-file
     * directory if {@code directory} is {@code null}) which, unlike a file created by
     * {@link File#createTempFile(String, String, File)}, is not readable by other users
     * of the local system.
     *
     * @param prefix    the prefix string used in generating the file's name
     * @param suffix    the suffix string used in generating the file's name
     * @param directory the directory in which the file is to be created, or
     *                  {@code null} if the default temporary-file directory is to be used
     * @return the newly created file
     * @throws IOException if the file could not be created or its permissions
     *                     could not be restricted
     */
    public static File createTempFile(String prefix, String suffix, File directory) throws IOException {
        if (FILES_CREATE_TEMP_FILE != null) {
            // Files.createTempFile creates the file with owner-only permissions.
            return createTempFileNio(prefix, suffix, directory);
        }
        return createTempFileLegacy(prefix, suffix, directory);
    }

    private static File createTempFileNio(String prefix, String suffix, File directory) throws IOException {
        Object path;
        if (directory == null) {
            path = invoke(FILES_CREATE_TEMP_FILE, null, prefix, suffix, EMPTY_FILE_ATTRIBUTES);
        } else {
            Object directoryPath = invoke(FILE_TO_PATH, directory);
            path = invoke(FILES_CREATE_TEMP_FILE_IN_DIR, null, directoryPath, prefix, suffix, EMPTY_FILE_ATTRIBUTES);
        }
        return (File) invoke(PATH_TO_FILE, path);
    }

    /**
     * Creates the file with {@link File#createTempFile(String, String, File)} and then
     * restricts its read permission to the owner. Used when NIO.2 is not available.
     */
    static File createTempFileLegacy(String prefix, String suffix, File directory) throws IOException {
        final File file;
        if (directory == null) {
            file = File.createTempFile(prefix, suffix);
        } else {
            file = File.createTempFile(prefix, suffix, directory);
        }

        if (FILE_SET_READABLE != null) {
            // Try to adjust the perms, if this fails there is not much else we can do...
            if (!setReadable(file, false, false)) {
                throw new IOException("Failed to set permissions on temporary file " + file);
            }
            if (!setReadable(file, true, true)) {
                throw new IOException("Failed to set permissions on temporary file " + file);
            }
        }
        return file;
    }

    private static boolean setReadable(File file, boolean readable, boolean ownerOnly) throws IOException {
        return Boolean.TRUE.equals(invoke(FILE_SET_READABLE, file, readable, ownerOnly));
    }

    private static Object invoke(Method method, Object target, Object... args) throws IOException {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof IllegalArgumentException) {
                // Files.createTempFile rejects a prefix or suffix which is not part of a plain file name
                // with an IllegalArgumentException where File.createTempFile fails with an IOException.
                throw newIOException("Unable to create temporary file", cause);
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw newIOException("Unable to create temporary file", cause);
        } catch (IllegalAccessException e) {
            throw newIOException("Unable to create temporary file", e);
        }
    }

    private static IOException newIOException(String message, Throwable cause) {
        IOException e = new IOException(message + ": " + cause.getMessage());
        e.initCause(cause);
        return e;
    }

    private PlatformDependent() {
        // only static method supported
    }
}
