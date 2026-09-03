package com.deloitte.service.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.deloitte.returns.repository.registration.RegularTaxpayerRepository;
import com.deloitte.service.impl.CommonServiceGstrLedgerImpl;
import com.deloitte.service.utility.procedure.LedgerGstinProjection;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Optimized GST Ledger processor.
 *
 * GSTINs are obtained directly from the stored procedure through
 * RegularTaxpayerRepository.
 *
 * Processing is performed in bounded chunks with a fixed number of concurrent
 * workers.
 *
 * This class does NOT load GstinEntity for every GSTIN.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class CommonControllerGstrUtilityImplOptimized {

	private static final String USERNAME = "GSTG2G18";

	/*
	 * ================================================================ DATE
	 * FORMATTERS ================================================================
	 */

	private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

	private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMyyyy");

	private static final DateTimeFormatter DAY_MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	/*
	 * ================================================================ PROCESSING
	 * CONFIGURATION
	 * ================================================================
	 *
	 * 500 GSTINs are taken as one logical batch.
	 *
	 * Only configuredThreads API calls are actually running at the same time.
	 *
	 * Example:
	 *
	 * 470723 GSTINs | +---- Batch 1 = 500 +---- Batch 2 = 500 +---- Batch 3 = 500
	 * ...
	 *
	 * With 10 threads, only 10 API calls execute simultaneously.
	 */
	private static final int CHUNK_SIZE = 500;

	/* Small pause between batches. */
	private static final long CHUNK_PAUSE_MILLIS = 100L;

	private final RegularTaxpayerRepository regularTaxpayerRepository;

	private final CommonServiceGstrLedgerImpl commonServiceGstrLedgerImpl;

	/*
	 * Configure in application.properties:
	 *
	 * ledger.processing.threads=10
	 *
	 * Maximum is deliberately restricted to 20.
	 */
	@Value("${ledger.processing.threads:10}")
	private int configuredThreads;

	private ExecutorService ledgerExecutor;

	/*
	 * ================================================================ EXECUTOR
	 * INITIALIZATION
	 * ================================================================
	 */

	@PostConstruct
	public void initializeExecutor() {

		int threads = Math.max(1, Math.min(configuredThreads, 20));

		ledgerExecutor = Executors.newFixedThreadPool(threads);

		log.info("============================================================");

		log.info("GST LEDGER EXECUTOR INITIALIZED");

		log.info("Threads   : {}", threads);

		log.info("Chunk size: {}", CHUNK_SIZE);

		log.info("============================================================");
	}

	/*
	 * ================================================================ EXECUTOR
	 * SHUTDOWN ================================================================
	 */

	@PreDestroy
	public void shutdownExecutor() {

		if (ledgerExecutor != null) {

			ledgerExecutor.shutdown();

			log.info("GST Ledger executor shutdown requested");
		}
	}

	/*
	 * = MAIN LEDGER PROCESSOR == Example: action = TAX fr_dt = 2026-07-01 to_dt =
	 * 2026-07-31 TAX API: fr_dt=072026 to_dt=072026
	 */
	public String getLedgerForMultipleGSTN(String action, String fr_dt, String to_dt) {

		long startTime = System.currentTimeMillis();

		if (action == null || action.isBlank()) {

			return "Action is required";
		}
		LocalDate fromDate;
		LocalDate toDate;

		try {

			fromDate = LocalDate.parse(fr_dt, INPUT_FORMATTER);

			toDate = LocalDate.parse(to_dt, INPUT_FORMATTER);

		} catch (Exception e) {

			log.error("Invalid ledger date | from={} | to={}", fr_dt, to_dt, e);

			return "Invalid date format. Required yyyy-MM-dd";
		}

		if (fromDate.isAfter(toDate)) {

			return "Invalid date range";
		}

		/* ====FORMAT DATE FOR GST API ==== */

		String formattedFromDate;

		String formattedToDate;

		if ("TAX".equalsIgnoreCase(action)) {

			/* Example: 2026-07-01 -> 072026 2026-07-31 -> 072026 */

			formattedFromDate = fromDate.format(MONTH_YEAR_FORMATTER);

			formattedToDate = toDate.format(MONTH_YEAR_FORMATTER);

		} else {

			formattedFromDate = fromDate.format(DAY_MONTH_YEAR_FORMATTER);

			formattedToDate = toDate.format(DAY_MONTH_YEAR_FORMATTER);
		}

		/* === GET GSTINs FROM YOUR STORED PROCEDURE== */

		long procedureStart = System.currentTimeMillis();

		List<LedgerGstinProjection> gstinList = regularTaxpayerRepository.getGstinForLedgerProcedure(fr_dt, to_dt,
				action);

		long procedureTime = System.currentTimeMillis() - procedureStart;

		log.info("GSTIN procedure completed | count={} | time={} ms", gstinList == null ? 0 : gstinList.size(),
				procedureTime);

		if (gstinList == null || gstinList.isEmpty()) {

			log.info("No GSTIN records returned by ledger procedure | action={}", action);

			return "No GSTIN records found";
		}

		final int total = gstinList.size();

		/* START LOGGING */

		int totalBatches = (int) Math.ceil((double) total / CHUNK_SIZE);

		log.info("============================================================");

		log.info("GST LEDGER EXTRACTION STARTED");

		log.info("Action       : {}", action);

		log.info("Input From   : {}", fr_dt);

		log.info("Input To     : {}", to_dt);

		log.info("API From     : {}", formattedFromDate);

		log.info("API To       : {}", formattedToDate);

		log.info("GSTIN Count  : {}", total);

		log.info("Chunk Size   : {}", CHUNK_SIZE);

		log.info("Threads      : {}", configuredThreads);

		log.info("Total Batches: {}", totalBatches);

		log.info("============================================================");

		/* COUNTERS */

		int totalSuccess = 0;

		int totalFailed = 0;

		/* PROCESS BATCHES */

		for (int start = 0; start < total; start += CHUNK_SIZE) {

			int end = Math.min(start + CHUNK_SIZE, total);

			/* Projection chunk. */
			List<LedgerGstinProjection> chunk = gstinList.subList(start, end);

			int batchNumber = (start / CHUNK_SIZE) + 1;

			log.info("------------------------------------------------------------");

			log.info("STARTING BATCH {}/{} | GSTIN {} - {} of {}", batchNumber, totalBatches, start + 1, end, total);

			long batchStart = System.currentTimeMillis();

			/* Process current chunk */
			BatchResult result = processChunk(chunk, USERNAME, action, formattedFromDate, formattedToDate);

			totalSuccess += result.success();

			totalFailed += result.failed();

			long batchTime = System.currentTimeMillis() - batchStart;

			/* Calculate progress. */
			double progress = ((double) end / total) * 100.0;

			log.info("BATCH {}/{} COMPLETED | success={} | failed={} | processed={}/{} | progress={} % | time={} ms",
					batchNumber, totalBatches, result.success(), result.failed(), end, total,
					String.format("%.2f", progress), batchTime);

			/* Small pause between batches. */
			if (end < total) {

				sleepBetweenChunks();
			}
		}

		/*
		 * ============================================================ FINAL RESULT
		 * ============================================================
		 */

		long elapsed = System.currentTimeMillis() - startTime;

		log.info("============================================================");

		log.info("GST LEDGER EXTRACTION FINISHED");

		log.info("Action       : {}", action);

		log.info("Total        : {}", total);

		log.info("Success      : {}", totalSuccess);

		log.info("Failed       : {}", totalFailed);

		log.info("Elapsed      : {} ms", elapsed);

		log.info("============================================================");

		return "Ledger processing completed. " + "Total=" + total + ", Success=" + totalSuccess + ", Failed="
				+ totalFailed + ", Time=" + elapsed + " ms";
	}

	/*
	 * ================================================================ PROCESS ONE
	 * CHUNK ================================================================
	 */

	private BatchResult processChunk(List<LedgerGstinProjection> chunk, String userName, String action, String fromDate,
			String toDate) {

		AtomicInteger success = new AtomicInteger(0);

		AtomicInteger failed = new AtomicInteger(0);

		/*
		 * Only 500 futures are created at one time. The fixed executor controls actual
		 * concurrency.
		 */
		List<CompletableFuture<Void>> futures = new ArrayList<>(chunk.size());

		for (LedgerGstinProjection projection : chunk) {

			CompletableFuture<Void> future = CompletableFuture.runAsync(
					() -> processSingleGstin(projection, userName, action, fromDate, toDate, success, failed),
					ledgerExecutor);

			futures.add(future);
		}

		/*
		 * Wait until current batch is completed.
		 */
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		return new BatchResult(success.get(), failed.get());
	}

	/* PROCESS SINGLE GSTIN */
	private void processSingleGstin(LedgerGstinProjection projection, String userName, String action, String fromDate,
			String toDate, AtomicInteger success, AtomicInteger failed) {

		/*
		 * IMPORTANT:
		 *
		 * Your LedgerGstinProjection must expose the GSTIN.
		 *
		 * Expected method:
		 *
		 * projection.getGstin()
		 */
		String gstin = projection.getGstin();

		if (gstin == null || gstin.isBlank()) {

			failed.incrementAndGet();

			log.warn("Skipped empty GSTIN from projection");

			return;
		}

		try {

			long requestStart = System.currentTimeMillis();

			/*
			 * ACTUAL GST LEDGER API CALL
			 * 
			 * /ledgers?action=TAX &fr_dt=072026 &to_dt=072026
			 * &gstin=18AAOFH5631E1ZB&state_cd=18
			 */
			String response = commonServiceGstrLedgerImpl.getLedgerFromGstn(userName, action, gstin, fromDate, toDate);

			long requestTime = System.currentTimeMillis() - requestStart;

			/* SUCCESS */

			if (isSuccessfulResponse(response)) {

				success.incrementAndGet();

				log.info("GSTIN SUCCESS | gstin={} | action={} | time={} ms", gstin, action, requestTime);

			} else {

				failed.incrementAndGet();

				log.warn("GSTIN FAILED | gstin={} | action={} | time={} ms | response={}", gstin, action, requestTime,
						response);
			}

		} catch (Exception e) {

			failed.incrementAndGet();

			log.error("GSTIN ERROR | gstin={} | action={}", gstin, action, e);
		}
	}

	/* SUCCESS RESPONSE CHECK */

	private boolean isSuccessfulResponse(String response) {

		return response != null && response.contains("File saved successfully for GSTIN");
	}

	/* CHUNK DELAY */

	private void sleepBetweenChunks() {

		try {

			Thread.sleep(CHUNK_PAUSE_MILLIS);

		} catch (InterruptedException e) {

			Thread.currentThread().interrupt();

			log.warn("Interrupted between ledger batches");
		}
	}

	/* RESULT */
	private record BatchResult(int success, int failed) {
	}
}