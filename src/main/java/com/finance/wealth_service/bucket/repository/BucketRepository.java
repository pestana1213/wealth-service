package com.finance.wealth_service.bucket.repository;

import com.finance.wealth_service.bucket.entity.Bucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface BucketRepository extends JpaRepository<Bucket, UUID> {

    List<Bucket> findByUserIdOrderByName(UUID userId);

    @Query(value = "SELECT * FROM buckets WHERE id = :id AND user_id = :userId FOR UPDATE NOWAIT", nativeQuery = true)
    Optional<Bucket> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query(value = "SELECT * FROM buckets WHERE id IN :id", nativeQuery = true)
    List<Bucket> findAllByIds(@Param("id") List<UUID> ids);
}
