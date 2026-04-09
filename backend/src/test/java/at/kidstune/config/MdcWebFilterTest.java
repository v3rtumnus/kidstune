package at.kidstune.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcWebFilterTest {

    private final MdcWebFilter filter = new MdcWebFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void setsXRequestIdResponseHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());

        AtomicReference<String> capturedRequestId = new AtomicReference<>();

        WebFilterChain chain = ex -> {
            capturedRequestId.set(ex.getResponse().getHeaders().getFirst("X-Request-Id"));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedRequestId.get()).isNotNull().hasSize(16);
    }

    @Test
    void clearsMdcAfterFilterChainCompletes() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void generatesUniqueRequestIdPerRequest() {
        MockServerWebExchange exchange1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());
        MockServerWebExchange exchange2 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());

        AtomicReference<String> id1 = new AtomicReference<>();
        AtomicReference<String> id2 = new AtomicReference<>();

        filter.filter(exchange1, ex -> {
            id1.set(ex.getResponse().getHeaders().getFirst("X-Request-Id"));
            return Mono.empty();
        }).block();

        filter.filter(exchange2, ex -> {
            id2.set(ex.getResponse().getHeaders().getFirst("X-Request-Id"));
            return Mono.empty();
        }).block();

        assertThat(id1.get()).isNotNull().isNotEqualTo(id2.get());
    }

    @Test
    void filterCompletesSuccessfullyWithoutSecurityContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());

        // No security context in the reactive chain — should not throw
        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();
    }
}
