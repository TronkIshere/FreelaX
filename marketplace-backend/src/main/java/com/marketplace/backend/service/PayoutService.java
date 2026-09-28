package com.marketplace.backend.service;

import com.marketplace.backend.entity.Job;

import java.util.List;
import java.util.UUID;

public interface PayoutService {

    void settle(Job job);

    void reconcile(UUID payoutRecordId);

    List<UUID> findRecordIdsToReconcile();
}