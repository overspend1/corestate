package com.corestate.backup

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableJpaRepositories
@EnableTransactionManagement
@ComponentScan(basePackages = ["com.corestate.backup"])
class BackupEngineService

fun main(args: Array<String>) {
    runApplication<BackupEngineService>(*args)
}