package com.roomix.api.repository;

import com.roomix.api.model.entity.TokenTransaction;
import com.roomix.api.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TokenTransactionRepository extends JpaRepository<TokenTransaction, UUID> {
    List<TokenTransaction>     findByUserOrderByCreatedAtDesc(User user);
    Optional<TokenTransaction> findByReference(String reference);
}
