package com.fer.projekt.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors

@Configuration
class PlagConfigExecutorServiceConfig {

    @Bean("plagConfigExecutorService")
    fun plagConfigExecutorService() = Executors.newVirtualThreadPerTaskExecutor()
}
