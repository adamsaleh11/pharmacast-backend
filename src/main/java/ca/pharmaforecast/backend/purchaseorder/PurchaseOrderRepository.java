package ca.pharmaforecast.backend.purchaseorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByIdAndLocationId(UUID id, UUID locationId);

    List<PurchaseOrder> findByLocationIdOrderByGeneratedAtDesc(UUID locationId);
}
