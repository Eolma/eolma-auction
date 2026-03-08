package com.eolma.auction.adapter.in.websocket;

import com.eolma.auction.adapter.in.websocket.dto.AuctionUpdateMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);
    private final ObjectMapper objectMapper;

    // auctionId -> Set<WebSocketSession>
    private final Map<Long, Set<WebSocketSession>> auctionSessions = new ConcurrentHashMap<>();

    // sessionId -> userId
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    public WebSocketSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void subscribe(Long auctionId, WebSocketSession session, String userId) {
        auctionSessions.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet())
                .add(session);
        sessionUserMap.put(session.getId(), userId);
        log.info("WebSocket subscribed: auctionId={}, sessionId={}, userId={}", auctionId, session.getId(), userId);
    }

    public void unsubscribe(Long auctionId, WebSocketSession session) {
        Set<WebSocketSession> sessions = auctionSessions.get(auctionId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                auctionSessions.remove(auctionId);
            }
        }
        sessionUserMap.remove(session.getId());
        log.debug("WebSocket unsubscribed: auctionId={}, sessionId={}", auctionId, session.getId());
    }

    public String getUserId(WebSocketSession session) {
        return sessionUserMap.get(session.getId());
    }

    public void broadcastAuctionUpdate(Long auctionId, Long currentPrice, int bidCount,
                                        LocalDateTime endAt, String bidderNickname) {
        long remainingSeconds = Math.max(0, Duration.between(LocalDateTime.now(), endAt).getSeconds());
        int viewerCount = getSubscriberCount(auctionId);
        AuctionUpdateMessage message = AuctionUpdateMessage.update(currentPrice, bidCount, remainingSeconds,
                viewerCount, bidderNickname);
        broadcast(auctionId, message);
    }

    public void broadcastAuctionClosed(Long auctionId, String winnerId, Long winningPrice, String status) {
        AuctionUpdateMessage message = AuctionUpdateMessage.closed(winnerId, winningPrice, status);
        broadcast(auctionId, message);
    }

    public void broadcastInstantBuyStarted(Long auctionId, String buyerId, java.time.LocalDateTime expiresAt) {
        AuctionUpdateMessage message = AuctionUpdateMessage.instantBuyStarted(buyerId, expiresAt.toString());
        broadcast(auctionId, message);
    }

    public void broadcastInstantBuyCancelled(Long auctionId) {
        AuctionUpdateMessage message = AuctionUpdateMessage.instantBuyCancelled();
        broadcast(auctionId, message);
    }

    public void sendToSession(WebSocketSession session, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.send(Mono.just(session.textMessage(json))).subscribe();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WebSocket message", e);
        }
    }

    private void broadcast(Long auctionId, Object message) {
        Set<WebSocketSession> sessions = auctionSessions.get(auctionId);
        if (sessions == null || sessions.isEmpty()) return;

        try {
            String json = objectMapper.writeValueAsString(message);
            sessions.forEach(session -> {
                if (session.isOpen()) {
                    session.send(Mono.just(session.textMessage(json))).subscribe();
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to broadcast WebSocket message: auctionId={}", auctionId, e);
        }
    }

    public int getSubscriberCount(Long auctionId) {
        Set<WebSocketSession> sessions = auctionSessions.get(auctionId);
        return sessions != null ? sessions.size() : 0;
    }
}
