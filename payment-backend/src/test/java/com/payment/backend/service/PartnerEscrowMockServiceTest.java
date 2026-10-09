package com.payment.backend.service;

import com.payment.backend.entity.PartnerEscrowMock;
import com.payment.backend.repository.PartnerEscrowMockRepository;
import com.payment.backend.repository.PartnerStatementMockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"}, showSql = false)
@ContextConfiguration(classes = PartnerEscrowMockServiceTest.Config.class)
class PartnerEscrowMockServiceTest {
    @Configuration
    @EntityScan(basePackageClasses = PartnerEscrowMock.class)
    @EnableJpaRepositories(basePackageClasses = PartnerEscrowMockRepository.class)
    @Import(PartnerEscrowMockService.class)
    static class Config {}
    @Autowired PartnerEscrowMockService service;
    @Autowired PartnerStatementMockRepository statements;

    private PartnerEscrowMockService.OpenRequest request(UUID milestoneId) {
        return new PartnerEscrowMockService.OpenRequest(milestoneId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), "fund-" + milestoneId);
    }

    @Test void releaseChargesThreePercentOnceAndRemovesFullEscrowLiability() {
        ReflectionTestUtils.setField(service, "mockRate", new BigDecimal("25000"));
        UUID id = UUID.randomUUID();
        var input = request(id);
        service.open(input);
        service.confirmFunding(id);
        assertThat(service.statement().balanceUsd()).isEqualByComparingTo("100.00");
        var release = new PartnerEscrowMockService.ReleaseRequest("release-" + id, "ACB", "1234567890");
        var result = service.release(id, release, false);
        assertThat(result.feeUsd()).isEqualByComparingTo("3.00");
        assertThat(result.freelancerUsd()).isEqualByComparingTo("97.00");
        assertThat(result.payoutVnd()).isEqualByComparingTo("2425000");
        assertThat(result.rateLockedAt()).isNotNull();
        assertThat(service.release(id, release, false).payoutVnd()).isEqualByComparingTo("2425000");
        assertThat(service.statement().balanceUsd()).isEqualByComparingTo("0.00");
        assertThat(statements.count()).isEqualTo(2);
        assertThatThrownBy(() -> service.refund(id, new PartnerEscrowMockService.RefundRequest("refund-" + id), false))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void frozenDisputeCanRefundOnlyByAdminWithNoFee() {
        UUID id = UUID.randomUUID();
        service.open(request(id));
        service.confirmFunding(id);
        service.freeze(id);
        var refund = new PartnerEscrowMockService.RefundRequest("refund-" + id);
        assertThatThrownBy(() -> service.refund(id, refund, false)).isInstanceOf(RuntimeException.class);
        var result = service.refund(id, refund, true);
        assertThat(result.status()).isEqualTo("REFUNDED");
        assertThat(result.feeUsd()).isEqualByComparingTo("0.00");
        assertThat(service.refund(id, refund, true).status()).isEqualTo("REFUNDED");
        assertThat(service.statement().balanceUsd()).isEqualByComparingTo("0.00");
        assertThat(statements.count()).isEqualTo(2);
    }
}
