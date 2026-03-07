package com.eolma.auction.adapter.in.websocket;

import com.eolma.auction.adapter.in.websocket.dto.BidMessage;
import com.eolma.auction.adapter.in.websocket.dto.BidResultMessage;
import com.eolma.auction.application.usecase.CloseAuctionUseCase;
import com.eolma.auction.application.usecase.PlaceBidUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriTemplate;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@Component
public class BidWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BidWebSocketHandler.class);
    private static final UriTemplate AUCTION_URI_TEMPLATE = new UriTemplate("/ws/auction/{id}");

    private final PlaceBidUseCase placeBidUseCase;
    private final CloseAuctionUseCase closeAuctionUseCase;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public BidWebSocketHandler(PlaceBidUseCase placeBidUseCase,
                                CloseAuctionUseCase closeAuctionUseCase,
                                WebSocketSessionManager sessionManager,
                                ObjectMapper objectMapper) {
        this.placeBidUseCase = placeBidUseCase;
        this.closeAuctionUseCase = closeAuctionUseCase;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Long auctionId = extractAuctionId(session);
        Long userId = extractUserId(session);

        if (auctionId == null) {
            log.warn("Invalid WebSocket connection: missing auctionId");
            return session.close();
        }

        sessionManager.subscribe(auctionId, session, userId);

        return session.receive()
                .doOnNext(message -> {
                    String payload = message.getPayloadAsText();
                    handleMessage(session, auctionId, userId, payload);
                })
                .doFinally(signal -> sessionManager.unsubscribe(auctionId, session))
                .then();
    }

    private void handleMessage(WebSocketSession session, Long auctionId, Long userId, String payload) {
        try {
            BidMessage bidMessage = objectMapper.readValue(payload, BidMessage.class);

            if (!"BID".equals(bidMessage.type())) {
                log.debug("Ignoring non-BID message: type={}", bidMessage.type());
                return;
            }

            if (userId == null) {
                sessionManager.sendToSession(session,
                        BidResultMessage.failure("UNAUTHORIZED", "로그인이 필요합니다."));
                return;
            }

            placeBidUseCase.execute(auctionId, userId, bidMessage.amount())
                    .subscribe(
                            result -> {
                                if (result.accepted()) {
                                    sessionManager.sendToSession(session,
                                            BidResultMessage.success(result.currentPrice(), result.bidCount(), result.nextMinBid()));
                                    if (result.instantBuy()) {
                                        closeAuctionUseCase.execute(auctionId).subscribe();
                                    }
                                } else {
                                    sessionManager.sendToSession(session,
                                            BidResultMessage.failure(result.errorCode(), result.errorMessage()));
                                }
                            },
                            error -> {
                                log.error("Bid processing error: auctionId={}, userId={}", auctionId, userId, error);
                                sessionManager.sendToSession(session,
                                        BidResultMessage.failure("INTERNAL_ERROR", "예기치 않은 오류가 발생했습니다."));
                            }
                    );
        } catch (Exception e) {
            log.error("Failed to parse WebSocket message: {}", payload, e);
            sessionManager.sendToSession(session,
                    Map.of("type", "ERROR", "code", "INVALID_MESSAGE", "message", "잘못된 메시지 형식입니다."));
        }
    }

    private Long extractAuctionId(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        Map<String, String> variables = AUCTION_URI_TEMPLATE.match(uri.getPath());
        String id = variables.get("id");
        if (id == null) return null;
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long extractUserId(WebSocketSession session) {
        // Gateway에서 JWT 검증 후 X-User-Id 헤더로 전달
        String userIdHeader = session.getHandshakeInfo().getHeaders().getFirst("X-User-Id");
        if (userIdHeader == null) {
            // 쿼리 파라미터 fallback (테스트용)
            String query = session.getHandshakeInfo().getUri().getQuery();
            if (query != null && query.contains("userId=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("userId=")) {
                        try {
                            return Long.parseLong(param.substring(7));
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }
                }
            }
            return null;
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
