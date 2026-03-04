# eolma-auction Development Guide

## 서비스 개요

실시간 경매 엔진. 고동시성 입찰 처리를 위해 Spring WebFlux(Netty) + R2DBC를 사용한다.

- 포트: 8083
- 프레임워크: Spring WebFlux (Reactive)
- DB: PostgreSQL (`eolma_auction`), R2DBC
- 캐시/락: Redis + Redisson (분산 락, 경매 상태 캐싱)
- 실시간: WebSocket

## 핵심 도메인

### Auction 엔티티 (R2DBC)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| productId | Long | 상품 ID |
| sellerId | Long | 판매자 ID |
| title | String | 상품 제목 |
| startingPrice | Long | 시작가 |
| instantPrice | Long | 즉시 구매가 (nullable) |
| reservePrice | Long | 최저 낙찰가 (nullable) |
| minBidUnit | Long | 최소 입찰 단위 |
| currentPrice | Long | 현재 최고가 |
| bidCount | Integer | 입찰 횟수 |
| endType | String | TIME / BID_COUNT |
| maxBidCount | Integer | 최대 입찰 횟수 (BID_COUNT 방식) |
| status | String | ACTIVE / COMPLETED / FAILED |
| endAt | LocalDateTime | 경매 종료 시각 |
| winnerId | Long | 낙찰자 ID (nullable) |
| winningPrice | Long | 낙찰가 (nullable) |

### Bid 엔티티

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| auctionId | Long | 경매 ID |
| bidderId | Long | 입찰자 ID |
| amount | Long | 입찰 금액 |
| bidType | String | MANUAL / INSTANT |
| status | String | ACCEPTED / REJECTED |

### BidResult (Record)

입찰 결과를 나타내는 불변 객체: success, currentPrice, nextMinBid, error 정보 포함.

## WebSocket 입찰 흐름

```
클라이언트 → Gateway(ws://localhost:8080/ws/auction/{id})
         → BidWebSocketHandler
         → PlaceBidUseCase (분산 락)
         → WebSocketSessionManager (브로드캐스트)
         → 모든 구독자에게 경매 상태 업데이트
```

### WebSocket 메시지 타입

**수신 (클라이언트 -> 서버):**
```json
{ "type": "BID", "amount": 55000 }
```

**송신 (서버 -> 클라이언트):**
- `BID_RESULT`: 입찰 결과 (성공/실패, 현재가, 다음 최소 입찰액)
- `AUCTION_UPDATE`: 경매 상태 업데이트 (현재가, 입찰수, 남은 시간)
- `AUCTION_CLOSED`: 경매 종료 (낙찰자, 낙찰가)
- `ERROR`: 에러 메시지

### 사용자 식별
- WebSocket 연결 시 `X-User-Id` 헤더에서 사용자 ID 추출
- Gateway의 JwtAuthFilter가 JWT 검증 후 헤더를 주입

## UseCase 목록

| UseCase | 설명 |
|---------|------|
| CreateAuctionUseCase | PRODUCT_ACTIVATED 수신 -> 경매 생성, Redis 캐시 초기화, 종료 스케줄 등록, AUCTION_STARTED 발행 |
| PlaceBidUseCase | 입찰 처리 (분산 락), 검증, 저장, 캐시 업데이트, WebSocket 브로드캐스트, BID_PLACED 발행 |
| CloseAuctionUseCase | 경매 종료, 낙찰자 결정, AUCTION_COMPLETED/FAILED 발행, WebSocket 종료 알림 |
| GetAuctionUseCase | 경매 조회 |

## 분산 락 (Redisson)

PlaceBidUseCase에서 입찰 시 경매별 분산 락을 사용:
- Lock key: `auction:{auctionId}:lock`
- Wait time: 3초
- Lease time: 5초

동시에 여러 입찰이 들어와도 순차 처리를 보장한다.

## 경매 종료 스케줄러

`AuctionEndScheduler`: 1초마다 실행
- Redis에서 만료된 경매 ID를 조회
- `CloseAuctionUseCase`를 호출하여 경매 종료

종료 판정:
- TIME 방식: endAt 시각 경과
- BID_COUNT 방식: bidCount >= maxBidCount
- 낙찰 조건: bidCount > 0 && (reservePrice == null || currentPrice >= reservePrice)

## Kafka 이벤트

**발행 (`eolma.auction.events`):**
- `AUCTION_STARTED`: 경매 생성 시
- `AUCTION_COMPLETED`: 낙찰 시 (winnerId, winningPrice 포함)
- `AUCTION_FAILED`: 유찰 시
- `BID_PLACED`: 입찰 시

**수신 (`eolma.product.events`):**
- `PRODUCT_ACTIVATED` -> 경매 생성 트리거

## R2DBC 주의사항

- JPA 사용 불가 (WebFlux + R2DBC)
- `@Table`, `@Id` 등 Spring Data R2DBC 어노테이션 사용
- 엔티티에 `@Entity` 대신 `@Table` 사용
- Repository: `ReactiveCrudRepository` 상속
- 스키마 자동 생성 없음 -> `schema.sql`로 관리
- 모든 DB 작업은 `Mono`/`Flux` 반환

## Redis 캐시 구조

- `auction:{id}:state` - 경매 상태 (currentPrice, bidCount, status)
- `auction:{id}:lock` - 분산 락
- 경매 종료 시 만료 시각 기반 Sorted Set으로 관리
