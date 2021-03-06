/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.http.impl.integration;

import java.io.IOException;
import java.net.Socket;

import org.apache.hc.client5.http.localserver.LocalServerTestBase;
import org.apache.hc.client5.http.methods.CloseableHttpResponse;
import org.apache.hc.client5.http.methods.HttpGet;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.entity.EntityUtils;
import org.apache.hc.core5.http.entity.StringEntity;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Test;

public class TestMalformedServerResponse extends LocalServerTestBase {

    static class BrokenServerConnection extends DefaultBHttpServerConnection {

        public BrokenServerConnection(final int buffersize) {
            super(buffersize);
        }

        @Override
        public void sendResponseHeader(final HttpResponse response) throws HttpException, IOException {
            super.sendResponseHeader(response);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT) {
                response.setEntity(new StringEntity(
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n"));
                sendResponseEntity(response);
            }
        }
    }

    static class BrokenServerConnectionFactory implements HttpConnectionFactory<DefaultBHttpServerConnection> {

        @Override
        public DefaultBHttpServerConnection createConnection(final Socket socket) throws IOException {
            final BrokenServerConnection conn = new BrokenServerConnection(4096);
            conn.bind(socket);
            return conn;
        }
    }

    @Test
    public void testNoContentResponseWithGarbage() throws Exception {
        this.serverBootstrap.setConnectionFactory(new BrokenServerConnectionFactory());
        this.serverBootstrap.registerHandler("/nostuff", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            }

        });
        this.serverBootstrap.registerHandler("/stuff", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_OK);
                response.setEntity(new StringEntity("Some important stuff"));
            }

        });

        final HttpHost target = start();
        final HttpGet get1 = new HttpGet("/nostuff");
        try (CloseableHttpResponse response1 = this.httpclient.execute(target, get1)) {
            Assert.assertEquals(HttpStatus.SC_NO_CONTENT, response1.getStatusLine().getStatusCode());
            EntityUtils.consume(response1.getEntity());
        }
        final HttpGet get2 = new HttpGet("/stuff");
        try (CloseableHttpResponse response2 = this.httpclient.execute(target, get2)) {
            Assert.assertEquals(HttpStatus.SC_OK, response2.getStatusLine().getStatusCode());
            EntityUtils.consume(response2.getEntity());
        }
    }

}
