package com.deloitte.service.support;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.deloitte.common.bean.GSTCommonResponseBean;
import com.deloitte.common.constant.Constants;
import com.deloitte.common.entity.APIDetails;
import com.deloitte.common.entity.GSTUserSession;
import com.deloitte.common.entity.MasterData;
import com.deloitte.returns.entity.LedgerDataJsonFile;
import com.deloitte.returns.entity.ReturnComparisonReportGstinJson;
import com.deloitte.returns.entity.type.LedgerCash.LedgerAttemptProjection;
import com.deloitte.returns.repository.common.LedgerDataJsonFileRepository;
import com.deloitte.returns.repository.registration.RegularTaxpayerRepository;
import com.deloitte.returns.service.AuthenticationHelper;
import com.deloitte.returns.service.GstUserSessionServices;
import com.deloitte.service.helper.REST.call.RestClientHelper;
import com.deloitte.service.impl.APIDetailsImpl;
import com.deloitte.service.impl.CommonServiceGstrLedgerImpl;
import com.deloitte.service.impl.MasterDataServiceImpl;
import com.deloitte.service.utility.procedure.LedgerGstinProjection;
import com.fasterxml.jackson.databind.JsonNode;

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

	private final LedgerDataJsonFileRepository ledgerDataJsonFileRepository;

	private final RegularTaxpayerRepository regularTaxpayerRepository;

	private final CommonServiceGstrLedgerImpl commonServiceGstrLedgerImpl;

	private final MasterDataServiceImpl masterDataService;

	private final GstUserSessionServices gstUserSessionServices;

	private final AuthenticationHelper authenticationHelper;

	private final RestClientHelper restClient;

	private final APIDetailsImpl apiDetailsImpl;

	/*
	 * == DATE FORMATTERS =============
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

		final long startTime = System.currentTimeMillis();

		// 1. VALIDATION
		if (action == null || action.isBlank()) {
			return "Action is required";
		}

		LocalDate fromDate;
		LocalDate toDate;

		try {
			fromDate = LocalDate.parse(fr_dt, INPUT_FORMATTER);
			toDate = LocalDate.parse(to_dt, INPUT_FORMATTER);
		} catch (Exception e) {

			log.error("Invalid ledger date | action={} | from={} | to={}", action, fr_dt, to_dt, e);

			return "Invalid date format. Required yyyy-MM-dd";
		}

		if (fromDate.isAfter(toDate)) {
			return "Invalid date range";
		}

		// 2. FORMAT DATE
		final String formattedFromDate;
		final String formattedToDate;

		if ("TAX".equalsIgnoreCase(action)) {

			// Example:
			// 2026-08-01 -> 082026
			// 2026-08-31 -> 082026

			formattedFromDate = fromDate.format(MONTH_YEAR_FORMATTER);

			formattedToDate = toDate.format(MONTH_YEAR_FORMATTER);

		} else {

			// CASH / ITC
			// Example:
			// 2026-08-01 -> 01-08-2026
			// 2026-08-31 -> 31-08-2026

			formattedFromDate = fromDate.format(DAY_MONTH_YEAR_FORMATTER);

			formattedToDate = toDate.format(DAY_MONTH_YEAR_FORMATTER);
		}

		final String storedFromDate = formattedFromDate;
		final String storedToDate = formattedToDate;

		log.info("============================================================");
		log.info("GST LEDGER EXTRACTION REQUEST");
		log.info("Action      : {}", action);
		log.info("Input From  : {}", fr_dt);
		log.info("Input To    : {}", to_dt);
		log.info("Stored From : {}", storedFromDate);
		log.info("Stored To   : {}", storedToDate);
		log.info("API From    : {}", formattedFromDate);
		log.info("API To      : {}", formattedToDate);
		log.info("Threads     : {}", configuredThreads);
		log.info("Chunk Size  : {}", CHUNK_SIZE);
		log.info("============================================================");

		// ============================================================
		// 3. GET GSTINS FROM STORED PROCEDURE
		// ============================================================

		long procedureStart = System.currentTimeMillis();

		List<LedgerGstinProjection> procedureResult = regularTaxpayerRepository.getGstinForLedgerProcedure(fr_dt, to_dt,
				action);

		long procedureTime = System.currentTimeMillis() - procedureStart;

		if (procedureResult == null || procedureResult.isEmpty()) {

			log.info("No GSTIN records returned by ledger procedure | action={} | time={} ms", action, procedureTime);

			return "No GSTIN records found";
		}

		log.info("GSTIN procedure completed | count={} | time={} ms", procedureResult.size(), procedureTime);

		// ============================================================
		// 4. REMOVE NULL / DUPLICATE GSTINS
		//
		// Very important for 4 lakh records.
		// We don't want to submit the same GSTIN multiple times.
		// ============================================================

		Map<String, LedgerGstinProjection> uniqueGstinMap = new LinkedHashMap<>();

		for (LedgerGstinProjection projection : procedureResult) {

			if (projection == null) {
				continue;
			}

			String gstin = projection.getGstin();

			if (gstin == null || gstin.isBlank()) {

				log.warn("Ignoring blank GSTIN | action={}", action);

				continue;
			}

			String normalizedGstin = gstin.trim().toUpperCase(Locale.ROOT);

			uniqueGstinMap.putIfAbsent(normalizedGstin, projection);
		}

		if (uniqueGstinMap.isEmpty()) {

			return "No valid GSTIN records found";
		}

		List<LedgerGstinProjection> uniqueGstinList = new ArrayList<>(uniqueGstinMap.values());

		log.info("GSTIN duplicate filtering completed | procedureCount={} | uniqueCount={}", procedureResult.size(),
				uniqueGstinList.size());

		// ============================================================
		// 5. BULK FILTER DATABASE RECORDS
		//
		// DO NOT execute 2 queries for every GSTIN.
		//
		// Old approach:
		//
		// 275,000 GSTIN
		// × 2 queries
		// = 550,000 DB queries
		//
		// New approach:
		//
		// one bulk query
		// then filter in memory.
		// ============================================================

		long filterStart = System.currentTimeMillis();

		List<LedgerAttemptProjection> existingRecords = ledgerDataJsonFileRepository.findLedgerStatusForGSTINs(action,
				storedFromDate, storedToDate);

		Map<String, LedgerAttemptProjection> statusMap = new HashMap<>();

		if (existingRecords != null) {

			for (LedgerAttemptProjection record : existingRecords) {

				if (record == null || record.getGstin() == null || record.getGstin().isBlank()) {

					continue;
				}

				String gstin = record.getGstin().trim().toUpperCase(Locale.ROOT);

				statusMap.put(gstin, record);
			}
		}

		List<LedgerGstinProjection> eligibleGstinList = new ArrayList<>(uniqueGstinList.size());

		int skippedSuccess = 0;
		int skippedMaxAttempt = 0;

		for (LedgerGstinProjection projection : uniqueGstinList) {

			String gstin = projection.getGstin().trim().toUpperCase(Locale.ROOT);

			LedgerAttemptProjection existing = statusMap.get(gstin);

			// --------------------------------------------------------
			// SUCCESS ALREADY EXISTS
			// --------------------------------------------------------

			if (existing != null && Boolean.TRUE.equals(existing.getIsSuccess())) {

				skippedSuccess++;
				continue;
			}

			// --------------------------------------------------------
			// MAX 4 ATTEMPTS
			// --------------------------------------------------------

			int attempt = 0;

			if (existing != null && existing.getDownloadAttempt() != null) {

				attempt = existing.getDownloadAttempt();
			}

			if (attempt >= 1) {

				skippedMaxAttempt++;
				continue;
			}

			eligibleGstinList.add(projection);
		}

		long filterTime = System.currentTimeMillis() - filterStart;

		log.info(
				"GSTIN FILTER COMPLETED | original={} | unique={} | eligible={} | skippedSuccess={} | skippedMaxAttempt={} | time={} ms",
				procedureResult.size(), uniqueGstinList.size(), eligibleGstinList.size(), skippedSuccess,
				skippedMaxAttempt, filterTime);

		// ============================================================
		// 6. NOTHING TO PROCESS
		// ============================================================

		if (eligibleGstinList.isEmpty()) {

			long elapsed = System.currentTimeMillis() - startTime;

			log.info("No eligible GSTINs | action={} | successSkipped={} | maxAttemptSkipped={} | elapsed={} ms",
					action, skippedSuccess, skippedMaxAttempt, elapsed);

			return "No eligible GSTINs. " + "Already Successful=" + skippedSuccess + ", Maximum Attempts Reached="
					+ skippedMaxAttempt + ", Time=" + elapsed + " ms";
		}

		// ============================================================
		// 7. PARALLEL PROCESSING
		//
		// THIS IS THE MAIN PERFORMANCE FIX.
		//
		// Old:
		//
		// processChunk()
		// WAIT
		// processChunk()
		// WAIT
		//
		// New:
		//
		// submit chunk 1
		// submit chunk 2
		// submit chunk 3
		// ...
		//
		// Executor workers process them concurrently.
		// ============================================================

		final int total = eligibleGstinList.size();

		final int totalBatches = (int) Math.ceil((double) total / CHUNK_SIZE);

		log.info("============================================================");
		log.info("GST LEDGER PARALLEL PROCESSING STARTED");
		log.info("Action              : {}", action);
		log.info("Total GSTIN         : {}", total);
		log.info("Chunk Size          : {}", CHUNK_SIZE);
		log.info("Configured Threads  : {}", configuredThreads);
		log.info("Total Chunks        : {}", totalBatches);
		log.info("Skipped Success     : {}", skippedSuccess);
		log.info("Skipped Max Attempt : {}", skippedMaxAttempt);
		log.info("============================================================");

		// ============================================================
		// 8. SUBMIT ALL CHUNKS TO EXECUTOR
		// ============================================================

		List<CompletableFuture<BatchResult>> futures = new ArrayList<>(totalBatches);

		AtomicInteger completedChunks = new AtomicInteger(0);

		AtomicInteger totalSuccess = new AtomicInteger(0);

		AtomicInteger totalFailed = new AtomicInteger(0);

		AtomicInteger totalProcessed = new AtomicInteger(0);

		for (int start = 0; start < total; start += CHUNK_SIZE) {

			final int chunkStart = start;

			final int chunkEnd = Math.min(start + CHUNK_SIZE, total);

			final int batchNumber = (start / CHUNK_SIZE) + 1;

			final List<LedgerGstinProjection> chunk = new ArrayList<>(eligibleGstinList.subList(chunkStart, chunkEnd));

			CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {

				long batchStart = System.currentTimeMillis();

				log.info("BATCH STARTED | batch={}/{} | records={} | thread={}", batchNumber, totalBatches,
						chunk.size(), Thread.currentThread().getName());

				try {

					BatchResult result = processChunk(chunk, USERNAME, action, formattedFromDate, formattedToDate);

					if (result != null) {

						totalSuccess.addAndGet(result.success());

						totalFailed.addAndGet(result.failed());
					}

					int processed = totalProcessed.addAndGet(chunk.size());

					int completed = completedChunks.incrementAndGet();

					long batchTime = System.currentTimeMillis() - batchStart;

					double progress = ((double) processed / total) * 100.0;

					log.info(
							"BATCH COMPLETED | batch={}/{} | success={} | failed={} | processed={}/{} | progress={} % | time={} ms | thread={}",
							batchNumber, totalBatches, result == null ? 0 : result.success(),
							result == null ? 0 : result.failed(), processed, total, String.format("%.2f", progress),
							batchTime, Thread.currentThread().getName());

					return result;

				} catch (Exception e) {

					completedChunks.incrementAndGet();

					totalFailed.addAndGet(chunk.size());

					log.error("BATCH FAILED | batch={}/{} | records={} | thread={}", batchNumber, totalBatches,
							chunk.size(), Thread.currentThread().getName(), e);

					return new BatchResult(0, chunk.size());
				}

			},

					ledgerExecutor);

			futures.add(future);
		}

		// ============================================================
		// 9. WAIT FOR ALL WORKERS
		// ============================================================

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		// ============================================================
		// 10. FINAL RESULT
		// ============================================================

		long elapsed = System.currentTimeMillis() - startTime;

		log.info("============================================================");
		log.info("GST LEDGER EXTRACTION FINISHED");
		log.info("Action              : {}", action);
		log.info("Total Eligible      : {}", total);
		log.info("Success             : {}", totalSuccess.get());
		log.info("Failed              : {}", totalFailed.get());
		log.info("Skipped Success     : {}", skippedSuccess);
		log.info("Skipped Max Attempt : {}", skippedMaxAttempt);
		log.info("Completed Chunks    : {}", completedChunks.get());
		log.info("Total Chunks        : {}", totalBatches);
		log.info("Elapsed             : {} ms", elapsed);
		log.info("============================================================");

		return "Ledger processing completed. " + "Total=" + total + ", Success=" + totalSuccess.get() + ", Failed="
				+ totalFailed.get() + ", SkippedSuccess=" + skippedSuccess + ", SkippedMaxAttempt=" + skippedMaxAttempt
				+ ", Time=" + elapsed + " ms";
	}

	/*
	 * ================================================================ PROCESS ONE
	 * CHUNK ================================================================
	 */

	private BatchResult processChunk(List<LedgerGstinProjection> chunk, String userName, String action, String fromDate,
			String toDate) {

		AtomicInteger success = new AtomicInteger(0);
		AtomicInteger failed = new AtomicInteger(0);

		log.info("PROCESS CHUNK STARTED | records={} | action={} | thread={}", chunk.size(), action,
				Thread.currentThread().getName());

		/*
		 * IMPORTANT:
		 *
		 * DO NOT create another CompletableFuture here.
		 *
		 * The caller has already submitted this chunk to ledgerExecutor.
		 *
		 * Therefore this method processes GSTINs directly.
		 *
		 * Example:
		 *
		 * 10 executor threads | +-- Batch 1 -> 500 GSTINs +-- Batch 2 -> 500 GSTINs +--
		 * Batch 3 -> 500 GSTINs ...
		 *
		 * So 10 GSTIN API calls can run concurrently.
		 */

		for (LedgerGstinProjection projection : chunk) {

			processSingleGstin(projection, userName, action, fromDate, toDate, success, failed);
		}

		log.info("PROCESS CHUNK COMPLETED | records={} | success={} | failed={} | thread={}", chunk.size(),
				success.get(), failed.get(), Thread.currentThread().getName());

		return new BatchResult(success.get(), failed.get());
	}

	/* PROCESS SINGLE GSTIN */
	private void processSingleGstin(LedgerGstinProjection projection, String userName, String action, String fromDate,
			String toDate, AtomicInteger success, AtomicInteger failed) {

		String gstin = projection.getGstin();

		if (gstin == null || gstin.isBlank()) {
			failed.incrementAndGet();
			log.warn("Skipped empty GSTIN from projection");
			return;
		}

		try {

			long requestStart = System.currentTimeMillis();

			log.info("BEFORE getLedgerFromGstn | gstin={} | action={} | thread={}", gstin, action,
					Thread.currentThread().getName());

			long gstnStart = System.currentTimeMillis();

			String response = commonServiceGstrLedgerImpl.getLedgerFromGstn(userName, action, gstin, fromDate, toDate);

			long requestTime = System.currentTimeMillis() - requestStart;

			log.info("AFTER getLedgerFromGstn | gstin={} | action={} | elapsed={} ms | response={}", gstin, action,
					requestTime, response);

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

			log.error("GSTIN ERROR | gstin={} | action={} | thread={}", gstin, action, Thread.currentThread().getName(),
					e);
		}
	}

	/* SUCCESS RESPONSE CHECK */

	private boolean isSuccessfulResponse(String response) {

		return response != null && response.contains("File saved successfully for GSTIN");
	}

	/* RESULT */
	private record BatchResult(int success, int failed) {
	}

	// ---------------------//
	public String getFailedLedgerExecute(String userName) {

		long startTime = System.currentTimeMillis();

		log.info("Started Failed Ledger Execute for user={}", userName);

		MasterData masterData = masterDataService.getMasterdatabyName(userName);

		if (masterData == null) {

			return "Master data not found";
		}

		GSTUserSession session = gstUserSessionServices.getUserSessionsByName(userName);

		if (session == null) {
			return "Session not authenticated";
		}

		ExecutorService executor = Executors.newFixedThreadPool(50);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failedCount = new AtomicInteger(0);

		try {

			while (true) {

				Pageable pageable = PageRequest.of(0, 500);

				Page<LedgerDataJsonFile> page = ledgerDataJsonFileRepository
						.findByIsSuccessNullOrIsSuccessFalseAndDownloadAttemptLessThan(2, pageable);

				if (page.isEmpty()) {

					log.info("No pending records found. Processing completed.");

					break;
				}

				List<LedgerDataJsonFile> records = page.getContent();

				log.info("Fetched {} records", records.size());

				List<CompletableFuture<Void>> futures = new ArrayList<>();

				for (LedgerDataJsonFile doc : records) {

					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

						try {

							
							commonServiceGstrLedgerImpl.getLedgerFromGstn(USERNAME, doc.getAction(), doc.getGstin(), doc.getFromDate(), doc.getToDate());

							successCount.incrementAndGet();

						} catch (Exception ex) {

							failedCount.incrementAndGet();

							log.error("Failed GSTIN={} From Date={}", doc.getGstin(), doc.getFromDate(), ex);

							doc.setIsProcessed(false);

							doc.setDownloadAttempt(doc.getDownloadAttempt() == 0 ? 1 : doc.getDownloadAttempt() + 1);

							ledgerDataJsonFileRepository.save(doc);
						}

					}, executor);

					futures.add(future);
				}

				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

				log.info("Batch Completed. Records Processed={}", records.size());
			}

		} catch (Exception ex) {

			log.error("Bulk processing failed", ex);

		} finally {

			executor.shutdown();

			try {

				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {

					executor.shutdownNow();
				}

			} catch (InterruptedException e) {

				executor.shutdownNow();

				Thread.currentThread().interrupt();
			}
		}

		long totalTime = System.currentTimeMillis() - startTime;

		log.info("Completed | Success={} | Failed={} | Time={} ms", successCount.get(), failedCount.get(), totalTime);

		return "Success=" + successCount.get() + ", Failed=" + failedCount.get() + ", Time(ms)=" + totalTime;
	}
	
	
	public String getFailedLedgerExecute(String userName, String action) {

		long startTime = System.currentTimeMillis();

		log.info("Started Failed Ledger Execute for user={}", userName);

		MasterData masterData = masterDataService.getMasterdatabyName(userName);

		if (masterData == null) {

			return "Master data not found";
		}

		GSTUserSession session = gstUserSessionServices.getUserSessionsByName(userName);

		if (session == null) {
			return "Session not authenticated";
		}

		ExecutorService executor = Executors.newFixedThreadPool(50);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failedCount = new AtomicInteger(0);

		try {

			while (true) {

				Pageable pageable = PageRequest.of(0, 500);

				Page<LedgerDataJsonFile> page = ledgerDataJsonFileRepository
						.findByActionAndIsSuccessNullOrIsSuccessFalseAndDownloadAttemptLessThan(action,2, pageable);

				if (page.isEmpty()) {

					log.info("No pending records found. Processing completed.");

					break;
				}

				List<LedgerDataJsonFile> records = page.getContent();

				log.info("Fetched {} records", records.size());

				List<CompletableFuture<Void>> futures = new ArrayList<>();

				for (LedgerDataJsonFile doc : records) {

					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

						try {

							
							commonServiceGstrLedgerImpl.getLedgerFromGstn(USERNAME, action, doc.getGstin(), doc.getFromDate(), doc.getToDate());

							successCount.incrementAndGet();

						} catch (Exception ex) {

							failedCount.incrementAndGet();

							log.error("Failed GSTIN={} From Date={}", doc.getGstin(), doc.getFromDate(), ex);

							doc.setIsProcessed(false);

							doc.setDownloadAttempt(doc.getDownloadAttempt() == 0 ? 1 : doc.getDownloadAttempt() + 1);

							ledgerDataJsonFileRepository.save(doc);
						}

					}, executor);

					futures.add(future);
				}

				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

				log.info("Batch Completed. Records Processed={}", records.size());
			}

		} catch (Exception ex) {

			log.error("Bulk processing failed", ex);

		} finally {

			executor.shutdown();

			try {

				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {

					executor.shutdownNow();
				}

			} catch (InterruptedException e) {

				executor.shutdownNow();

				Thread.currentThread().interrupt();
			}
		}

		long totalTime = System.currentTimeMillis() - startTime;

		log.info("Completed | Success={} | Failed={} | Time={} ms", successCount.get(), failedCount.get(), totalTime);

		return "Success=" + successCount.get() + ", Failed=" + failedCount.get() + ", Time(ms)=" + totalTime;
	}
	
	


	public void updateErrorRecordHim(ReturnComparisonReportGstinJson doc, String errorMessage,
			Queue<ReturnComparisonReportGstinJson> queue) {

		try {

			doc.setIsProcessed(false);
			doc.setCounterAttempt(doc.getCounterAttempt() + 1);
			queue.add(doc);

		} catch (Exception e) {
			log.error("Error updating record Gstin={}", doc.getGstin(), e);
		}
	}

}