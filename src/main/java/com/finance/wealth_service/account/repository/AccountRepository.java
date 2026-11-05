package com.finance.wealth_service.account.repository;

import com.finance.wealth_service.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdOrderByName(UUID userId);

    @Query(value = """
          SELECT a.* FROM accounts a
          JOIN users u ON u.id = a.user_id
          WHERE a.id = :accountId AND u.subject = :subject AND u.issuer = :issuer""",
            nativeQuery = true)
    Optional<Account> findOwnedBy(@Param("accountId") UUID accountId,
                                  @Param("subject") String subject,
                                  @Param("issuer") String issuer);

    @Query(value = "SELECT * FROM accounts WHERE id = :id AND user_id = :userId FOR UPDATE NOWAIT", nativeQuery = true)
    Optional<Account> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query(value = "SELECT * FROM accounts WHERE id IN :id", nativeQuery = true)
    List<Account> findAllByIds(@Param("id") List<UUID> ids);
}
