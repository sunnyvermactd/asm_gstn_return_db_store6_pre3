package com.deloitte.controller;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deloitte.service.forest.ForestApiService;
import com.deloitte.service.forest.ForestExtractionService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/forest")
@Slf4j
public class ForestApiController {

	@Autowired
	private ForestApiService service;

	@Autowired
	private ForestExtractionService forestExtractionService;

	@GetMapping("/download")
	public ResponseEntity<String> download() {

		long startTime = System.currentTimeMillis();
		log.info("START :: Monthly data download triggered");

		try {
			service.downloadAndSaveMonthlyData();

			long endTime = System.currentTimeMillis();
			log.info("END :: Monthly data download completed | Time Taken={} ms", (endTime - startTime));

			return ResponseEntity.ok("Data downloaded and stored successfully");

		} catch (Exception ex) {
			log.error("ERROR :: Monthly data download failed", ex);
			return ResponseEntity.internalServerError().body("Download failed");
		}
	}

	//@Scheduled(cron = "0 20 10 * * *")
	@GetMapping("/download-save-daily-data")
	public ResponseEntity<String> downloadAndSaveDailyData() {

		long startTime = System.currentTimeMillis();
		log.info("START :: Daily scheduled download started");

		try {
			service.downloadAndSaveDailyData();

			long endTime = System.currentTimeMillis();
			log.info("END :: Daily scheduled download completed | Time Taken={} ms", (endTime - startTime));

			return ResponseEntity.ok("Daily data downloaded successfully");

		} catch (Exception ex) {
			log.error("ERROR :: Daily scheduled download failed", ex);
			return ResponseEntity.internalServerError().body("Daily download failed");
		}
	}

	@PostMapping("/extract")
	public ResponseEntity<String> startExtraction(
			@RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
			@RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

		long startTime = System.currentTimeMillis();
		log.info("START :: Extraction started | From={} To={}", start, end);

		try {
			forestExtractionService.startExtraction(start, end);

			long endTime = System.currentTimeMillis();
			log.info("END :: Extraction completed | Time Taken={} ms | From={} To={}", (endTime - startTime), start,
					end);

			return ResponseEntity.ok("Extraction started successfully");

		} catch (Exception ex) {
			log.error("ERROR :: Extraction failed | From={} To={}", start, end, ex);
			return ResponseEntity.internalServerError().body("Extraction failed");
		}
	}
}