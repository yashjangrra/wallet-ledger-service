package com.yash.ledgerservice;

import com.yash.ledgerservice.dto.AccountResponse;
import com.yash.ledgerservice.dto.CreateAccountRequest;
import com.yash.ledgerservice.dto.DepositRequest;
import com.yash.ledgerservice.dto.TransferRequest;
import com.yash.ledgerservice.exception.InsufficientFundsException;
import com.yash.ledgerservice.repository.LedgerEntryRepository;
import com.yash.ledgerservice.service.AccountService;
import com.yash.ledgerservice.service.LedgerService;
import com.yash.ledgerservice.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentTransferTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void concurrentTransfersMustNotOverdraftTheSender() throws Exception {
        final int threads = 50;
        final long initialBalance = 100_000;  // Rs 1000.00 in paise
        final long transferAmount = 80_000;   // Rs 800.00: only ONE of these can succeed

        // Unique names per run, so reruns never collide with old data
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AccountResponse alice = accountService.create(new CreateAccountRequest("Alice-" + suffix, "INR"));
        AccountResponse bob = accountService.create(new CreateAccountRequest("Bob-" + suffix, "INR"));
        ledgerService.deposit(alice.id(), new DepositRequest(initialBalance));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final String key = "race-" + suffix + "-" + i;   // different key per request
            pool.execute(() -> {
                ready.countDown();
                try {
                    start.await();                           // wait for the starting gun
                    transferService.transfer(key,
                            new TransferRequest(alice.id(), bob.id(), transferAmount));
                    succeeded.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    rejected.incrementAndGet();              // correct behaviour
                } catch (Exception e) {
                    failed.incrementAndGet();
                    System.out.println("Unexpected error: " + e);
                }
            });
        }

        ready.await();       // all 50 threads are standing at the line...
        start.countDown();   // ...GO!
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        long aliceBalance = ledgerEntryRepository.computeBalance(alice.id());
        long bobBalance = ledgerEntryRepository.computeBalance(bob.id());

        System.out.println("========== CONCURRENCY RESULT ==========");
        System.out.println("Succeeded : " + succeeded.get() + "  (should be exactly 1)");
        System.out.println("Rejected  : " + rejected.get());
        System.out.println("Errors    : " + failed.get());
        System.out.println("Alice     : " + aliceBalance + "  (should never be negative)");
        System.out.println("Bob       : " + bobBalance);
        System.out.println("Total     : " + (aliceBalance + bobBalance) + "  (should equal " + initialBalance + ")");
        System.out.println("========================================");

        assertThat(failed.get()).as("unexpected errors").isZero();
        assertThat(aliceBalance + bobBalance).as("money must be conserved").isEqualTo(initialBalance);
        assertThat(succeeded.get()).as("transfers that succeeded").isEqualTo(1);
        assertThat(aliceBalance).as("Alice's balance must never go negative").isGreaterThanOrEqualTo(0);
    }
}