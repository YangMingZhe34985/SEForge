package com.ustb.seforge.review.infrastructure;

import com.ustb.seforge.config.SEForgeProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports configuration state without contacting SonarQube during application startup. */
@Component("sonarQube")
public class SonarHealthIndicator implements HealthIndicator {
    private final SEForgeProperties properties;

    public SonarHealthIndicator(SEForgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        SEForgeProperties.Sonar sonar = properties.getSonar();
        if (!sonar.isEnabled()) {
            return Health.status("DEGRADED")
                    .withDetail("reason", "SonarQube code review is disabled").build();
        }
        if (blank(sonar.getToken()) || blank(sonar.getServerUrl())
                || blank(sonar.getScannerExecutable())) {
            return Health.status("DEGRADED")
                    .withDetail("reason", "SonarQube code review is not fully configured").build();
        }
        return Health.up().withDetail("mode", "configured; connectivity checked per review job")
                .build();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
