package com.misa.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.misa.backend.configuration.*;
import com.misa.backend.dto.request.misa.*;
import com.misa.backend.entity.*;
import com.misa.backend.exception.*;
import com.misa.backend.repository.*;
import com.misa.backend.service.*;
import com.misa.backend.service.pdf.CertificatePdfRenderer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"}, showSql = false)
@ContextConfiguration(classes = CertificateRecoveryTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CertificateRecoveryTest {
    @Configuration @EntityScan(basePackageClasses = Taxpayer.class)
    @EnableJpaRepositories(basePackageClasses = TaxpayerRepository.class)
    @Import({WithholdingCertificateServiceImpl.class, TaxpayerServiceImpl.class})
    static class Config {
        @Bean TransactionTemplate tx(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
        @Bean MisaProperties misaProperties() { return new MisaProperties(); }
        @Bean CertificatePdfProperties pdfProperties() { return new CertificatePdfProperties(); }
    }
    @Autowired WithholdingCertificateService service;
    @Autowired TaxpayerService taxpayers;
    @Autowired TaxpayerRepository taxpayerRepo;
    @Autowired PayoutTransactionRepository payouts;
    @Autowired WithholdingCertificateRepository certificates;
    @Autowired IncorrectRecordNotificationRepository notifications;
    @Autowired TransactionTemplate tx;
    @MockitoBean MisaProviderClient provider;
    @MockitoBean TaxEngineService taxEngine;
    @MockitoBean CertificatePdfRenderer pdf;
    PayoutTransaction payout;
    AtomicInteger assignments;

    @BeforeEach void setup() {
        notifications.deleteAll(); certificates.deleteAll(); payouts.deleteAll(); taxpayerRepo.deleteAll();
        assignments = new AtomicInteger();
        when(provider.createCertificate(any())).thenAnswer(a -> {
            int n = assignments.incrementAndGet();
            return new MisaProviderClient.MisaCertificateAssignment("DEMO", "000" + n, "lookup-" + n);
        });
        when(taxEngine.calculateTaxWithheld(any())).thenAnswer(a -> ((BigDecimal) a.getArgument(0)).multiply(new BigDecimal("0.1")));
        payout = tx.execute(status -> {
            Taxpayer t = new Taxpayer(); t.setFullName("Private taxpayer"); t.setIdentityNumber("PRIVATE-ID");
            t.setTaxCode("PRIVATE-TAX"); t.setNationality("VN"); t.setExternalId(UUID.randomUUID().toString()); taxpayerRepo.saveAndFlush(t);
            PayoutTransaction p = new PayoutTransaction(); p.setTaxpayer(t); p.setPlatformPayoutId(UUID.randomUUID().toString());
            p.setAmountUsdc(new BigDecimal("100")); p.setExchangeRate(new BigDecimal("25000"));
            p.setAmountVndGross(new BigDecimal("2500000")); p.setTransactionHash("public-settlement-reference");
            p.setPaymentDate(LocalDate.now()); return payouts.saveAndFlush(p);
        });
    }

    @Test void firstCreatePersistsIdentityAndFingerprintAndSafeLookup() throws Exception {
        var c = service.create(request(payout.getId(), "stable-key"));
        var saved = certificates.findById(c.getId()).orElseThrow();
        assertThat(saved.getIdempotencyKey()).isEqualTo("stable-key"); assertThat(saved.getPayloadHash()).hasSize(64);
        var recovery = service.findByPlatformPayout(payout.getPlatformPayoutId());
        assertThat(recovery.certificateId()).isEqualTo(c.getId()); assertThat(recovery.payoutTransactionId()).isEqualTo(payout.getId());
        assertThat(recovery.status()).isEqualTo("DRAFT"); assertThat(recovery.simulation()).isTrue();
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(recovery);
        assertThat(json).doesNotContain("PRIVATE-ID", "PRIVATE-TAX", "Private taxpayer", "digitalCertificateSerial", "password", "token");
        assertThat(recovery.createdAt()).isNotNull(); assertThat(recovery.taxableIncome()).isEqualByComparingTo("2500000");
    }

    @Test void sameKeySamePayloadReturnsOneCertificate() {
        var first = service.create(request(payout.getId(), "stable-key"));
        assertThat(service.create(request(payout.getId(), "stable-key")).getId()).isEqualTo(first.getId());
        assertThat(certificates.count()).isEqualTo(1); assertThat(assignments.get()).isEqualTo(1);
    }

    @Test void sameKeyChangedImmutablePayloadConflicts() {
        service.create(request(payout.getId(), "stable-key"));
        tx.executeWithoutResult(status -> { var p = payouts.findById(payout.getId()).orElseThrow(); p.setTransactionHash("changed"); payouts.save(p); });
        assertConflict(() -> service.create(request(payout.getId(), "stable-key")), ErrorCode.CERTIFICATE_KEY_CONFLICT);
        assertThat(certificates.count()).isEqualTo(1); assertThat(assignments.get()).isEqualTo(1);
    }

    @Test void differentKeySamePayoutConflictsWithoutDuplicate() {
        service.create(request(payout.getId(), "key-a"));
        assertConflict(() -> service.create(request(payout.getId(), "key-b")), ErrorCode.CERTIFICATE_IDENTITY_CONFLICT);
        assertThat(certificates.count()).isEqualTo(1);
    }

    @Test void sameKeyDifferentPayoutConflicts() {
        service.create(request(payout.getId(), "same-key"));
        UUID second = tx.execute(status -> {
            PayoutTransaction p = new PayoutTransaction(); p.setTaxpayer(taxpayerRepo.findAll().get(0));
            p.setPlatformPayoutId(UUID.randomUUID().toString()); p.setAmountUsdc(payout.getAmountUsdc());
            p.setExchangeRate(payout.getExchangeRate()); p.setAmountVndGross(payout.getAmountVndGross());
            p.setTransactionHash("second"); p.setPaymentDate(LocalDate.now()); return payouts.saveAndFlush(p).getId();
        });
        assertConflict(() -> service.create(request(second, "same-key")), ErrorCode.CERTIFICATE_KEY_CONFLICT);
        assertThat(certificates.count()).isEqualTo(1);
    }

    @Test void concurrentSamePayoutReturnsWinningCertificate() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<UUID> call = () -> { start.await(); return service.create(request(payout.getId(), "concurrent")).getId(); };
            var a = pool.submit(call); var b = pool.submit(call); start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
            assertThat(certificates.count()).isEqualTo(1); assertThat(assignments.get()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void unknownLookupUsesExistingCertificateNotFoundCode() {
        assertConflict(() -> service.findByPlatformPayout(UUID.randomUUID().toString()), ErrorCode.CERTIFICATE_NOT_FOUND);
        assertThat(certificates.count()).isZero(); verifyNoInteractions(provider);
    }

    @Test void legacyRequestDerivesStableKeyAndRepeatsSafely() {
        var first = service.create(request(payout.getId(), null));
        assertThat(first.getIdempotencyKey()).isEqualTo("payout-" + payout.getId());
        assertThat(service.create(request(payout.getId(), null)).getId()).isEqualTo(first.getId());
        assertThat(assignments.get()).isEqualTo(1);
    }

    @Test void historicalNullKeyCertificateRemainsRecoverableAndCompatible() {
        UUID id = tx.execute(status -> {
            WithholdingCertificate c = new WithholdingCertificate(); var p = payouts.findById(payout.getId()).orElseThrow();
            c.setPayoutTransaction(p); c.setTaxpayer(p.getTaxpayer()); c.setSymbol("OLD"); c.setCertificateNumber("OLD1");
            c.setTaxableIncome(p.getAmountVndGross()); c.setTaxWithheld(new BigDecimal("250000"));
            return certificates.saveAndFlush(c).getId();
        });
        assertThat(service.create(request(payout.getId(), null)).getId()).isEqualTo(id);
        assertThat(service.findByPlatformPayout(payout.getPlatformPayoutId()).idempotencyKey()).isEqualTo("payout-" + payout.getId());
        verifyNoInteractions(provider);
    }

    @Test void externalTaxpayerRegistrationRetryKeepsSameTaxCodeOwner() {
        String external = tx.execute(status -> payouts.findById(payout.getId()).orElseThrow().getTaxpayer().getExternalId());
        CreateTaxpayerRequest r = new CreateTaxpayerRequest(); r.setExternalId(external); r.setFullName("Same taxpayer");
        r.setTaxCode("PRIVATE-TAX"); r.setIdentityNumber("PRIVATE-ID"); r.setNationality("VN");
        var first = taxpayers.registerForExternal(r); assertThat(taxpayers.registerForExternal(r).getId()).isEqualTo(first.getId());
        r.setExternalId(UUID.randomUUID().toString());
        assertConflict(() -> taxpayers.registerForExternal(r), ErrorCode.TAX_CODE_ALREADY_EXISTS);
        assertThat(taxpayerRepo.count()).isEqualTo(1);
    }

    @Test void whitespaceOrBlankKeyIsRejectedBeforeProviderCall() {
        assertConflict(() -> service.create(request(payout.getId(), " bad ")), ErrorCode.INVALID_DATA);
        assertConflict(() -> service.create(request(payout.getId(), "")), ErrorCode.INVALID_DATA);
        verifyNoInteractions(provider);
    }

    private CreateWithholdingCertificateRequest request(UUID id, String key) {
        var r = new CreateWithholdingCertificateRequest(); r.setPayoutTransactionId(id); r.setIdempotencyKey(key); return r;
    }
    private void assertConflict(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApplicationException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }
}
