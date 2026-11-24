/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;

import org.junit.Test;

import static org.junit.Assert.*;

public class HttpMessageDecoderTest {

    @Test
    public void testHeaderWithNoValueAndMissingColon() {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "Host:\r\n" +
                "netty.io\r\n\r\n";

        try {
            embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testMultipleContentLengthHeaders() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Content-Length: 0\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeaders2() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 0\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithCommaValue() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1,1\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersWithFolding() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 5\r\n" +
                "Content-Length:\r\n" +
                "\t6\r\n\r\n" +
                "123456";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthAndTransferEncodingChunked() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Content-Length: 5\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "0\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithPositiveSign() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: +1\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithNegativeSign() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: -1\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithNegativeValue() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: -10\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersSameValue() {
        // When allowDuplicateContentLengths is false (default), even identical values should be rejected
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "Content-Length: 0\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersSameValueWithComma() {
        // When allowDuplicateContentLengths is false (default), even identical comma-separated values should be rejected
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0, 0\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    // ---------------------------------------------------------------------
    // GHSA-wx5j-54mm-rqqq: header names must be bracketed by OWS only.
    // The decoder used to silently strip every Character.isWhitespace char,
    // which let an attacker smuggle control characters around a header name.
    // The strict OWS check now rejects 0x0c (FF) and 0x1c-0x1f (FS/GS/RS/US)
    // both before the name and between the name and the colon.
    // ---------------------------------------------------------------------

    @Test
    public void testRequestHeaderNameStartsWithControlChar1c() {
        testRequestHeaderNameStartsWithControlChar(0x1c);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1d() {
        testRequestHeaderNameStartsWithControlChar(0x1d);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1e() {
        testRequestHeaderNameStartsWithControlChar(0x1e);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1f() {
        testRequestHeaderNameStartsWithControlChar(0x1f);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar0c() {
        testRequestHeaderNameStartsWithControlChar(0x0c);
    }

    private static void testRequestHeaderNameStartsWithControlChar(int controlChar) {
        ChannelBuffer requestBuffer = ChannelBuffers.dynamicBuffer();
        requestBuffer.writeBytes("GET /some/path HTTP/1.1\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeByte(controlChar);
        requestBuffer.writeBytes("Transfer-Encoding: chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidRequestHeaders0(requestBuffer);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1c() {
        testRequestHeaderNameEndsWithControlChar(0x1c);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1d() {
        testRequestHeaderNameEndsWithControlChar(0x1d);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1e() {
        testRequestHeaderNameEndsWithControlChar(0x1e);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1f() {
        testRequestHeaderNameEndsWithControlChar(0x1f);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar0c() {
        testRequestHeaderNameEndsWithControlChar(0x0c);
    }

    private static void testRequestHeaderNameEndsWithControlChar(int controlChar) {
        ChannelBuffer requestBuffer = ChannelBuffers.dynamicBuffer();
        requestBuffer.writeBytes("GET /some/path HTTP/1.1\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeBytes("Transfer-Encoding".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeByte(controlChar);
        requestBuffer.writeBytes(": chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidRequestHeaders0(requestBuffer);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1c() {
        testResponseHeaderNameStartsWithControlChar(0x1c);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1d() {
        testResponseHeaderNameStartsWithControlChar(0x1d);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1e() {
        testResponseHeaderNameStartsWithControlChar(0x1e);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1f() {
        testResponseHeaderNameStartsWithControlChar(0x1f);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar0c() {
        testResponseHeaderNameStartsWithControlChar(0x0c);
    }

    private static void testResponseHeaderNameStartsWithControlChar(int controlChar) {
        ChannelBuffer responseBuffer = ChannelBuffers.dynamicBuffer();
        responseBuffer.writeBytes("HTTP/1.1 200 OK\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeByte(controlChar);
        responseBuffer.writeBytes("Transfer-Encoding: chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidResponseHeaders0(responseBuffer);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1c() {
        testResponseHeaderNameEndsWithControlChar(0x1c);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1d() {
        testResponseHeaderNameEndsWithControlChar(0x1d);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1e() {
        testResponseHeaderNameEndsWithControlChar(0x1e);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1f() {
        testResponseHeaderNameEndsWithControlChar(0x1f);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar0c() {
        testResponseHeaderNameEndsWithControlChar(0x0c);
    }

    private static void testResponseHeaderNameEndsWithControlChar(int controlChar) {
        ChannelBuffer responseBuffer = ChannelBuffers.dynamicBuffer();
        responseBuffer.writeBytes("HTTP/1.1 200 OK\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeBytes("Transfer-Encoding".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeByte(controlChar);
        responseBuffer.writeBytes(": chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidResponseHeaders0(responseBuffer);
    }

    // ---------------------------------------------------------------------
    // CVE-2019-16869: no whitespace is allowed between a header field-name and
    // the colon (RFC 7230 section 3.2.4). For requests this must be rejected
    // (400 Bad Request); for responses the whitespace is silently skipped so
    // that interop with non-compliant servers is preserved.
    // Backport of netty/netty@39cafcb (issue #9571 / PR #9585).
    // ---------------------------------------------------------------------

    @Test
    public void testWhitespaceInRequestHeaderName() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Transfer-Encoding : chunked\r\n" +
                "Host: netty.io\r\n\r\n";
        testInvalidRequestHeaders0(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
    }

    @Test
    public void testWhitespaceInResponseHeaderName() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding : chunked\r\n" +
                "Host: netty.io\r\n\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        embedder.offer(ChannelBuffers.copiedBuffer(responseStr, CharsetUtil.US_ASCII));
        HttpResponse response = (HttpResponse) embedder.poll();
        assertNotNull(response);
        assertEquals(HttpHeaders.Values.CHUNKED,
                response.getHeader(HttpHeaders.Names.TRANSFER_ENCODING));
        assertEquals("netty.io", response.getHeader(HttpHeaders.Names.HOST));
    }

    // ---------------------------------------------------------------------
    // CVE-2026-93566: the chunk-size line must follow the strict grammar
    //   chunk-size [ BWS ";" chunk-ext ]
    // The decoder used to trim the line and silently truncate it at the first
    // ';', whitespace or control character (and to drop a bare CR together
    // with the byte that followed it), so a malformed chunk-size line was
    // accepted and could be used to smuggle a second HTTP message.
    // Backport of netty/netty@161f9ad.
    // ---------------------------------------------------------------------

    @Test
    public void testRequestChunkSizeEmpty() {
        testInvalidRequestChunkSize("");
    }

    @Test
    public void testRequestChunkSizeTrailingWhitespace() {
        testInvalidRequestChunkSize("5 ");
    }

    @Test
    public void testRequestChunkSizeLeadingWhitespace() {
        testInvalidRequestChunkSize(" 5");
    }

    @Test
    public void testRequestChunkSizeWhitespaceThenHexDigit() {
        testInvalidRequestChunkSize("5 c");
    }

    @Test
    public void testRequestChunkSizeWhitespaceThenToken() {
        testInvalidRequestChunkSize("5 x");
    }

    @Test
    public void testRequestChunkSizeWhitespaceThenHexDigitAndExtension() {
        testInvalidRequestChunkSize("5 c;foo=bar");
    }

    @Test
    public void testRequestChunkSizeLeadingWhitespaceThenHexDigit() {
        testInvalidRequestChunkSize("  5 c");
    }

    @Test
    public void testRequestChunkSizeLeadingWhitespaceThenToken() {
        testInvalidRequestChunkSize("  5 x");
    }

    @Test
    public void testRequestChunkSizeLeadingWhitespaceThenHexDigitAndExtension() {
        testInvalidRequestChunkSize("  5 c;foo=bar");
    }

    @Test
    public void testRequestChunkSizeWithNul() {
        testInvalidRequestChunkSize("5\u0000");
    }

    @Test
    public void testRequestChunkSizeWithVerticalTab() {
        testInvalidRequestChunkSize("5\u000b");
    }

    @Test
    public void testRequestChunkSizeWithLeadingControlChar() {
        testInvalidRequestChunkSize("\u00015");
    }

    @Test
    public void testRequestChunkSizeWithBareCr() {
        testInvalidRequestChunkSize("5\rX");
    }

    @Test
    public void testRequestChunkSizeWithSign() {
        testInvalidRequestChunkSize("+5");
    }

    @Test
    public void testResponseChunkSizeEmpty() {
        testInvalidResponseChunkSize("");
    }

    @Test
    public void testResponseChunkSizeTrailingWhitespace() {
        testInvalidResponseChunkSize("5 ");
    }

    @Test
    public void testResponseChunkSizeLeadingWhitespace() {
        testInvalidResponseChunkSize(" 5");
    }

    @Test
    public void testResponseChunkSizeWhitespaceThenHexDigit() {
        testInvalidResponseChunkSize("5 c");
    }

    @Test
    public void testResponseChunkSizeWhitespaceThenToken() {
        testInvalidResponseChunkSize("5 x");
    }

    @Test
    public void testResponseChunkSizeWhitespaceThenHexDigitAndExtension() {
        testInvalidResponseChunkSize("5 c;foo=bar");
    }

    @Test
    public void testResponseChunkSizeLeadingWhitespaceThenHexDigit() {
        testInvalidResponseChunkSize("  5 c");
    }

    @Test
    public void testResponseChunkSizeLeadingWhitespaceThenToken() {
        testInvalidResponseChunkSize("  5 x");
    }

    @Test
    public void testResponseChunkSizeLeadingWhitespaceThenHexDigitAndExtension() {
        testInvalidResponseChunkSize("  5 c;foo=bar");
    }

    @Test
    public void testResponseChunkSizeWithNul() {
        testInvalidResponseChunkSize("5\u0000");
    }

    @Test
    public void testResponseChunkSizeWithVerticalTab() {
        testInvalidResponseChunkSize("5\u000b");
    }

    @Test
    public void testResponseChunkSizeWithLeadingControlChar() {
        testInvalidResponseChunkSize("\u00015");
    }

    @Test
    public void testResponseChunkSizeWithBareCr() {
        testInvalidResponseChunkSize("5\rX");
    }

    @Test
    public void testResponseChunkSizeWithSign() {
        testInvalidResponseChunkSize("+5");
    }

    @Test
    public void testRequestChunkSizeOverflow() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                "80000000\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        try {
            embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof NumberFormatException);
        }
        HttpRequest request = (HttpRequest) embedder.poll();
        assertNotNull(request);
        assertTrue(request.isChunked());
        assertNull(embedder.poll());
    }

    @Test
    public void testRequestChunkSizeValid() {
        testValidRequestChunkSize("5");
    }

    @Test
    public void testRequestChunkSizeValidWithLeadingZeros() {
        testValidRequestChunkSize("00005");
    }

    @Test
    public void testRequestChunkSizeValidWithExtension() {
        testValidRequestChunkSize("5;foo=bar");
    }

    @Test
    public void testRequestChunkSizeValidWithBadWhitespaceBeforeExtension() {
        testValidRequestChunkSize("5 \t;foo=bar");
    }

    @Test
    public void testResponseChunkSizeValidWithBadWhitespaceBeforeExtension() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                "A ;foo=bar\r\n" +
                "helloworld\r\n" +
                "0\r\n" +
                "\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(responseStr, CharsetUtil.US_ASCII)));
        HttpResponse response = (HttpResponse) embedder.poll();
        assertNotNull(response);
        assertEquals(HttpResponseStatus.OK, response.getStatus());
        assertTrue(response.isChunked());
        HttpChunk chunk = (HttpChunk) embedder.poll();
        assertEquals("helloworld", chunk.getContent().toString(CharsetUtil.US_ASCII));
        HttpChunk lastChunk = (HttpChunk) embedder.poll();
        assertTrue(lastChunk.isLast());
        assertNull(embedder.poll());
    }

    private static void testValidRequestChunkSize(String chunkSizeLine) {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                chunkSizeLine + "\r\n" +
                "hello\r\n" +
                "0\r\n" +
                "\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        assertTrue(embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = (HttpRequest) embedder.poll();
        assertNotNull(request);
        assertEquals(HttpMethod.POST, request.getMethod());
        assertTrue(request.isChunked());
        HttpChunk chunk = (HttpChunk) embedder.poll();
        assertEquals("hello", chunk.getContent().toString(CharsetUtil.US_ASCII));
        HttpChunk lastChunk = (HttpChunk) embedder.poll();
        assertTrue(lastChunk.isLast());
        assertNull(embedder.poll());
    }

    private static void testInvalidRequestChunkSize(String chunkSizeLine) {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                chunkSizeLine + "\r\n" +
                "GPOST\r\n" +
                "0\r\n" +
                "\r\n" +
                "GET /smuggled HTTP/1.1\r\n" +
                "Host: example\r\n" +
                "\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        try {
            embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            // The malformed chunk-size line must be rejected, not truncated.
            assertInvalidChunkSize(e);
        }

        // The request headers parse fine.
        HttpRequest request = (HttpRequest) embedder.poll();
        assertNotNull(request);
        assertEquals(HttpVersion.HTTP_1_1, request.getProtocolVersion());
        assertEquals(HttpMethod.POST, request.getMethod());
        assertTrue(request.isChunked());

        assertNoContentAndNoSmuggledMessage(embedder);
    }

    private static void testInvalidResponseChunkSize(String chunkSizeLine) {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                chunkSizeLine + "\r\n" +
                "GPOST\r\n" +
                "0\r\n" +
                "\r\n" +
                "HTTP/1.1 200 smuggled\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        try {
            embedder.offer(ChannelBuffers.copiedBuffer(responseStr, CharsetUtil.US_ASCII));
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            // The malformed chunk-size line must be rejected, not truncated.
            assertInvalidChunkSize(e);
        }

        // The response headers parse fine.
        HttpResponse response = (HttpResponse) embedder.poll();
        assertNotNull(response);
        assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        assertEquals(HttpResponseStatus.OK, response.getStatus());
        assertTrue(response.isChunked());

        assertNoContentAndNoSmuggledMessage(embedder);
    }

    private static void assertInvalidChunkSize(CodecEmbedderException e) {
        Throwable cause = e.getCause();
        assertTrue(String.valueOf(cause), cause instanceof NumberFormatException);
        assertNotNull(cause.getMessage());
        assertTrue(cause.getMessage(), cause.getMessage().contains("chunk size"));
    }

    private static void assertNoContentAndNoSmuggledMessage(DecoderEmbedder<Object> embedder) {
        // No chunk of the malformed body may be surfaced.
        assertNull(embedder.poll());
        try {
            embedder.finish();
        } catch (CodecEmbedderException e) {
            // The decoder keeps rejecting the malformed chunk-size line when the channel is closed.
            assertInvalidChunkSize(e);
        }
        // Nothing after the failed chunk may be surfaced as a second, smuggled message.
        assertNull(embedder.poll());
    }

    private static void testInvalidHeaders0(String requestStr) {
        testInvalidRequestHeaders0(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
    }

    private static void testInvalidRequestHeaders0(ChannelBuffer requestBuffer) {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        try {
            embedder.offer(requestBuffer);
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    private static void testInvalidResponseHeaders0(ChannelBuffer responseBuffer) {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        try {
            embedder.offer(responseBuffer);
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }
}
