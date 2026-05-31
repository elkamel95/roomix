package com.roomix.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class AppConfig {

    /**
     * TransactionTemplate pour les services @Async qui ne peuvent pas utiliser @Transactional.
     * (@Async + @Transactional sur la même méthode est une anti-pattern — le commit arrive
     *  avant que le vrai corps ne s'exécute dans le thread-pool.)
     *
     * @ConditionalOnBean : n'est pas créé dans les contextes @WebMvcTest (pas de JPA/TX manager).
     */
    @Bean
    @ConditionalOnBean(PlatformTransactionManager.class)
    public TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }
}
