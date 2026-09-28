package com.marketplace.backend.service;

import com.marketplace.backend.entity.FreelancerPayoutRecord;

public interface OnChainOffRampService {
    void advance(FreelancerPayoutRecord payoutRecord);
}
