package com.duriancare.gateway.filter;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Emits metadata-only traces for the mobile prediction request in local and test environments.
 * It deliberately excludes authorization and multipart values.
 */
@Component
public class PredictionRequestTraceFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(PredictionRequestTraceFilter.class);
    private static final String TRACE_HEADER = "X-DurianCare-Trace-Id";
    private static final String PREDICT_PATH = "/api/v1/predict";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!"POST".equals(request.getMethod().name()) || !PREDICT_PATH.equals(request.getPath().value())) {
            return chain.filter(exchange);
        }

        String traceId = request.getHeaders().getFirst(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route == null ? "unresolved" : route.getId();
        String upstream = route == null ? "unresolved" : route.getUri().toString();
        long startedAt = System.nanoTime();
        String finalTraceId = traceId;

        logger.info(
                "PREDICTION_TRACE gateway_received traceId={} method={} path={} routeId={} upstream={} contentType={} contentLength={}",
                traceId,
                request.getMethod(),
                request.getPath().value(),
                routeId,
                upstream,
                request.getHeaders().getContentType(),
                request.getHeaders().getContentLength());

        ServerHttpRequest tracedRequest = request.mutate()
                .headers(headers -> headers.set(TRACE_HEADER, finalTraceId))
                .build();
        return chain.filter(exchange.mutate().request(tracedRequest).build())
                .doFinally(signalType -> {
                    int status = exchange.getResponse().getStatusCode() == null
                            ? 0
                            : exchange.getResponse().getStatusCode().value();
                    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                    logger.info(
                            "PREDICTION_TRACE gateway_completed traceId={} routeId={} status={} elapsedMs={} signal={}",
                            finalTraceId,
                            routeId,
                            status,
                            elapsedMs,
                            signalType);
                });
    }

    @Override
    public int getOrder() {
        // Run before authentication so traces record both authentication failures and forwarded requests.
        return -300;
    }
}
