package ca.pharmaforecast.backend.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AuthBootstrapConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuthBootstrapService.class)
    AuthBootstrapService authBootstrapService(JdbcTemplate jdbcTemplate) {
        return new JdbcAuthBootstrapService(jdbcTemplate);
    }
}
