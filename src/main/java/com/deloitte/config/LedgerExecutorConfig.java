package com.deloitte.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class LedgerExecutorConfig {

	@Bean(name = "ledgerExecutor")
	public Executor ledgerExecutor() {

		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

		/*
		 * IMPORTANT:
		 *
		 * Start with: corePoolSize = 15 maxPoolSize = 20
		 *
		 * Do NOT directly use 50/100 threads.
		 *
		 * GST API + DB connection pool can become the bottleneck.
		 */
		executor.setCorePoolSize(15);
		executor.setMaxPoolSize(20);

		executor.setQueueCapacity(500);

		executor.setThreadNamePrefix("ledger-worker-");

		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(120);

		executor.initialize();

		return executor;
	}
}