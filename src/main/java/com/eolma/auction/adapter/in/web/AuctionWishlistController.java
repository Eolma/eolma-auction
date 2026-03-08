package com.eolma.auction.adapter.in.web;

import com.eolma.auction.adapter.in.web.dto.AuctionListResponse;
import com.eolma.auction.application.usecase.GetWishlistUseCase;
import com.eolma.auction.application.usecase.ToggleWishlistUseCase;
import com.eolma.common.dto.PageResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionWishlistController {

    private final ToggleWishlistUseCase toggleWishlistUseCase;
    private final GetWishlistUseCase getWishlistUseCase;

    public AuctionWishlistController(ToggleWishlistUseCase toggleWishlistUseCase,
                                      GetWishlistUseCase getWishlistUseCase) {
        this.toggleWishlistUseCase = toggleWishlistUseCase;
        this.getWishlistUseCase = getWishlistUseCase;
    }

    @PostMapping("/{id}/wishlist")
    public Mono<Map<String, Boolean>> toggleWishlist(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {
        return toggleWishlistUseCase.execute(id, userId)
                .map(wishlisted -> Map.of("wishlisted", wishlisted));
    }

    @GetMapping("/{id}/wishlist")
    public Mono<Map<String, Boolean>> isWishlisted(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {
        return getWishlistUseCase.isWishlisted(id, userId)
                .map(wishlisted -> Map.of("wishlisted", wishlisted));
    }

    @GetMapping("/wishlist/me")
    public Mono<PageResponse<AuctionListResponse>> getMyWishlist(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getWishlistUseCase.getMyWishlist(userId, page, size);
    }
}
