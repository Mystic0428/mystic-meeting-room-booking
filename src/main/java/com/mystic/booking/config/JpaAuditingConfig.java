package com.mystic.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 啟用 JPA Auditing(@CreatedDate / @LastModifiedDate)。
 * 刻意放在獨立的 @Configuration,而非主程式 class——避免 @SpringBootTest 載入時
 * jpaAuditingHandler 被重複註冊而報 BeanDefinitionOverrideException。
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
