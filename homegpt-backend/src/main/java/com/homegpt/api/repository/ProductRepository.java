package com.homegpt.api.repository;

import com.homegpt.api.model.entity.Generation;
import com.homegpt.api.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByGeneration(Generation generation);
}
