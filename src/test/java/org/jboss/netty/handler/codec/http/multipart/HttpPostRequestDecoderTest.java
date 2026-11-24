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
package org.jboss.netty.handler.codec.http.multipart;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.Arrays;

import static org.jboss.netty.util.internal.PlatformDependentTest.*;
import static org.junit.Assert.*;

/** {@link HttpPostRequestDecoder} test case. */
public class HttpPostRequestDecoderTest {
    @Test
    public void testBinaryStreamUpload() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";

        final DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "http://localhost");

        req.setContent(ChannelBuffers.EMPTY_BUFFER);
        req.setHeader(HttpHeaders.Names.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        req.setChunked(true);

        // Force to use memory-based data.
        final DefaultHttpDataFactory inMemoryFactory = new DefaultHttpDataFactory(false);

        for (String data : Arrays.asList("", "\r", "\r\r", "\r\r\r")) {
            final String body =
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
                    "Content-Type: image/gif\r\n" +
                    "\r\n" +
                    data + "\r\n" +
                    "--" + boundary + "--\r\n";

            // Create decoder instance to test.
            final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(inMemoryFactory, req);

            decoder.offer(new DefaultHttpChunk(ChannelBuffers.copiedBuffer(body, CharsetUtil.UTF_8)));
            decoder.offer(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER));

            // Validate it's enough chunks to decode upload.
            assertTrue(decoder.hasNext());

            // Decode binary upload.
            MemoryFileUpload upload = (MemoryFileUpload) decoder.next();

            // Validate data has been parsed correctly as it was passed into request.
            assertArrayEquals("Invalid decoded data [data=" + data + "upload=" + upload + ']',
                data.getBytes(), upload.get());
        }
    }

    @Test
    public void testDiskUploadIsStoredInFilesNotReadableByOthers() throws Exception {
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";

        final DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "http://localhost");

        req.setContent(ChannelBuffers.EMPTY_BUFFER);
        req.setHeader(HttpHeaders.Names.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        req.setChunked(true);

        // Force to use disk-based data.
        final DefaultHttpDataFactory diskFactory = new DefaultHttpDataFactory(true);

        final String body =
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"password\"\r\n" +
                "\r\n" +
                "secret\r\n" +
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"secret.txt\"\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "secret content\r\n" +
                "--" + boundary + "--\r\n";

        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(diskFactory, req);
        try {
            decoder.offer(new DefaultHttpChunk(ChannelBuffers.copiedBuffer(body, CharsetUtil.UTF_8)));
            decoder.offer(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER));

            assertTrue(decoder.hasNext());
            DiskAttribute attribute = (DiskAttribute) decoder.next();
            assertEquals("secret", attribute.getValue());
            // The uploaded data must not be readable by other local users.
            assertNotReadableByOthers(attribute.getFile());

            assertTrue(decoder.hasNext());
            DiskFileUpload upload = (DiskFileUpload) decoder.next();
            assertEquals("secret content", upload.getString(CharsetUtil.UTF_8));
            assertNotReadableByOthers(upload.getFile());
        } finally {
            decoder.cleanFiles();
        }
    }
}
