package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.DimStaff;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DimStaffRepository extends JpaRepository<DimStaff, Long> {

    Optional<DimStaff> findByAdminUserIdAndCurrentRowTrue(UUID adminUserId);
}
