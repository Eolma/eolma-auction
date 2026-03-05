# UX 백엔드 지원: ViewerCount + Wishlist + Sort + BidderNickname

## Background

프론트엔드 UX 개선 세션에서 여러 기능이 백엔드 작업을 필요로 한다. (1) 실시간 경매 참여자 수 표시를 위해 AUCTION_UPDATE 메시지에 viewerCount 필드 추가 (2) 경매 찜하기 기능을 위한 Wishlist REST API 신규 생성 (3) 히어로 캐러셀용 bidCount 정렬 지원 (4) 실시간 입찰 피드에 입찰자 닉네임 포함. WebSocketSessionManager에 이미 `getSubscriberCount()` 메서드가 존재하므로 viewerCount는 최소한의 변경으로 가능하다.

## Objective

AUCTION_UPDATE WebSocket 메시지에 viewerCount/bidderNickname 필드를 추가하고, 경매 찜하기 CRUD REST API를 구현하며, 경매 목록 API에 정렬 파라미터를 추가한다.

## Requirements

### Functional Requirements
- FR-1: AUCTION_UPDATE 메시지에 `viewerCount` 필드 포함 (해당 경매의 현재 WebSocket 구독자 수)
- FR-2: 찜하기 API - `POST /api/v1/auctions/{id}/wishlist` (토글)
- FR-3: 찜 여부 조회 - `GET /api/v1/auctions/{id}/wishlist` (본인 기준)
- FR-4: 내 찜 목록 조회 - `GET /api/v1/auctions/wishlist/me` (페이지네이션)
- FR-5: 경매 상세 응답에 `isWishlisted` 필드 추가 (로그인 시)
- FR-6: 경매 목록 응답에 각 항목의 `wishlistCount` 필드 추가
- FR-7: 경매 목록 API에 `sort` 파라미터 추가 (`bidCount` 정렬 지원, 히어로 캐러셀용)
- FR-8: AUCTION_UPDATE 메시지에 `bidderNickname` 필드 포함 (eolma-user API 호출로 조회)

### Non-Functional Requirements
- NFR-1: 빌드(./gradlew build) 성공
- NFR-2: 기존 WebSocket 메시지 호환성 유지 (필드 추가만, 기존 필드 변경 없음)
- NFR-3: wishlist 조회 성능 - Redis 캐시 활용

## Out of Scope
- 찜 알림 (푸시/이메일)
- 찜 기반 추천
- 찜 수 기반 정렬
- 사용자 닉네임 캐싱 (초기에는 매 요청마다 user 서비스 호출, 향후 Redis 캐시 도입)

## Technical Approach

### ViewerCount (FR-1)

현재 `WebSocketSessionManager.broadcastAuctionUpdate()` 호출 시 viewerCount를 함께 계산하여 메시지에 포함:

1. `AuctionUpdateMessage` record에 `Integer viewerCount` 필드 추가
2. `broadcastAuctionUpdate()` 메서드에서 `getSubscriberCount(auctionId)` 호출
3. `AuctionUpdateMessage.update()` 팩토리에 viewerCount 파라미터 추가

변경 파일:
- `AuctionUpdateMessage.java` - viewerCount, bidderNickname 필드 추가
- `WebSocketSessionManager.java` - broadcastAuctionUpdate에서 subscriberCount 주입

### BidderNickname (FR-8)

입찰 성공 시 AUCTION_UPDATE 브로드캐스트에 입찰자 닉네임을 포함:

1. eolma-user의 공개 프로필 API (`GET /api/v1/members/{id}`) 호출을 위한 WebClient 설정
2. `PlaceBidUseCase`에서 입찰 성공 후 userId로 닉네임 조회
3. `AuctionUpdateMessage`에 `String bidderNickname` 필드 추가
4. `broadcastAuctionUpdate()` 시그니처에 bidderNickname 파라미터 추가

변경 파일:
- `AuctionUpdateMessage.java` - bidderNickname 필드 추가
- `WebSocketSessionManager.java` - broadcastAuctionUpdate 시그니처 확장
- `PlaceBidUseCase.java` - 닉네임 조회 후 broadcast에 전달
- 신규: `UserServiceClient.java` - eolma-user API 호출 WebClient

### Sort 파라미터 (FR-7)

경매 목록 API에 정렬 옵션 추가:

1. `AuctionController.getAuctions()`에 `@RequestParam(required = false) String sort` 추가
2. `AuctionRepository`에 `ORDER BY bid_count DESC` 쿼리 추가
3. `GetAuctionUseCase`에 sort 분기 로직 추가
4. 지원 정렬: `bidCount` (입찰 많은 순), `latest` (기본, 최신순)

변경 파일:
- `AuctionController.java` - sort 파라미터 추가
- `AuctionRepository.java` - bidCount 정렬 쿼리 추가
- `GetAuctionUseCase.java` - sort 분기 처리

### Wishlist API (FR-2~6)

Hexagonal Architecture 패턴을 따라 구현:

**도메인 레이어**:
- `AuctionWishlist` 엔티티: `id`, `auctionId`, `userId`, `createdAt`
- 복합 유니크 제약: `(auctionId, userId)`

**포트**:
- `AuctionWishlistPort` (out): `save`, `delete`, `findByAuctionIdAndUserId`, `findByUserId`, `countByAuctionId`, `existsByAuctionIdAndUserId`

**유스케이스**:
- `ToggleWishlistUseCase`: 존재하면 삭제, 없으면 생성 (토글)
- `GetWishlistUseCase`: 찜 여부 확인, 찜 목록 조회

**어댑터**:
- `AuctionWishlistR2dbcAdapter` (out): R2DBC Repository 기반
- `AuctionWishlistController` (in): REST API 엔드포인트

**테이블**:
```sql
CREATE TABLE auction_wishlist (
    id BIGSERIAL PRIMARY KEY,
    auction_id BIGINT NOT NULL REFERENCES auction(id),
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(auction_id, user_id)
);
CREATE INDEX idx_wishlist_user ON auction_wishlist(user_id);
CREATE INDEX idx_wishlist_auction ON auction_wishlist(auction_id);
```

### Affected Files

**신규 생성**:
- `src/main/java/com/eolma/auction/domain/model/AuctionWishlist.java`
- `src/main/java/com/eolma/auction/application/port/out/AuctionWishlistPort.java`
- `src/main/java/com/eolma/auction/application/usecase/ToggleWishlistUseCase.java`
- `src/main/java/com/eolma/auction/application/usecase/GetWishlistUseCase.java`
- `src/main/java/com/eolma/auction/adapter/in/web/AuctionWishlistController.java`
- `src/main/java/com/eolma/auction/adapter/out/persistence/AuctionWishlistR2dbcAdapter.java`
- `src/main/java/com/eolma/auction/adapter/out/persistence/AuctionWishlistRepository.java`
- `src/main/resources/db/migration/` 또는 `schema.sql`에 테이블 추가

**수정**:
- `src/main/java/com/eolma/auction/adapter/in/ws/dto/AuctionUpdateMessage.java` - viewerCount, bidderNickname 필드 추가
- `src/main/java/com/eolma/auction/adapter/in/ws/WebSocketSessionManager.java` - broadcastAuctionUpdate 시그니처 확장
- `src/main/java/com/eolma/auction/application/usecase/PlaceBidUseCase.java` - 닉네임 조회 후 broadcast 전달
- `src/main/java/com/eolma/auction/adapter/in/web/AuctionController.java` - sort 파라미터 추가
- `src/main/java/com/eolma/auction/adapter/out/persistence/AuctionRepository.java` - bidCount 정렬 쿼리
- `src/main/java/com/eolma/auction/application/usecase/GetAuctionUseCase.java` - sort 분기
- `src/main/java/com/eolma/auction/adapter/in/web/dto/AuctionDetailResponse.java` - isWishlisted 필드 (필요 시)
- `src/main/resources/schema.sql` - auction_wishlist 테이블 DDL 추가

**신규 생성 (추가)**:
- `src/main/java/com/eolma/auction/adapter/out/external/UserServiceClient.java` - eolma-user API WebClient

## Implementation Items

### Phase 1: WebSocket 메시지 확장
- [x] 1-1: AuctionUpdateMessage에 viewerCount, bidderNickname 필드 추가
- [x] 1-2: WebSocketSessionManager.broadcastAuctionUpdate 시그니처 확장
- [x] 1-3: UserServiceClient 생성 (eolma-user 공개 프로필 API WebClient)
- [x] 1-4: PlaceBidUseCase에서 닉네임 조회 후 broadcast에 전달

### Phase 2: Sort 파라미터
- [x] 2-1: AuctionRepository에 bidCount 정렬 쿼리 추가
- [x] 2-2: GetAuctionUseCase에 sort 분기 처리
- [x] 2-3: AuctionController에 sort 파라미터 추가

### Phase 3: Wishlist 도메인 + 포트
- [x] 2-1: AuctionWishlist 엔티티 생성
- [x] 2-2: AuctionWishlistPort 아웃 포트 정의
- [x] 2-3: schema.sql에 auction_wishlist 테이블 DDL 추가

### Phase 4: Wishlist 유스케이스
- [x] 4-1: ToggleWishlistUseCase 구현
- [x] 4-2: GetWishlistUseCase 구현

### Phase 5: Wishlist 어댑터
- [x] 5-1: AuctionWishlistRepository (R2DBC) 생성
- [x] 5-2: AuctionWishlistR2dbcAdapter 구현
- [x] 5-3: AuctionWishlistController REST API 구현

### Phase 6: 검증
- [x] 6-1: ./gradlew build 성공 확인
- [ ] 6-2: 스모크 테스트 (WebSocket viewerCount/bidderNickname, Sort, Wishlist CRUD)

## Acceptance Criteria
- [x] AC-1: AUCTION_UPDATE 메시지에 viewerCount 필드가 포함됨
- [x] AC-2: AUCTION_UPDATE 메시지에 bidderNickname 필드가 포함됨
- [x] AC-3: GET /api/v1/auctions?sort=bidCount 호출 시 입찰 많은 순 정렬
- [x] AC-4: POST /api/v1/auctions/{id}/wishlist 호출 시 찜 토글 동작
- [x] AC-5: GET /api/v1/auctions/wishlist/me 호출 시 내 찜 목록 반환
- [x] AC-6: ./gradlew build 성공
- [x] AC-7: 기존 WebSocket 메시지의 다른 필드가 변경되지 않음

## Notes
- viewerCount는 해당 경매의 WebSocket 연결 수이므로 실제 "조회자 수"와 다를 수 있음 (WebSocket 미연결 사용자는 카운트 안 됨)
- eolma-auction은 WebFlux(reactive) 프로젝트이므로 모든 Repository는 ReactiveCrudRepository 기반
- 찜하기는 인증 필수 (X-User-Id 헤더, Gateway에서 주입)
- 경매 종료 후에도 찜 데이터는 유지 (히스토리용)
- AuctionUpdateMessage가 record 타입이므로 필드 추가 시 기존 factory 메서드도 수정 필요
- bidderNickname 조회는 eolma-user 서비스의 `GET /api/v1/members/{id}` API에 의존 (eolma-user 세션에서 구현)
- UserServiceClient는 WebClient로 구현 (WebFlux 환경), 닉네임 조회 실패 시 "익명" 폴백
- sort 파라미터 기본값은 `latest` (created_at DESC)
