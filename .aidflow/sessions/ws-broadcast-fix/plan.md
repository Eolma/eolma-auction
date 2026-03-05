# WebSocket broadcast remainingSeconds 버그 수정 - QA #1

## 문제 요약

### #1 [CRITICAL] 입찰 후 경매가 즉시 "종료" 표시

**현상**: 입찰 성공 후 프론트엔드에서 타이머가 "종료"로 표시되고 "경매가 종료되었습니다" 문구 노출. 새로고침하면 정상 복구.
**근본 원인**: `WebSocketSessionManager.broadcastAuctionUpdate()` (line 56)에서 `remainingSeconds`를 **하드코딩된 0**으로 전송:
```java
AuctionUpdateMessage message = AuctionUpdateMessage.update(currentPrice, bidCount, 0);
```
프론트엔드는 AUCTION_UPDATE 메시지의 remainingSeconds를 그대로 사용하므로 경매가 종료된 것으로 판단.

## 구현 계획

### 수정 파일
- `src/main/java/com/eolma/auction/adapter/in/websocket/WebSocketSessionManager.java`
- `src/main/java/com/eolma/auction/application/usecase/PlaceBidUseCase.java` (broadcastAuctionUpdate 호출부)

### 변경 내용
- [x] `broadcastAuctionUpdate` 메서드 시그니처에 `LocalDateTime endAt` 파라미터 추가
- [x] endAt에서 현재 시각을 빼서 remainingSeconds 계산: `Duration.between(LocalDateTime.now(), endAt).getSeconds()`
- [x] `PlaceBidUseCase`에서 `broadcastAuctionUpdate` 호출 시 auction의 endAt 전달
- [x] 혹시 다른 곳에서 `broadcastAuctionUpdate`를 호출하는 곳이 있으면 함께 수정

### 검증
- [ ] 입찰 후 WebSocket AUCTION_UPDATE 메시지에 remainingSeconds > 0 확인
- [ ] 프론트엔드에서 입찰 후 타이머가 정상 유지되는지 확인
