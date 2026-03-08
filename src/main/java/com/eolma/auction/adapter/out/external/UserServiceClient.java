package com.eolma.auction.adapter.out.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
    private final WebClient webClient;

    public UserServiceClient(@Value("${services.user.url:http://localhost:8081}") String userServiceUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }

    public Mono<String> getNickname(String userId) {
        return webClient.get()
                .uri("/api/v1/members/{id}", userId)
                .retrieve()
                .bodyToMono(MemberResponse.class)
                .map(r -> r.nickname() != null ? r.nickname() : "익명")
                .onErrorResume(e -> {
                    log.warn("Failed to fetch nickname for userId={}: {}", userId, e.getMessage());
                    return Mono.just("익명");
                });
    }

    private record MemberResponse(String id, String nickname, String profileImageUrl) {}
}
