package com.corestate.backup

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BackupEngineService

fun main(args: Array<String>) {
    runApplication<BackupEngineService>(*args)
}