package org.ikigaidigital;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Compression is applied by the embedded server, so this test runs against a real HTTP port. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ResponseCompressionTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void compressesTimeDepositsResponseWhenClientAcceptsGzip() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/time-deposits"))
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();

        HttpResponse<InputStream> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofInputStream());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).hasValue("gzip");
        try (InputStream body = new GZIPInputStream(response.body())) {
            JsonNode timeDeposits = objectMapper.readTree(body);
            assertThat(timeDeposits.isArray()).isTrue();
            assertThat(timeDeposits).hasSize(6);
        }
    }
}
