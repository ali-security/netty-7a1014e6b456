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
package org.jboss.netty.handler.codec.http.multipart;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.jboss.netty.util.internal.PlatformDependentTest.*;
import static org.junit.Assert.*;

/** {@link DiskFileUpload} and {@link DiskAttribute} temporary file test cases. */
public class DiskFileUploadTest {

    @Test
    public void testSetContentStoresUploadInFileNotReadableByOthers() throws Exception {
        DiskFileUpload upload = new DiskFileUpload(
                "file", "secret.txt", "text/plain", null, CharsetUtil.UTF_8, 0);
        try {
            upload.setContent(ChannelBuffers.copiedBuffer("secret", CharsetUtil.UTF_8));
            File file = upload.getFile();
            assertNotNull(file);
            assertEquals("secret", upload.getString(CharsetUtil.UTF_8));
            assertNotReadableByOthers(file);
        } finally {
            upload.delete();
        }
    }

    @Test
    public void testAddContentStoresUploadInFileNotReadableByOthers() throws Exception {
        DiskFileUpload upload = new DiskFileUpload(
                "file", "secret.txt", "text/plain", null, CharsetUtil.UTF_8, 0);
        try {
            upload.addContent(ChannelBuffers.copiedBuffer("sec", CharsetUtil.UTF_8), false);
            upload.addContent(ChannelBuffers.copiedBuffer("ret", CharsetUtil.UTF_8), true);
            File file = upload.getFile();
            assertNotNull(file);
            assertEquals("secret", upload.getString(CharsetUtil.UTF_8));
            assertNotReadableByOthers(file);
        } finally {
            upload.delete();
        }
    }

    @Test
    public void testUploadInBaseDirectoryIsStoredInFileNotReadableByOthers() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "netty-upload-" + System.nanoTime());
        assertTrue(dir.mkdir());
        String oldBaseDirectory = DiskFileUpload.baseDirectory;
        DiskFileUpload.baseDirectory = dir.getPath();
        DiskFileUpload upload = new DiskFileUpload(
                "file", "secret.txt", "text/plain", null, CharsetUtil.UTF_8, 0);
        try {
            upload.setContent(ChannelBuffers.copiedBuffer("secret", CharsetUtil.UTF_8));
            File file = upload.getFile();
            assertEquals(dir.getCanonicalFile(), file.getParentFile().getCanonicalFile());
            assertNotReadableByOthers(file);
        } finally {
            DiskFileUpload.baseDirectory = oldBaseDirectory;
            upload.delete();
            dir.delete();
        }
    }

    @Test
    public void testAttributeIsStoredInFileNotReadableByOthers() throws Exception {
        DiskAttribute attribute = new DiskAttribute("password", "secret");
        try {
            File file = attribute.getFile();
            assertNotNull(file);
            assertEquals("secret", attribute.getValue());
            assertNotReadableByOthers(file);
        } finally {
            attribute.delete();
        }
    }

    @Test
    public void testAttributeWithNameOutsideOfDirectoryFailsWithIOException() throws Exception {
        DiskAttribute attribute = new DiskAttribute("../../password");
        try {
            attribute.setValue("secret");
            fail("must not store the attribute outside of the temporary directory: " + attribute.getFile());
        } catch (IOException e) {
            // expected
        } finally {
            attribute.delete();
        }
    }
}
