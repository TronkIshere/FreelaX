package com.payment.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.backend.dto.request.bofa.*;
import com.payment.backend.entity.*;
import com.payment.backend.exception.*;
import com.payment.backend.repository.*;
import com.payment.backend.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true", "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true"}, showSql = false)
@ContextConfiguration(classes = BofaCheckoutRefundServiceImplTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BofaCheckoutRefundServiceImplTest {
    @Configuration @EntityScan(basePackageClasses = BofaCheckoutOrder.class)
    @EnableJpaRepositories(basePackageClasses = BofaCheckoutOrderRepository.class)
    @Import({BofaCheckoutRefundServiceImpl.class, BofaPayoutReleaseServiceImpl.class,
            BofaCheckoutOrderServiceImpl.class, BofaAccountBalanceServiceImpl.class})
    static class Config { @Bean TransactionTemplate tx(PlatformTransactionManager manager) { return new TransactionTemplate(manager); } }
    @Autowired BofaCheckoutRefundService service;
    @Autowired BofaPayoutReleaseService release;
    @Autowired BofaCheckoutOrderService checkout;
    @Autowired BofaCheckoutOrderRepository orders;
    @Autowired BofaPayoutReleaseRepository releases;
    @Autowired BofaRecipientCreditRepository credits;
    @Autowired BofaAccountBalanceRepository balances;
    @MockitoSpyBean BofaCheckoutRefundRepository refunds;
    @Autowired jakarta.persistence.EntityManager entityManager;
    UUID order, payer, recipient;
    @BeforeEach void setup() {
        refunds.deleteAll(); credits.deleteAll(); releases.deleteAll(); orders.deleteAll(); balances.deleteAll();
        payer = UUID.randomUUID(); recipient = UUID.randomUUID(); order = createOrder(); checkout.capture(order);
        assertThat(balance()).isEqualByComparingTo("9500");
    }
    @Test void capturedRefundRestoresOriginalPayerExactlyOnce() {
        var r = service.refund(request("key"));
        assertThat(r.payerUserId()).isEqualTo(payer); assertThat(r.status()).isEqualTo("SUCCEEDED");
        assertThat(r.refundReference()).isEqualTo("sim-refund-" + r.refundId()); assertThat(r.simulation()).isTrue();
        assertThat(balance()).isEqualByComparingTo("10000"); assertThat(refunds.count()).isEqualTo(1);
        assertThat(orders.count()).isEqualTo(1); assertThat(credits.count()).isZero();
        checkout.capture(order); assertThat(balance()).isEqualByComparingTo("10000");
    }
    @Test void uncapturedCheckoutRejected() {
        UUID un = createOrder(); expect(new CreateCheckoutRefundRequest(un, amount("500.00", "USD"), "key"), ErrorCode.INVALID_CHECKOUT_ORDER_STATUS);
        assertThat(refunds.count()).isZero(); assertThat(balance()).isEqualByComparingTo("9500");
    }
    @Test void amountMismatchRejected() { expect(new CreateCheckoutRefundRequest(order, amount("499.99", "USD"), "key"), ErrorCode.REFUND_AMOUNT_MISMATCH); }
    @Test void currencyMismatchRejected() { expect(new CreateCheckoutRefundRequest(order, amount("500.00", "VND"), "key"), ErrorCode.REFUND_CURRENCY_MISMATCH); }
    @Test void sameKeySamePayloadReturnsOriginalResult() {
        var r = service.refund(request("key")); assertThat(service.refund(request("key")).refundId()).isEqualTo(r.refundId());
        assertThat(balance()).isEqualByComparingTo("10000"); assertThat(refunds.count()).isEqualTo(1);
    }
    @Test void sameKeyChangedPayloadConflicts() {
        service.refund(request("key")); expect(new CreateCheckoutRefundRequest(order, amount("499.99", "USD"), "key"), ErrorCode.REFUND_KEY_CONFLICT);
        assertThat(balance()).isEqualByComparingTo("10000");
    }
    @Test void differentKeyCannotCreditSameCheckoutAgain() {
        service.refund(request("a")); expect(request("b"), ErrorCode.CHECKOUT_ALREADY_REFUNDED); assertThat(balance()).isEqualByComparingTo("10000");
    }
    @Test void existingPrimaryReleaseBlocksRefund() {
        release.release(releaseRequest()); expect(request("refund"), ErrorCode.REFUND_RELEASE_CONFLICT);
        assertThat(balance()).isEqualByComparingTo("9500"); assertThat(credits.count()).isEqualTo(1);
    }
    @Test void refundedCheckoutCannotRelease() {
        service.refund(request("refund"));
        assertThatThrownBy(() -> release.release(releaseRequest())).isInstanceOfSatisfying(ApplicationException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFUND_RELEASE_CONFLICT));
        assertThat(credits.count()).isZero();
    }
    @Test void missingCaptureLedgerCannotManufactureOpeningBalance() {
        balances.deleteAll(); expect(request("key"), ErrorCode.REFUND_PAYER_LEDGER_MISSING); assertThat(balances.count()).isZero();
    }
    @Test void lookupReturnsSameSafeReferenceAndUnknownIs404() throws Exception {
        var r = service.refund(request("key")); assertThat(service.getByRefundKey("key").refundId()).isEqualTo(r.refundId());
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(r)).doesNotContain("PRIVATE-BANK", "payloadHash", "holder", "password", "token");
        assertThatThrownBy(() -> service.getByRefundKey("missing")).isInstanceOfSatisfying(ApplicationException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFUND_NOT_FOUND));
    }
    @Test void transactionFailureRollsBackCreditAndRefundTogether() {
        doAnswer(a -> { BofaCheckoutRefund r = a.getArgument(0);
            if (r.getId() == null) entityManager.persist(r);
            entityManager.flush();
            if (r.getStatus() == BofaRefundStatus.SUCCEEDED) throw new IllegalStateException("injected after balance credit"); return r;
        }).when(refunds).saveAndFlush(any());
        assertThatThrownBy(() -> service.refund(request("key"))).hasMessageContaining("injected");
        assertThat(balance()).isEqualByComparingTo("9500"); assertThat(refunds.count()).isZero();
    }
    @Test void concurrentSameKeyCreatesOneRefundAndOneCredit() throws Exception {
        var results = concurrent(() -> service.refund(request("key")).refundId(), () -> service.refund(request("key")).refundId());
        assertThat(results.get(0)).isEqualTo(results.get(1)); assertThat(refunds.count()).isEqualTo(1);
        assertThat(balance()).isEqualByComparingTo("10000");
    }
    @Test void refundReleaseRaceCanOnlyProduceOneFinancialWinner() throws Exception {
        var results = concurrent(() -> winner(() -> service.refund(request("refund"))), () -> winner(() -> release.release(releaseRequest())));
        assertThat(results.stream().filter(Boolean.TRUE::equals).count()).isEqualTo(1);
        assertThat(refunds.count() + credits.count()).isEqualTo(1);
        assertThat(balance()).isEqualByComparingTo(refunds.count() == 1 ? "10000" : "9500");
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "500.001", "100000000000000000.00"})
    void invalidAmountsNeverCredit(String value) { expect(new CreateCheckoutRefundRequest(order, amount(value, "USD"), "key"), ErrorCode.INVALID_DATA); }
    @Test void invalidKeyRejected() { expect(request(" key "), ErrorCode.INVALID_DATA); expect(request(""), ErrorCode.INVALID_DATA); }
    private UUID createOrder() {
        CreateCheckoutOrderRequest r = new CreateCheckoutOrderRequest(); r.setPayerUserId(payer); r.setJobId(UUID.randomUUID());
        r.setAmountUsd(new BigDecimal("500.00")); r.setPayerBankAccountNumber("PRIVATE-BANK"); r.setPayerBankCode("TEST"); r.setPayerBankAccountHolderName("Private");
        return checkout.create(r).getId();
    }
    private CreateCheckoutRefundRequest request(String key) { return new CreateCheckoutRefundRequest(order, amount("500.00", "USD"), key); }
    private CreateCheckoutRefundRequest.ExpectedAmount amount(String a, String c) { return new CreateCheckoutRefundRequest.ExpectedAmount(new BigDecimal(a), c); }
    private CreatePayoutReleaseRequest releaseRequest() { return new CreatePayoutReleaseRequest(order, recipient, new CreatePayoutReleaseRequest.ExpectedAmount(new BigDecimal("500.00"), "USD"), "release"); }
    private BigDecimal balance() { return balances.findByBankAccountNumber("PRIVATE-BANK").orElseThrow().getBalance(); }
    private void expect(CreateCheckoutRefundRequest r, ErrorCode code) {
        assertThatThrownBy(() -> service.refund(r)).isInstanceOfSatisfying(ApplicationException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }
    private boolean winner(Runnable call) { try { call.run(); return true; } catch (ApplicationException ex) { return false; } }
    private <T> List<T> concurrent(Callable<T> a, Callable<T> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try { var one = pool.submit(() -> { start.await(); return a.call(); }); var two = pool.submit(() -> { start.await(); return b.call(); });
            start.countDown(); return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
