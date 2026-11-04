package com.vistay.warehouse.domain.repository;

import com.vistay.warehouse.domain.model.DimChannel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DimChannelRepository extends JpaRepository<DimChannel, Short> {

    Optional<DimChannel> findByChannelCode(String channelCode);
}
