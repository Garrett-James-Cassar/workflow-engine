package dev.workflow.app

import dev.workflow.core.Engine
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
class WorkflowEngineApplication {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun engine(clock: Clock): Engine = Engine(clock)
}

fun main(args: Array<String>) {
    runApplication<WorkflowEngineApplication>(*args)
}
