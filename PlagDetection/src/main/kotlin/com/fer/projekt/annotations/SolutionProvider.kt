package com.fer.projekt.annotations

import com.fer.projekt.controller.SolutionProviderName

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SolutionProvider(val value: SolutionProviderName)
