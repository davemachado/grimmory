package org.booklore.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.booklore.BookloreApplication;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code server.tomcat.remoteip.internal-proxies} (backed by the
 * {@code INTERNAL_PROXIES} env var) controls which peers are trusted to set
 * X-Forwarded-For, since this is Tomcat connector-level behaviour that mocking
 * HttpServletRequest cannot exercise.
 */
@SpringBootTest(
        classes = {BookloreApplication.class, RemoteAddrTrustIntegrationTest.RemoteAddrTestController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class RemoteAddrTrustIntegrationTest {

    @LocalServerPort
    private int port;

    private String remoteAddrFor(String forwardedFor) {
        RestTestClient client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        return client.get()
                .uri("/__test/remote-addr")
                .header("X-Forwarded-For", forwardedFor)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void defaultConfigTrustsLoopbackAndHonoursForwardedFor() {
        // RestTestClient connects from 127.0.0.1, which is in the default
        // internal-proxies range (same range that covers every default Docker
        // Compose bridge network), so the header must be trusted.
        assertThat(remoteAddrFor("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void twoClientsBehindTrustedProxyGetIndependentAddresses() {
        // Directly encodes the #78 bug scenario: two distinct "users" behind the
        // same trusted proxy must resolve to distinct IPs, not the proxy's IP,
        // so they land in independent rate-limit buckets.
        assertThat(remoteAddrFor("203.0.113.10")).isEqualTo("203.0.113.10");
        assertThat(remoteAddrFor("203.0.113.20")).isEqualTo("203.0.113.20");
    }

    @Nested
    @SpringBootTest(
            classes = {BookloreApplication.class, RemoteAddrTestController.class},
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
    )
    @ActiveProfiles("test")
    @TestPropertySource(properties = "INTERNAL_PROXIES=203\\.0\\.113\\.\\d{1,3}")
    class WithNarrowedTrustList {

        @LocalServerPort
        private int port;

        @Test
        void untrustedPeerCannotSpoofForwardedFor() {
            // Loopback (127.0.0.1, the actual RestTestClient peer) is no longer
            // in the trust list once INTERNAL_PROXIES is overridden to an
            // unrelated range, so the header must be ignored and the real socket
            // peer address returned instead - guards against an overly broad
            // override silently trusting arbitrary clients.
            RestTestClient client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
            String body = client.get()
                    .uri("/__test/remote-addr")
                    .header("X-Forwarded-For", "198.51.100.99")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body).isNotEqualTo("198.51.100.99");
            assertThat(body).isIn("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
        }
    }

    @RestController
    static class RemoteAddrTestController {

        @GetMapping("/__test/remote-addr")
        String remoteAddr(HttpServletRequest request) {
            return request.getRemoteAddr();
        }
    }
}
