package com.freelax.solanagateway.api;

import com.freelax.solanagateway.api.ApiTypes.Commitment;
import com.freelax.solanagateway.api.Responses.AccountResponse;
import com.freelax.solanagateway.api.Responses.ConfigDto;
import com.freelax.solanagateway.api.Responses.InvoiceDto;
import com.freelax.solanagateway.api.Responses.MockOnrampReceiptDto;
import com.freelax.solanagateway.api.Responses.RateSnapshotDto;
import com.freelax.solanagateway.api.Responses.TokenBalanceResponse;
import com.freelax.solanagateway.api.Responses.TransactionOperationResponse;
import com.freelax.solanagateway.api.Responses.TransactionStatusResponse;
import com.freelax.solanagateway.api.Responses.WithdrawalDto;
import com.freelax.solanagateway.service.SolanaGatewayService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/solana")
public class SolanaGatewayController {

    private final SolanaGatewayService service;

    public SolanaGatewayController(SolanaGatewayService service) {
        this.service = service;
    }

    @GetMapping("/config")
    AccountResponse<ConfigDto> config(@RequestParam(required = false) Commitment commitment) {
        return service.config(commitment);
    }

    @GetMapping("/invoices/{freelancer}/{invoiceId}")
    AccountResponse<InvoiceDto> invoice(@PathVariable String freelancer, @PathVariable String invoiceId,
                                        @RequestParam(required = false) Commitment commitment) {
        return service.invoice(freelancer, invoiceId, commitment);
    }

    @GetMapping("/rates/{rateId}")
    AccountResponse<RateSnapshotDto> rate(@PathVariable String rateId,
                                          @RequestParam(required = false) Commitment commitment) {
        return service.rate(rateId, commitment);
    }

    @GetMapping("/withdrawals/{freelancer}/{withdrawalId}")
    AccountResponse<WithdrawalDto> withdrawal(@PathVariable String freelancer,
                                              @PathVariable String withdrawalId,
                                              @RequestParam(required = false) Commitment commitment) {
        return service.withdrawal(freelancer, withdrawalId, commitment);
    }

    @GetMapping("/onramp-receipts/{client}/{purchaseId}")
    AccountResponse<MockOnrampReceiptDto> receipt(@PathVariable String client,
                                                  @PathVariable String purchaseId,
                                                  @RequestParam(required = false) Commitment commitment) {
        return service.receipt(client, purchaseId, commitment);
    }

    @GetMapping("/token-balances/{owner}/{mint}")
    TokenBalanceResponse tokenBalance(@PathVariable String owner, @PathVariable String mint,
                                      @RequestParam(required = false) Commitment commitment) {
        return service.tokenBalance(owner, mint, commitment);
    }

    @GetMapping("/transactions/{signature}")
    TransactionStatusResponse transactionStatus(@PathVariable String signature,
                                                 @RequestParam(defaultValue = "false") boolean includeTransaction,
                                                 @RequestParam(required = false) Commitment commitment) {
        return service.transactionStatus(signature, includeTransaction, commitment);
    }

    @PostMapping("/config/initialize")
    TransactionOperationResponse initializeConfig(
            @Valid @RequestBody Requests.InitializeConfigRequest request) {
        return service.initializeConfig(request);
    }

    @PutMapping("/config")
    TransactionOperationResponse updateConfig(@Valid @RequestBody Requests.UpdateConfigRequest request) {
        return service.updateConfig(request);
    }

    @PutMapping("/config/mock-onramp")
    TransactionOperationResponse configureMockOnramp(
            @Valid @RequestBody Requests.ConfigureMockOnrampRequest request) {
        return service.configureMockOnramp(request);
    }

    @PostMapping("/mock-onramp/purchases")
    TransactionOperationResponse mockOnramp(@Valid @RequestBody Requests.MockOnrampRequest request) {
        return service.mockOnramp(request);
    }

    @PostMapping("/rates")
    TransactionOperationResponse publishRate(@Valid @RequestBody Requests.PublishRateRequest request) {
        return service.publishRate(request);
    }

    @PostMapping("/invoices")
    TransactionOperationResponse createInvoice(@Valid @RequestBody Requests.CreateInvoiceRequest request) {
        return service.createInvoice(request);
    }

    @PostMapping("/invoices/{freelancer}/{invoiceId}/pay")
    TransactionOperationResponse payInvoice(@PathVariable String freelancer, @PathVariable String invoiceId,
                                            @Valid @RequestBody Requests.PayInvoiceRequest request) {
        return service.payInvoice(freelancer, invoiceId, request);
    }

    @PostMapping("/invoices/{freelancer}/{invoiceId}/cancel")
    TransactionOperationResponse cancelInvoice(@PathVariable String freelancer,
                                               @PathVariable String invoiceId,
                                               @RequestBody(required = false)
                                               Requests.InvoiceActionRequest request) {
        return service.cancelInvoice(freelancer, invoiceId, action(request));
    }

    @DeleteMapping("/invoices/{freelancer}/{invoiceId}")
    TransactionOperationResponse closeInvoice(@PathVariable String freelancer,
                                              @PathVariable String invoiceId,
                                              @RequestBody(required = false)
                                              Requests.InvoiceActionRequest request) {
        return service.closeInvoice(freelancer, invoiceId, action(request));
    }

    @PostMapping("/withdrawals")
    TransactionOperationResponse requestOfframp(
            @Valid @RequestBody Requests.RequestOfframpRequest request) {
        return service.requestOfframp(request);
    }

    @PostMapping("/withdrawals/{freelancer}/{withdrawalId}/complete")
    TransactionOperationResponse completeOfframp(@PathVariable String freelancer,
                                                 @PathVariable String withdrawalId,
                                                 @Valid @RequestBody Requests.CompleteOfframpRequest request) {
        return service.completeOfframp(freelancer, withdrawalId, request);
    }

    @PostMapping("/withdrawals/{freelancer}/{withdrawalId}/fail")
    TransactionOperationResponse failOfframp(@PathVariable String freelancer,
                                             @PathVariable String withdrawalId,
                                             @Valid @RequestBody Requests.MarkOfframpFailedRequest request) {
        return service.failOfframp(freelancer, withdrawalId, request);
    }

    @PostMapping("/withdrawals/{freelancer}/{withdrawalId}/resolve")
    TransactionOperationResponse resolveOfframp(@PathVariable String freelancer,
                                                @PathVariable String withdrawalId,
                                                @Valid @RequestBody Requests.ResolveOfframpRequest request) {
        return service.resolveOfframp(freelancer, withdrawalId, request);
    }

    @PostMapping("/transactions/submit")
    Responses.SubmitTransactionResponse submit(
            @Valid @RequestBody Requests.SubmitSignedTransactionRequest request) {
        return service.submit(request);
    }

    private Requests.InvoiceActionRequest action(Requests.InvoiceActionRequest request) {
        return request == null ? new Requests.InvoiceActionRequest(null, null, null) : request;
    }
}
