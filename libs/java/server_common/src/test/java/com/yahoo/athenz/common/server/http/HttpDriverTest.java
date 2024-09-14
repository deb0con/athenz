/*
 * Copyright The Athenz Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yahoo.athenz.common.server.http;

import com.oath.auth.KeyRefresherException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class HttpDriverTest {
    private final ClassLoader classLoader = this.getClass().getClassLoader();

    @Test
    public void testDriverThrowsException() throws IllegalArgumentException {
        String certFile = classLoader.getResource("driver.cert.pem").getFile();
        String keyFile = classLoader.getResource("unit_test_driver.key.pem").getFile();
        System.setProperty("athenz.cert_refresher.tls_algorithm", "unknown");
        try {
            new HttpDriver.Builder("/tmp/truststore-path", "asdf".toCharArray(), keyFile, certFile)
                    .maxPoolPerRoute(20)
                    .maxPoolTotal(30)
                    .clientRetryIntervalMs(5000)
                    .clientMaxRetries(2)
                    .clientConnectTimeoutMs(5000)
                    .clientReadTimeoutMs(5000)
                    .build();
            fail();
        } catch (Exception ignored) {
        }
        System.clearProperty("athenz.cert_refresher.tls_algorithm");
    }

    @Test
    public void testDriverInit() {
        String caCertFile = classLoader.getResource("driver.truststore.jks").getFile();
        String certFile = classLoader.getResource("driver.cert.pem").getFile();
        String keyFile = classLoader.getResource("unit_test_driver.key.pem").getFile();

        // NOTE: the jks, cert, key are copied from cert refresher test resources
        // the jks had 123456 as the password
        HttpDriver httpDriver = new HttpDriver.Builder(caCertFile, "123456".toCharArray(), certFile, keyFile)
                .maxPoolPerRoute(20)
                .maxPoolTotal(30)
                .clientRetryIntervalMs(5000)
                .clientMaxRetries(2)
                .clientConnectTimeoutMs(5000)
                .clientReadTimeoutMs(5000)
                .build();

        httpDriver.close();
    }

    @Test
    public void testDoGet() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse httpResponse = Mockito.mock(CloseableHttpResponse.class);
        HttpEntity entity = Mockito.mock(HttpEntity.class);

        String data = "Sample Server Response";

        Mockito.when(httpResponse.getCode()).thenReturn(HttpStatus.SC_OK);
        Mockito.when(entity.getContent()).thenReturn(new ByteArrayInputStream(data.getBytes()));
        Mockito.when(httpResponse.getEntity()).thenReturn(entity);
        Mockito.when(httpClient.execute(Mockito.any(HttpGet.class))).thenReturn(httpResponse);

        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();

        httpDriver.setHttpClient(httpClient);

        String url = "https://localhost:4443/sample.html";

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Unit Tests");
        String out = httpDriver.doGet(url, headers);
        Assert.assertEquals(out, data);
    }

    @Test
    public void testDoGet404() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse httpResponse = Mockito.mock(CloseableHttpResponse.class);
        HttpEntity entity = Mockito.mock(HttpEntity.class);

        String data = "Not Found";

        Mockito.when(httpResponse.getCode()).thenReturn(HttpStatus.SC_NOT_FOUND);
        Mockito.when(entity.getContent()).thenReturn(new ByteArrayInputStream(data.getBytes()));
        Mockito.when(httpResponse.getEntity()).thenReturn(entity);
        Mockito.when(httpClient.execute(Mockito.any(HttpGet.class))).thenReturn(httpResponse);

        HttpDriver httpDriver = new HttpDriver.Builder(null).build();
        httpDriver.setHttpClient(httpClient);

        String url = "https://localhost:4443/sample.html";

        String out = httpDriver.doGet(url);
        Assert.assertEquals(out, "");
    }

    @Test
    public void testDoGetException() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);

        Mockito.when(httpClient.execute(Mockito.any(HttpGet.class))).thenThrow(new IOException("Unknown error"));

        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();

        httpDriver.setHttpClient(httpClient);

        String url = "https://localhost:4443/sample.html";

        try {
            httpDriver.doGet(url);
        } catch (IOException e) {
            Mockito.verify(httpClient, Mockito.times(2)).execute(Mockito.any(HttpGet.class));
        }
    }

    @Test
    public void testDoGetExecuteNullResponse() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(httpClient.execute(Mockito.any(HttpGet.class))).thenReturn(null);

        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();
        httpDriver.setHttpClient(httpClient);

        String url = "https://localhost:4443/sample.html";
        try {
            httpDriver.doGet(url);
        } catch (IOException e) {
            Mockito.verify(httpClient, Mockito.times(2)).execute(Mockito.any(HttpGet.class));
        }
    }

    @Test
    public void testDoPostHttpPost() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse httpResponse = Mockito.mock(CloseableHttpResponse.class);
        HttpEntity responseEntity = Mockito.mock(HttpEntity.class);

        String data = "Sample Server Response";

        Mockito.when(httpResponse.getCode()).thenReturn(HttpStatus.SC_OK);
        Mockito.when(responseEntity.getContent()).thenReturn(new ByteArrayInputStream(data.getBytes()));
        Mockito.when(httpResponse.getEntity()).thenReturn(responseEntity);
        Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenReturn(httpResponse);

        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();
        httpDriver.setHttpClient(httpClient);

        HttpPost httpPost = new HttpPost("https://localhost:4443/sample");

        // prepare POST body
        String body = "<?xml version='1.0'?><methodCall><methodName>test.test</methodName></methodCall>";

        // set POST body
        HttpEntity entity = new StringEntity(body);
        httpPost.setEntity(entity);

        String out = httpDriver.doPost(httpPost);
        Assert.assertEquals(out, data);
    }

    @Test
    public void testDoPostHttpPostResponse() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse httpResponse = Mockito.mock(CloseableHttpResponse.class);
        HttpEntity responseEntity = Mockito.mock(HttpEntity.class);

        String data = "Sample Server Response";

        Mockito.when(httpResponse.getCode()).thenReturn(HttpStatus.SC_OK);
        Mockito.when(responseEntity.getContent()).thenReturn(new ByteArrayInputStream(data.getBytes()));
        Mockito.when(httpResponse.getEntity()).thenReturn(responseEntity);
        Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenReturn(httpResponse);

        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();
        httpDriver.setHttpClient(httpClient);

        HttpPost httpPost = new HttpPost("https://localhost:4443/sample");

        // prepare POST body
        String body = "<?xml version='1.0'?><methodCall><methodName>test.test</methodName></methodCall>";

        // set POST body
        HttpEntity entity = new StringEntity(body);
        httpPost.setEntity(entity);

        HttpDriverResponse httpDriverResponse = httpDriver.doPostHttpResponse(httpPost);
        Assert.assertEquals(httpDriverResponse.getMessage(), data);
        Assert.assertEquals(httpDriverResponse.getStatusCode(), HttpStatus.SC_OK);
    }

    @Test
    public void testDoPostHttpPostResponseFailure() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse httpResponse = Mockito.mock(CloseableHttpResponse.class);
        HttpEntity responseEntity = Mockito.mock(HttpEntity.class);

        String data = "ERROR RESPONSE FROM SERVER";

        Mockito.when(httpResponse.getCode()).thenReturn(HttpStatus.SC_BAD_GATEWAY);
        Mockito.when(responseEntity.getContent()).thenReturn(new ByteArrayInputStream(data.getBytes()));
        Mockito.when(httpResponse.getEntity()).thenReturn(responseEntity);
        Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenReturn(httpResponse);

        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();
        httpDriver.setHttpClient(httpClient);

        HttpPost httpPost = new HttpPost("https://localhost:4443/sample");

        // prepare POST body
        String body = "<?xml version='1.0'?><methodCall><methodName>test.test</methodName></methodCall>";

        // set POST body
        HttpEntity entity = new StringEntity(body);
        httpPost.setEntity(entity);

        HttpDriverResponse httpDriverResponse = httpDriver.doPostHttpResponse(httpPost);
        Assert.assertEquals(httpDriverResponse.getMessage(), data);
        Assert.assertEquals(httpDriverResponse.getStatusCode(), HttpStatus.SC_BAD_GATEWAY);
    }

    @Test
    public void testDoPost200() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse httpResponse = Mockito.mock(CloseableHttpResponse.class);
        HttpEntity entity = Mockito.mock(HttpEntity.class);

        String data = "Sample Server Response";

        Mockito.when(httpResponse.getCode()).thenReturn(HttpStatus.SC_OK);
        Mockito.when(entity.getContent()).thenReturn(new ByteArrayInputStream(data.getBytes()));
        Mockito.when(httpResponse.getEntity()).thenReturn(entity);
        Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenReturn(httpResponse);

        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();
        httpDriver.setHttpClient(httpClient);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("data", "value"));

        String url = "https://localhost:4443/sample";

        String out = httpDriver.doPost(url, params);
        Assert.assertEquals(out, data);
    }

    @Test
    public void testDoPost201() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse httpResponse = Mockito.mock(CloseableHttpResponse.class);
        HttpEntity entity = Mockito.mock(HttpEntity.class);

        String data = "Sample Server Response";

        Mockito.when(httpResponse.getCode()).thenReturn(HttpStatus.SC_CREATED);
        Mockito.when(entity.getContent()).thenReturn(new ByteArrayInputStream(data.getBytes()));
        Mockito.when(httpResponse.getEntity()).thenReturn(entity);
        Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenReturn(httpResponse);

        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();
        httpDriver.setHttpClient(httpClient);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("data", "value"));

        String url = "https://localhost:4443/sample";

        String out = httpDriver.doPost(url, params);
        Assert.assertEquals(out, data);
    }

    @Test
    public void testDoPost404() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse httpResponse = Mockito.mock(CloseableHttpResponse.class);
        HttpEntity entity = Mockito.mock(HttpEntity.class);

        String data = "Not Found";

        Mockito.when(httpResponse.getCode()).thenReturn(HttpStatus.SC_NOT_FOUND);
        Mockito.when(entity.getContent()).thenReturn(new ByteArrayInputStream(data.getBytes()));
        Mockito.when(httpResponse.getEntity()).thenReturn(entity);
        Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenReturn(httpResponse);

        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();
        httpDriver.setHttpClient(httpClient);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("data", "value"));

        String url = "https://localhost:4443/sample";

        String out = httpDriver.doPost(url, params);
        Assert.assertEquals(out, "");
    }

    @Test
    public void testDoPostException() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);

        Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenThrow(new IOException("Unknown error"));

        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();
        httpDriver.setHttpClient(httpClient);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("data", "value"));

        String url = "https://localhost:4443/sample";
        try {
            httpDriver.doPost(url, params);
        } catch (IOException e) {
            Mockito.verify(httpClient, Mockito.times(2)).execute(Mockito.any(HttpPost.class));
        }
    }

    @Test
    public void testDoPostExecuteNullResponse() throws IOException {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(httpClient.execute(Mockito.any(HttpPost.class))).thenReturn(null);

        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();
        httpDriver.setHttpClient(httpClient);

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("data", "value"));

        String url = "https://localhost:4443/sample";
        try {
            httpDriver.doPost(url, params);
        } catch (IOException e) {
            Mockito.verify(httpClient, Mockito.times(2)).execute(Mockito.any(HttpPost.class));
        }
    }

    @Test
    public void testClose() {
        CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
        HttpDriver httpDriver = new HttpDriver.Builder(null, "asdf".toCharArray(), null, null)
                .build();
        httpDriver.setHttpClient(httpClient);
        httpDriver.close();

        httpDriver.setHttpClient(null);
        httpDriver.close();
    }

    @Test
    public void testCreateSSLContext() throws KeyRefresherException, IOException, InterruptedException {
        assertNull(HttpDriver.createSSLContext(null, null, null, null));
    }
}

