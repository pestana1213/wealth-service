package com.finance.wealth_service.bucket.repository;

import com.finance.wealth_service.bucket.entity.Bucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface BucketRepository extends JpaRepository<Bucket, UUID> {

    /** Active buckets only: an archived bucket stays out of the dashboard and the listings. */
    List<Bucket> findByUserIdAndArchivedFalseOrderByName(UUID userId);

    /** Locks the bucket if it belongs to the caller and is active; fails at once if another movement holds it. */
    @Query(value = """
            SELECT b.* FROM buckets b
            JOIN users u ON u.id = b.user_id
            WHERE b.id = :bucketId AND b.archived = false AND u.subject = :subject AND u.issuer = :issuer
            FOR UPDATE OF b NOWAIT""",
            nativeQuery = true)
    Optional<Bucket> findOwnedByForUpdate(@Param("bucketId") UUID bucketId,
                                          @Param("subject") String subject,
                                          @Param("issuer") String issuer);

    /** The bucket, if it belongs to the caller; archived ones included. */
    @Query(value = """
            SELECT b.* FROM buckets b
            JOIN users u ON u.id = b.user_id
            WHERE b.id = :bucketId AND u.subject = :subject AND u.issuer = :issuer""",
            nativeQuery = true)
    Optional<Bucket> findOwnedBy(@Param("bucketId") UUID bucketId,
                                 @Param("subject") String subject,
                                 @Param("issuer") String issuer);

    @Query(value = "SELECT * FROM buckets WHERE id IN :id", nativeQuery = true)
    List<Bucket> findAllByIds(@Param("id") List<UUID> ids);
}
