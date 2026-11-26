package com.wallet.recon.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallet.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MaintenanceSchedulerLockTest {

    @Autowired
    private MaintenanceScheduler scheduler;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void runningAJobLeavesItsLockRow() {
        scheduler.expireStaleTopUps();

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM shedlock WHERE name = 'wallet-expire-topups'", Integer.class);
        assertThat(rows).isEqualTo(1);
    }
}
