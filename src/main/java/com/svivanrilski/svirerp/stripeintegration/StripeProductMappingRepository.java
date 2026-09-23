package com.svivanrilski.svirerp.stripeintegration;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StripeProductMappingRepository extends JpaRepository<StripeProductMapping, UUID> {

    @EntityGraph(attributePaths = {"fund", "categoryAccount"})
    List<StripeProductMapping> findAll();

    @EntityGraph(attributePaths = {"fund", "categoryAccount"})
    Optional<StripeProductMapping> findByStripePriceId(String stripePriceId);
}
