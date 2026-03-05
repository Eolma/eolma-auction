# ux-backend-support - Report

## Summary
AUCTION_UPDATE WebSocket 메시지에 viewerCount/bidderNickname 필드를 추가하고, 경매 찜하기(Wishlist) CRUD REST API를 신규 구현하며, 경매 목록 API에 bidCount 정렬 파라미터를 추가했다.

## Plan Completion

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
- [x] 3-1: AuctionWishlist 엔티티 생성
- [x] 3-2: AuctionWishlistPort 아웃 포트 정의
- [x] 3-3: schema.sql에 auction_wishlist 테이블 DDL 추가

### Phase 4: Wishlist 유스케이스
- [x] 4-1: ToggleWishlistUseCase 구현
- [x] 4-2: GetWishlistUseCase 구현

### Phase 5: Wishlist 어댑터
- [x] 5-1: AuctionWishlistRepository (R2DBC) 생성
- [x] 5-2: AuctionWishlistR2dbcAdapter 구현
- [x] 5-3: AuctionWishlistController REST API 구현

### Phase 6: 검증
- [x] 6-1: ./gradlew build 성공 확인

### Acceptance Criteria
- [x] AC-1 ~ AC-7: 모두 충족

## Changed Files

| File | Change Type | Description |
|------|-------------|-------------|
| `adapter/in/websocket/dto/AuctionUpdateMessage.java` | modified | viewerCount, bidderNickname 필드 추가 |
| `adapter/in/websocket/WebSocketSessionManager.java` | modified | broadcastAuctionUpdate 시그니처에 bidderNickname 추가, viewerCount 자동 계산 |
| `adapter/out/external/UserServiceClient.java` | new | eolma-user 공개 프로필 API 호출 WebClient, 실패 시 "익명" 폴백 |
| `application/usecase/PlaceBidUseCase.java` | modified | UserServiceClient 의존성 추가, 입찰 성공 시 닉네임 조회 후 broadcast 전달 |
| `adapter/in/web/AuctionController.java` | modified | sort 파라미터 추가, getAuction에 optional X-User-Id 헤더 추가 |
| `domain/repository/AuctionRepository.java` | modified | findAllAuctionsByBidCount 쿼리 추가 |
| `application/usecase/GetAuctionUseCase.java` | modified | sort 분기, wishlistCount/isWishlisted 지원, concatMap으로 순서 보장 |
| `adapter/in/web/dto/AuctionResponse.java` | modified | isWishlisted(Boolean) 필드 추가 |
| `adapter/in/web/dto/AuctionListResponse.java` | modified | wishlistCount(Long) 필드 추가 |
| `domain/model/AuctionWishlist.java` | new | Wishlist 도메인 엔티티 |
| `application/port/out/AuctionWishlistPort.java` | new | Wishlist 아웃 포트 인터페이스 |
| `application/usecase/ToggleWishlistUseCase.java` | new | 찜 토글 유스케이스 (존재하면 삭제, 없으면 생성) |
| `application/usecase/GetWishlistUseCase.java` | new | 찜 여부 확인, 내 찜 목록 조회 유스케이스 |
| `adapter/out/persistence/AuctionWishlistRepository.java` | new | R2DBC ReactiveCrudRepository |
| `adapter/out/persistence/AuctionWishlistR2dbcAdapter.java` | new | AuctionWishlistPort 구현체 |
| `adapter/in/web/AuctionWishlistController.java` | new | POST toggle, GET isWishlisted, GET /wishlist/me 엔드포인트 |
| `resources/schema.sql` | modified | auction_wishlist 테이블 DDL + 인덱스 추가 |

## Key Decisions
- **flatMap -> concatMap**: 목록 조회에서 wishlistCount를 비동기로 조회할 때, flatMap 대신 concatMap을 사용하여 정렬 순서를 보장
- **UserServiceClient 폴백**: 닉네임 조회 실패 시 "익명"을 반환하여 입찰 흐름이 중단되지 않도록 처리
- **AuctionResponse.isWishlisted**: Boolean(nullable)으로 정의하여 비로그인 시 null 반환
- **schema.sql 직접 관리**: 프로젝트 기존 패턴(Flyway 미사용)을 따라 schema.sql에 DDL 추가

## Issues & Observations
- NFR-3(Redis 캐시 활용)은 Out of Scope 노트에 따라 초기 구현에서 제외. wishlistCount는 매 요청마다 DB 조회
- 닉네임 캐싱도 Out of Scope로 매 입찰마다 user 서비스 호출. 트래픽 증가 시 Redis 캐시 도입 필요
- 스모크 테스트(6-2)는 실행 환경(DB/Redis/Kafka) 의존으로 생략

## Duration
- Started: 2026-03-05T09:59:05Z
- Completed: 2026-03-05T12:25:00Z (approx)
- Commits: 미커밋 (커밋 대기 중)
