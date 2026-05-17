package com.homegpt.api.repository;

import com.homegpt.api.model.entity.Project;
import com.homegpt.api.model.entity.User;
import com.homegpt.api.model.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Page<Project> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    Page<Project> findByUserAndStatusOrderByCreatedAtDesc(User user, ProjectStatus status, Pageable pageable);
    Optional<Project> findByIdAndUser(UUID id, User user);
}
