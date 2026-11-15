package com.yash.ledgerservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yash.ledgerservice.dto.CreateAccountRequest;
import com.yash.ledgerservice.dto.DepositRequest;
import com.yash.ledgerservice.dto.TransferRequest;
import com.yash.ledgerservice.model.Account;
import com.yash.ledgerservice.repository.AccountRepository;
import com.yash.ledgerservice.service.AccountService;
import com.yash.ledgerservice.service.LedgerService;
import com.yash.ledgerservice.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class ErrorPathIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private AccountService accountService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    private Long usdAccount1Id;
    private Long usdAccount2Id;
    private Long inrAccountId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        usdAccount1Id = accountService.create(new CreateAccountRequest("Test Alice", "USD")).id();
        usdAccount2Id = accountService.create(new CreateAccountRequest("Test Bob", "USD")).id();
        inrAccountId = accountService.create(new CreateAccountRequest("Test Charlie", "INR")).id();

        // Deposit 100.00 USD (10000 cents) into usdAccount1
        ledgerService.deposit(usdAccount1Id, new DepositRequest(10000L));
    }

    // =========================================================================
    // 1. HTTP 400 BAD REQUEST TESTS
    // =========================================================================

    @Test
    @DisplayName("400: Missing Idempotency-Key Header on Transfer")
    void testTransfer_missingIdempotencyKey_returns400() throws Exception {
        TransferRequest req = new TransferRequest(usdAccount1Id, usdAccount2Id, 1000L);

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400: Zero or Negative Transfer Amount")
    void testTransfer_zeroOrNegativeAmount_returns400() throws Exception {
        TransferRequest req = new TransferRequest(usdAccount1Id, usdAccount2Id, 0L);

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400: Blank Owner Name on Account Creation")
    void testCreateAccount_blankOwnerName_returns400() throws Exception {
        CreateAccountRequest req = new CreateAccountRequest("   ", "USD");

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400: Negative Deposit Amount")
    void testDeposit_negativeAmount_returns400() throws Exception {
        DepositRequest req = new DepositRequest(-500L);

        mockMvc.perform(post("/accounts/" + usdAccount1Id + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 2. HTTP 404 NOT FOUND TESTS
    // =========================================================================

    @Test
    @DisplayName("404: Account ID Not Found")
    void testGetAccount_notFound_returns404() throws Exception {
        mockMvc.perform(get("/accounts/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"));
    }

    @Test
    @DisplayName("404: Deposit into Non-Existent Account")
    void testDeposit_accountNotFound_returns404() throws Exception {
        DepositRequest req = new DepositRequest(1000L);

        mockMvc.perform(post("/accounts/999999/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("404: Transfer with Non-Existent Sender")
    void testTransfer_senderNotFound_returns404() throws Exception {
        TransferRequest req = new TransferRequest(999999L, usdAccount2Id, 500L);

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("404: Transfer Reversal with Non-Existent Transfer ID")
    void testReverseTransfer_notFound_returns404() throws Exception {
        mockMvc.perform(post("/transfers/999999/reverse"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Transfer not found"));
    }

    // =========================================================================
    // 3. HTTP 409 CONFLICT TESTS
    // =========================================================================

    @Test
    @DisplayName("409: Idempotency Key Reused with Different Payload")
    void testTransfer_idempotencyConflict_returns409() throws Exception {
        String key = "test-conflict-" + UUID.randomUUID();
        TransferRequest originalReq = new TransferRequest(usdAccount1Id, usdAccount2Id, 1000L);

        // 1. Initial valid transfer
        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalReq)))
                .andExpect(status().isCreated());

        // 2. Conflicting transfer with different amount under same key
        TransferRequest conflictingReq = new TransferRequest(usdAccount1Id, usdAccount2Id, 2500L);
        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictingReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Idempotency key conflict"));
    }

    // =========================================================================
    // 4. HTTP 422 UNPROCESSABLE ENTITY TESTS
    // =========================================================================

    @Test
    @DisplayName("422: Transfer to the Same Account")
    void testTransfer_sameAccount_returns422() throws Exception {
        TransferRequest req = new TransferRequest(usdAccount1Id, usdAccount1Id, 500L);

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Cannot transfer to the same account")));
    }

    @Test
    @DisplayName("422: Currency Mismatch Between Sender and Recipient")
    void testTransfer_currencyMismatch_returns422() throws Exception {
        TransferRequest req = new TransferRequest(usdAccount1Id, inrAccountId, 500L);

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Currency mismatch")));
    }

    @Test
    @DisplayName("422: Insufficient Funds (Overdraft Prevention)")
    void testTransfer_insufficientFunds_returns422() throws Exception {
        // usdAccount1 has 10000 cents, attempt to transfer 50000 cents
        TransferRequest req = new TransferRequest(usdAccount1Id, usdAccount2Id, 50000L);

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Insufficient funds in account")));
    }

    @Test
    @DisplayName("422: Cannot Reverse Twice")
    void testReverseTransfer_cannotReverseTwice_returns422() throws Exception {
        // 1. Execute transfer
        String key = "rev-test-" + UUID.randomUUID();
        TransferRequest req = new TransferRequest(usdAccount1Id, usdAccount2Id, 2000L);

        String response = mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long transferId = objectMapper.readTree(response).get("transferId").asLong();

        // 2. First reverse succeeds
        mockMvc.perform(post("/transfers/" + transferId + "/reverse"))
                .andExpect(status().isOk());

        // 3. Second reverse fails with 422
        mockMvc.perform(post("/transfers/" + transferId + "/reverse"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("has already been reversed")));
    }

    @Test
    @DisplayName("422: Cannot Reverse if Recipient Has Already Spent the Money")
    void testReverseTransfer_recipientSpentFunds_returns422() throws Exception {
        // 1. Initial state: usdAccount1 has 10000. Transfer 4000 to usdAccount2.
        // usdAccount2 now has 4000 balance.
        String key1 = "rev-spend-1-" + UUID.randomUUID();
        TransferRequest req1 = new TransferRequest(usdAccount1Id, usdAccount2Id, 4000L);

        String res1 = mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", key1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long transfer1Id = objectMapper.readTree(res1).get("transferId").asLong();

        // 2. Create third account and have usdAccount2 spend 3500 (leaving usdAccount2 with only 500)
        Long usdAccount3Id = accountService.create(new CreateAccountRequest("Test Dave", "USD")).id();
        String key2 = "rev-spend-2-" + UUID.randomUUID();
        TransferRequest req2 = new TransferRequest(usdAccount2Id, usdAccount3Id, 3500L);

        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", key2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // 3. Attempting to reverse transfer1 (which requires 4000 from usdAccount2) must fail with 422!
        mockMvc.perform(post("/transfers/" + transfer1Id + "/reverse"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("has already spent the funds")));
    }
}
