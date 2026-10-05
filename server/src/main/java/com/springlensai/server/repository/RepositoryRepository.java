package com.springlensai.server.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springlensai.server.entity.IndexStatus;
import com.springlensai.server.entity.Repository;

public interface RepositoryRepository extends JpaRepository<Repository, UUID> {

    List<Repository> findByUserIdOrderByFullNameAsc(UUID userId);

    Optional<Repository> findByIdAndUserId(UUID id, UUID userId);

    Optional<Repository> findByUserIdAndGithubRepoId(UUID userId, Long githubRepoId);

    /** Used on startup to recover jobs that were running when the server stopped. */
    List<Repository> findByIndexStatus(IndexStatus indexStatus);

    boolean existsByUserIdAndIndexStatus(UUID userId, IndexStatus indexStatus);

    /** Deletes all repository rows of the user (used by account deletion). */
    @Modifying
    @Query("delete from Repository r where r.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
