package com.fer.projekt

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ProjektApplication

fun main(args: Array<String>) {
    runApplication<ProjektApplication>(*args)
}
