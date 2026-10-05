package com.payment.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.backend.dto.request.bofa.CreatePayoutReleaseRequest;
import com.payment.backend.dto.request.bofa.CreatePayoutReleaseRequest.ExpectedAmount;
import com.payment.backend.entity.*;
import com.payment.backend.exception.ApplicationException;
import com.payment.backend.exception.ErrorCode;
import com.payment.backend.repository.*;
import com.payment.backend.service.impl.BofaPayoutReleaseServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true",
        "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
        "spring.jpa.show-sql=false"}, showSql = false)
@ContextConfiguration(classes = BofaPayoutReleaseServiceImplTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BofaPayoutReleaseServiceImplTest {
    @Configuration
    @EntityScan(basePackageClasses = BofaCheckoutOrder.class)
    @EnableJpaRepositories(basePackageClasses = BofaCheckoutOrderRepository.class)
    @Import(BofaPayoutReleaseServiceImpl.class)
    static class Config {
        @Bean TransactionTemplate transactions(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    @Autowired BofaPayoutReleaseService service;
    @Autowired BofaCheckoutOrderRepository orders;
    @MockitoSpyBean BofaPayoutReleaseRepository releases;
    @Autowired BofaRecipientCreditRepository credits;
    @Autowired BofaAccountBalanceRepository bankBalances;
    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager entityManager;
    UUID recipient;
    BofaCheckoutOrder order;

    @BeforeEach
    void setup() {
        credits.deleteAll();
        releases.deleteAll();
        orders.deleteAll();
        bankBalances.deleteAll();
        recipient = UUID.randomUUID();
        order = checkout(BofaCheckoutOrderStatus.CAPTURED);
    }

    @Test
    void capturedReleaseCreditsOneEntitlementAndNeverDebitsClientAgain() {
        BofaAccountBalance bank = new BofaAccountBalance();
        bank.setBankAccountNumber(order.getPayerBankAccountNumber());
        bank.setBalance(new BigDecimal("9500.00"));
        bankBalances.saveAndFlush(bank);
        var result = service.release(request("release-1"));
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.amount()).isEqualByComparingTo("500.00");
        assertThat(result.recipientUserId()).isEqualTo(recipient);
        assertThat(result.simulation()).isTrue();
        assertThat(result.retryable()).isFalse();
        assertThat(result.releaseReference()).isEqualTo("sim-release-" + result.releaseId());
        assertThat(releases.findById(result.releaseId()).orElseThrow().getStatus())
                .isEqualTo(BofaPayoutReleaseStatus.SUCCEEDED);
        assertThat(credits.creditedEntitlement(recipient, "USD")).isEqualByComparingTo("500.00");
        assertThat(credits.count()).isEqualTo(1);
        assertThat(bankBalances.findById(bank.getId()).orElseThrow().getBalance()).isEqualByComparingTo("9500.00");
        assertThat(orders.count()).isEqualTo(1);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(BofaCheckoutOrderStatus.CAPTURED);
    }

    @Test
    void uncapturedCheckoutCannotRelease() {
        order.setStatus(BofaCheckoutOrderStatus.CREATED);
        orders.saveAndFlush(order);
        expectError(request("release-1"), ErrorCode.INVALID_CHECKOUT_ORDER_STATUS);
        assertEmptyLedger();
    }

    @Test
    void missingCheckoutCannotRelease() {
        expectError(new CreatePayoutReleaseRequest(UUID.randomUUID(), recipient,
                new ExpectedAmount(new BigDecimal("500.00"), "USD"), "release-1"), ErrorCode.CHECKOUT_ORDER_NOT_FOUND);
        assertEmptyLedger();
    }

    @Test
    void amountMustEqualCapturedAmount() {
        expectError(withAmount("499.99", "USD"), ErrorCode.RELEASE_AMOUNT_MISMATCH);
        assertEmptyLedger();
    }

    @Test
    void currencyMustMatchImplicitUsdCheckout() {
        expectError(withAmount("500.00", "VND"), ErrorCode.RELEASE_CURRENCY_MISMATCH);
        assertEmptyLedger();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "500.001", "100000000000000000.00"})
    void invalidAmountsNeverCreateCredit(String amount) {
        expectError(withAmount(amount, "USD"), ErrorCode.INVALID_DATA);
        assertEmptyLedger();
    }

    @Test
    void payerCannotBeRecipient() {
        recipient = order.getPayerUserId();
        expectError(request("release-1"), ErrorCode.RELEASE_RECIPIENT_INVALID);
        assertEmptyLedger();
    }

    @Test
    void emptyOrPaddedKeyRejected() {
        expectError(request(" "), ErrorCode.INVALID_DATA);
        expectError(request(" release-1 "), ErrorCode.INVALID_DATA);
        assertEmptyLedger();
    }

    @Test
    void sameKeyAndNormalizedAmountReturnSameRelease() {
        var first = service.release(request("release-1"));
        var retry = new CreatePayoutReleaseRequest(order.getId(), recipient,
                new ExpectedAmount(new BigDecimal("500"), "USD"), "release-1");
        assertThat(service.release(retry)).isEqualTo(first);
        assertThat(service.release(request("release-1"))).isEqualTo(first);
        assertThat(releases.count()).isEqualTo(1);
        assertThat(credits.count()).isEqualTo(1);
    }

    @Test
    void sameKeyChangedPayloadConflictsWithoutCredit() {
        service.release(request("release-1"));
        expectError(withAmount("499.00", "USD"), ErrorCode.RELEASE_KEY_CONFLICT);
        expectError(new CreatePayoutReleaseRequest(order.getId(), UUID.randomUUID(),
                request("release-1").expectedAmount(), "release-1"), ErrorCode.RELEASE_KEY_CONFLICT);
        assertThat(credits.creditedEntitlement(recipient, "USD")).isEqualByComparingTo("500.00");
    }

    @Test
    void differentKeyOnReleasedCheckoutConflicts() {
        service.release(request("release-1"));
        expectError(request("release-2"), ErrorCode.PAYOUT_ALREADY_RELEASED);
        assertThat(credits.count()).isEqualTo(1);
    }

    @Test
    void stableLookupsReturnExactSafeRelease() throws Exception {
        var response = service.release(request("release-1"));
        assertThat(service.getByReleaseKey("release-1")).isEqualTo(response);
        assertThat(service.getByCheckoutOrderId(order.getId())).isEqualTo(response);
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(response);
        assertThat(json.path("amount").asText()).isEqualTo("500.00");
        assertThat(json.path("amount").isTextual()).isTrue();
        assertThat(json.toString()).doesNotContain(order.getPayerBankAccountNumber(), "bankAccount", "token", "signer", "payloadHash", "password");
    }

    @Test
    void missingLookupIsNotFoundAndDoesNotExecuteRelease() {
        assertThatThrownBy(() -> service.getByReleaseKey("missing"))
                .isInstanceOfSatisfying(ApplicationException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYOUT_RELEASE_NOT_FOUND));
        assertThatThrownBy(() -> service.getByCheckoutOrderId(order.getId())).isInstanceOf(ApplicationException.class);
        assertEmptyLedger();
    }

    @Test
    void releasePersistenceFailureAfterCreditRollsBackBothRows() {
        AtomicInteger saves = new AtomicInteger();
        doAnswer(invocation -> {
            if (saves.incrementAndGet() == 2) {
                assertThat(credits.count()).isEqualTo(1); // Credit was actually flushed in this transaction.
                throw new IllegalStateException("test persistence failure");
            }
            BofaPayoutRelease release = invocation.getArgument(0);
            entityManager.persist(release);
            entityManager.flush();
            return release;
        }).when(releases).saveAndFlush(any(BofaPayoutRelease.class));
        assertThatThrownBy(() -> service.release(request("release-1")))
                .hasMessage("test persistence failure");
        assertEmptyLedger();
    }

    @Test
    void callerTransactionRollbackCannotLeaveCreditOrRelease() {
        assertThatThrownBy(() -> transactions.execute(tx -> {
            service.release(request("release-1"));
            throw new IllegalStateException("outer rollback");
        })).hasMessage("outer rollback");
        assertEmptyLedger();
        assertThat(service.release(request("release-1")).status()).isEqualTo("SUCCEEDED");
        assertThat(credits.count()).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyCannotCreditTwice() throws Exception {
        var requests = new ArrayList<CreatePayoutReleaseRequest>();
        for (int i = 0; i < 6; i++) requests.add(request("release-1"));
        var outcomes = concurrently(requests);
        assertThat(outcomes).allMatch(UUID.class::isInstance);
        assertThat(outcomes.stream().distinct().count()).isEqualTo(1);
        assertThat(releases.count()).isEqualTo(1);
        assertThat(credits.creditedEntitlement(recipient, "USD")).isEqualByComparingTo("500.00");
    }

    @Test
    void concurrentDifferentKeysOnOneCheckoutCannotCreditTwice() throws Exception {
        var outcomes = concurrently(List.of(request("release-1"), request("release-2")));
        assertThat(outcomes.stream().filter(UUID.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes).contains(ErrorCode.PAYOUT_ALREADY_RELEASED);
        assertThat(credits.count()).isEqualTo(1);
    }

    @Test
    void globalKeyRaceAcrossCheckoutsRollsBackLosingCredit() throws Exception {
        var first = request("release-1");
        var secondOrder = checkout(BofaCheckoutOrderStatus.CAPTURED);
        var second = new CreatePayoutReleaseRequest(secondOrder.getId(), recipient,
                first.expectedAmount(), "release-1");
        var outcomes = concurrently(List.of(first, second));
        assertThat(outcomes.stream().filter(UUID.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes).contains(ErrorCode.RELEASE_KEY_CONFLICT);
        assertThat(credits.count()).isEqualTo(1);
    }

    @Test
    void independentCheckoutsAccumulateUserEntitlementWithoutLostCredit() throws Exception {
        var first = request("release-1");
        var secondOrder = checkout(BofaCheckoutOrderStatus.CAPTURED);
        var second = new CreatePayoutReleaseRequest(secondOrder.getId(), recipient,
                first.expectedAmount(), "release-2");
        assertThat(concurrently(List.of(first, second))).allMatch(UUID.class::isInstance);
        assertThat(credits.creditedEntitlement(recipient, "USD")).isEqualByComparingTo("1000.00");
    }

    private List<Object> concurrently(List<CreatePayoutReleaseRequest> requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (var request : requests) futures.add(executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                try { return service.release(request).releaseId(); }
                catch (ApplicationException ex) { return ex.getErrorCode(); }
            }));
            start.countDown();
            List<Object> result = new ArrayList<>();
            for (var future : futures) result.add(future.get(20, TimeUnit.SECONDS));
            return result;
        } finally { executor.shutdownNow(); }
    }

    private BofaCheckoutOrder checkout(BofaCheckoutOrderStatus status) {
        BofaCheckoutOrder value = new BofaCheckoutOrder();
        value.setPayerUserId(UUID.randomUUID());
        value.setJobId(UUID.randomUUID());
        value.setAmountUsd(new BigDecimal("500.00"));
        value.setBofaOrderId(UUID.randomUUID().toString());
        value.setPayerBankCode("VIETCOMBANK");
        value.setPayerBankAccountNumber("123456789");
        value.setPayerBankAccountHolderName("Test Client");
        value.setStatus(status);
        value.setCreatedAt(LocalDateTime.now());
        return orders.saveAndFlush(value);
    }

    private CreatePayoutReleaseRequest request(String key) {
        return new CreatePayoutReleaseRequest(order.getId(), recipient,
                new ExpectedAmount(new BigDecimal("500.00"), "USD"), key);
    }
    private CreatePayoutReleaseRequest withAmount(String amount, String currency) {
        return new CreatePayoutReleaseRequest(order.getId(), recipient,
                new ExpectedAmount(new BigDecimal(amount), currency), "release-1");
    }
    private void expectError(CreatePayoutReleaseRequest request, ErrorCode code) {
        assertThatThrownBy(() -> service.release(request)).isInstanceOfSatisfying(ApplicationException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }
    private void assertEmptyLedger() {
        assertThat(releases.count()).isZero();
        assertThat(credits.count()).isZero();
    }
}
