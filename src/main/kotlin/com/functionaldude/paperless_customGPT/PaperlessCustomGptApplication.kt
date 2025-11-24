package com.functionaldude.paperless_customGPT

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PaperlessCustomGptApplication

fun main(args: Array<String>) {
	runApplication<PaperlessCustomGptApplication>(*args)
}
