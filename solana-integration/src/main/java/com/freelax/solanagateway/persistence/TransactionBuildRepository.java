package com.freelax.solanagateway.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionBuildRepository extends JpaRepository<TransactionBuild, String> {
}
