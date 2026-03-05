CREATE TABLE IF NOT EXISTS auction (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      BIGINT NOT NULL UNIQUE,
    seller_id       BIGINT NOT NULL,
    title           VARCHAR(200) NOT NULL,
    starting_price  BIGINT NOT NULL,
    instant_price   BIGINT,
    reserve_price   BIGINT,
    min_bid_unit    BIGINT NOT NULL DEFAULT 1000,
    current_price   BIGINT NOT NULL,
    bid_count       INT NOT NULL DEFAULT 0,
    end_type        VARCHAR(20) NOT NULL,
    max_bid_count   INT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    end_at          TIMESTAMPTZ NOT NULL,
    winner_id       BIGINT,
    winning_price   BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_auction_product ON auction(product_id);
CREATE INDEX IF NOT EXISTS idx_auction_status ON auction(status);
CREATE INDEX IF NOT EXISTS idx_auction_status_end ON auction(status, end_at);

CREATE TABLE IF NOT EXISTS bid (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auction_id  BIGINT NOT NULL,
    bidder_id   BIGINT NOT NULL,
    amount      BIGINT NOT NULL,
    bid_type    VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status      VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bid_auction ON bid(auction_id);
CREATE INDEX IF NOT EXISTS idx_bid_auction_amount ON bid(auction_id, amount DESC);
CREATE INDEX IF NOT EXISTS idx_bid_bidder ON bid(bidder_id);

CREATE TABLE IF NOT EXISTS auction_wishlist (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auction_id  BIGINT NOT NULL REFERENCES auction(id),
    user_id     BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(auction_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_wishlist_user ON auction_wishlist(user_id);
CREATE INDEX IF NOT EXISTS idx_wishlist_auction ON auction_wishlist(auction_id);

CREATE TABLE IF NOT EXISTS processed_event (
    event_id     VARCHAR(36) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
