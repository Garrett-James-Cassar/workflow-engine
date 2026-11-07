package dev.workflow.app.config

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * DB spans: the DataSource is wrapped with OpenTelemetry's JDBC instrumentation, so every statement is a
 * child span of the use case or request that ran it. Statements are sanitised (no literal values).
 */
@Configuration(proxyBeanMethods = false)
class JdbcTelemetryConfiguration {
    companion object {
        @Bean
        @JvmStatic
        fun dataSourceTelemetry(otel: ObjectProvider<OpenTelemetry>): BeanPostProcessor = object : BeanPostProcessor {
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                // Resolve OpenTelemetry only for the DataSource: asking for it on every bean would create the SDK too early.
                if (bean !is DataSource) return bean
                return otel.ifAvailable?.let { JdbcTelemetry.create(it).wrap(bean) } ?: bean
            }
        }
    }
}
