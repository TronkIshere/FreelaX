package com.marketplace.backend.repository;

import com.marketplace.backend.entity.PortfolioItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, UUID> {
    List<PortfolioItem> findByUserIdOrderBySortOrderAscCreatedAtAsc(UUID userId);
    long countByUserId(UUID userId);
    Optional<PortfolioItem> findByIdAndUserId(UUID id, UUID userId);
}
