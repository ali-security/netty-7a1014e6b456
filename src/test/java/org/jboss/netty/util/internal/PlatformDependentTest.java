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

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

/** {@link PlatformDependent} test cases. */
public class PlatformDependentTest {

    @Test
    public void testCreateTempFileInDefaultDirectory() throws IOException {
        File file = PlatformDependent.createTempFile("netty-test-", ".tmp", null);
        try {
            assertTrue(file.isFile());
            assertEquals(0, file.length());
            assertTrue(file.getName().startsWith("netty-test-"));
            assertTrue(file.getName().endsWith(".tmp"));
            assertEquals(new File(System.getProperty("java.io.tmpdir")).getCanonicalFile(),
                    file.getParentFile().getCanonicalFile());
        } finally {
            file.delete();
        }
    }

    @Test
    public void testCreateTempFileInDirectory() throws IOException {
        File dir = newTempDirectory();
        File file = PlatformDependent.createTempFile("netty-test-", ".tmp", dir);
        try {
            assertTrue(file.isFile());
            assertTrue(file.getName().startsWith("netty-test-"));
            assertTrue(file.getName().endsWith(".tmp"));
            assertEquals(dir.getCanonicalFile(), file.getParentFile().getCanonicalFile());
        } finally {
            file.delete();
            dir.delete();
        }
    }

    @Test
    public void testCreateTempFileIsNotReadableByOthers() throws IOException {
        File file = PlatformDependent.createTempFile("netty-test-", ".tmp", null);
        try {
            assertNotReadableByOthers(file);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testCreateTempFileInDirectoryIsNotReadableByOthers() throws IOException {
        File dir = newTempDirectory();
        File file = PlatformDependent.createTempFile("netty-test-", ".tmp", dir);
        try {
            assertNotReadableByOthers(file);
        } finally {
            file.delete();
            dir.delete();
        }
    }

    @Test
    public void testCreateTempFileLegacyIsNotReadableByOthers() throws IOException {
        // File.setReadable(false, false) always fails on Windows, where files cannot be made unreadable.
        assumeTrue(!DetectionUtil.isWindows());
        File file = PlatformDependent.createTempFileLegacy("netty-test-", ".tmp", null);
        try {
            assertTrue(file.isFile());
            assertTrue(file.canRead());
            assertNotReadableByOthers(file);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testCreateTempFileLegacyInDirectoryIsNotReadableByOthers() throws IOException {
        assumeTrue(!DetectionUtil.isWindows());
        File dir = newTempDirectory();
        File file = PlatformDependent.createTempFileLegacy("netty-test-", ".tmp", dir);
        try {
            assertEquals(dir.getCanonicalFile(), file.getParentFile().getCanonicalFile());
            assertNotReadableByOthers(file);
        } finally {
            file.delete();
            dir.delete();
        }
    }

    @Test
    public void testCreateTempFileWithNameOutsideOfDirectoryFails() throws IOException {
        File dir = newTempDirectory();
        try {
            PlatformDependent.createTempFile("netty-test-", "_a/b.tmp", dir);
            fail("must not create a temporary file outside of " + dir);
        } catch (IOException e) {
            // expected
        } finally {
            dir.delete();
        }
    }

    private static File newTempDirectory() throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"), "netty-test-" + System.nanoTime());
        if (!dir.mkdir()) {
            throw new IOException("failed to create: " + dir);
        }
        return dir;
    }

    /**
     * Asserts that the given file can be read by its owner only, which is the case for a file
     * created by {@link PlatformDependent#createTempFile(String, String, File)}, but not by
     * {@link File#createTempFile(String, String, File)} which applies the umask
     * (e.g. {@code rw-r--r--}). Skipped when the POSIX permissions cannot be determined.
     */
    public static void assertNotReadableByOthers(File file) {
        Set<String> permissions = posixFilePermissions(file);
        assumeNotNull(permissions);
        assertTrue(file + ": " + permissions, permissions.contains("OWNER_READ"));
        assertFalse(file + ": " + permissions, permissions.contains("GROUP_READ"));
        assertFalse(file + ": " + permissions, permissions.contains("OTHERS_READ"));
    }

    /**
     * Returns the names of the POSIX permissions of the given file (e.g. {@code "GROUP_READ"}),
     * or {@code null} when they cannot be determined (Java 6, or a non-POSIX file system).
     */
    private static Set<String> posixFilePermissions(File file) {
        try {
            Class<?> filesClass = Class.forName("java.nio.file.Files");
            Class<?> pathClass = Class.forName("java.nio.file.Path");
            Object noLinkOptions = Array.newInstance(Class.forName("java.nio.file.LinkOption"), 0);
            Method getPosixFilePermissions = filesClass.getMethod(
                    "getPosixFilePermissions", pathClass, noLinkOptions.getClass());
            Object path = File.class.getMethod("toPath").invoke(file);
            Set<?> permissions = (Set<?>) getPosixFilePermissions.invoke(null, path, noLinkOptions);
            Set<String> names = new HashSet<String>();
            for (Object permission : permissions) {
                names.add(String.valueOf(permission));
            }
            return names;
        } catch (Exception e) {
            return null;
        }
    }
}
