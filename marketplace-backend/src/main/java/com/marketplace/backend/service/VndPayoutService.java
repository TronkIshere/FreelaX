package com.marketplace.backend.service;

import com.marketplace.backend.entity.FreelancerPayoutRecord;

public interface VndPayoutService {
    void advance(FreelancerPayoutRecord payoutRecord);
}
