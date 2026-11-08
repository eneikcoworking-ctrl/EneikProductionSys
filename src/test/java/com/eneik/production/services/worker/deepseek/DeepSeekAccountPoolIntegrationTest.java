package com.eneik.production.services.worker.deepseek;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.services.worker.WorkerExecutionRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeepSeekAccountPoolIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private WorkerExecutionRouter workerExecutionRouter;

    @Test
    @DisplayName("V145 sovereign DeepSeek worker pool must be present, active, and resolvable by router")
    void verifyDeepSeekWorkerPoolIsActive() {
        List<AccountEntity> deepseekAccounts = accountRepository.findAll().stream()
                .filter(a -> "DEEPSEEK".equalsIgnoreCase(a.getProvider()))
                .toList();

        assertThat(deepseekAccounts)
                .as("Sovereign DeepSeek worker pool must contain 8 accounts matching local agy capacity")
                .hasSize(8);

        for (AccountEntity worker : deepseekAccounts) {
            assertThat(worker.isEnabled()).isTrue();
            assertThat(worker.getStatus()).isEqualTo(AccountStatus.idle);
            assertThat(worker.getCapabilities()).contains("BARCAN-TAG-00", "BARCAN-TAG-12");
            assertThat(worker.getApiKey()).isNotBlank();
            assertThat(worker.getName()).startsWith("deepseek-worker-");
        }

        assertThat(workerExecutionRouter.hasAvailableDeepSeekAccountOrKey())
                .as("WorkerExecutionRouter must detect available DeepSeek capacity")
                .isTrue();

        Optional<AccountEntity> available = workerExecutionRouter.findAvailableDeepSeekAccount();
        assertThat(available).isPresent();
        assertThat(available.get().getProvider()).isEqualToIgnoringCase("DEEPSEEK");
    }
}
