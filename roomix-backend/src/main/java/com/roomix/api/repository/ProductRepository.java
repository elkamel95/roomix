package com.roomix.api.repository;

import com.roomix.api.model.entity.Generation;
import com.roomix.api.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByGeneration(Generation generation);
}
