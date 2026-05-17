package com.homegpt.api.repository;

import com.homegpt.api.model.entity.Generation;
import com.homegpt.api.model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GenerationRepository extends JpaRepository<Generation, UUID> {
    List<Generation> findByProjectOrderByCreatedAtDesc(Project project);
    Optional<Generation> findTopByProjectOrderByCreatedAtDesc(Project project);
}
