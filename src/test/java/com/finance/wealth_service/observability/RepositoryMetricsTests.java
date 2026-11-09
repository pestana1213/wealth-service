package com.finance.wealth_service.observability;

import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.goal.repository.GoalRepository;
import com.finance.wealth_service.support.TestUsers;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RepositoryMetricsTests {

    @Autowired MeterRegistry registry;
    @Autowired UserRepository users;
    @Autowired GoalRepository goals;

    @Test
    void timesEachRepositoryMethodWithAHistogram() {
        User user = users.saveAndFlush(TestUsers.newUser("Metrics"));

        goals.findOwnedBy(UUID.randomUUID(), user.getSubject(), user.getIssuer());

        Timer timer = registry.find("spring.data.repository.invocations")
                .tag("repository", "GoalRepository")
                .tag("method", "findOwnedBy")
                .tag("state", "SUCCESS")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isGreaterThanOrEqualTo(1);
        HistogramSnapshot snapshot = timer.takeSnapshot();
        assertThat(snapshot.histogramCounts()).isNotEmpty();
    }
}
