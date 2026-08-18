package com.example.incidentstream.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
public class WebFluxConfig {

    @org.springframework.context.annotation.Bean
    public WebFilter sseHeadersFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            ServerHttpResponse response = exchange.getResponse();

            String path = exchange.getRequest().getPath().value();

            if (path.startsWith("/api/v1/incident/stream")) {
                response.getHeaders().set("X-Accel-Buffering", "no");
                response.getHeaders().set("Cache-Control", "no-cache");
                response.getHeaders().set("Connection", "keep-alive");
            }

            return chain.filter(exchange);
        };
    }
}
