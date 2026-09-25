package com.deloitte.controller;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.deloitte.returns.service.GstUserSessionServices;
import com.deloitte.service.impl.ArnHandlerForRegistration;
import com.deloitte.service.impl.GstinExcelService;
import com.deloitte.service.support.GstinServiceRegistration;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("/common/registration")
@Log4j2
@RequiredArgsConstructor
public class RegistrationController {

	private final GstinServiceRegistration gstinService;

	private final GstinExcelService gstinExcelService;

	private final ArnHandlerForRegistration arnHandlerForRegistration;

	private final GstUserSessionServices gstUserSessionServices;

	private static final String USERNAME = "GSTG2G18";

	@GetMapping("/complete-registration-automation-final-verdict")
	public ResponseEntity<String> completeRegistrationAutomationFinalVerdict() {

		log.info("=====================================================");
		log.info("▶️ COMPLETE REGISTRATION AUTOMATION STARTED");
		log.info("=====================================================");

		long startTime = System.currentTimeMillis();

		try {

			// SESSION VALIDATION
			if (gstUserSessionServices.isSessionExpired(USERNAME)) {

				log.error("❌ SESSION EXPIRED BEFORE 6 HOUR");

				return ResponseEntity.badRequest().body("SESSION EXPIRED BEFORE 6 HOUR");
			}

			// STEP-1 : Complete Registration Automation
			log.info("▶️ STEP-1 : executeCompleteAutomation STARTED");

			String automationResponse = gstinService.executeCompleteAutomation();

			log.info("✅ STEP-1 : executeCompleteAutomation COMPLETED");

			// STEP-2 : Retry Alert Exception
			log.info("▶️ STEP-2 : retryAlertException STARTED");

			String retryResponse = gstinService.retryAlertException();

			log.info("✅ STEP-2 : retryAlertException COMPLETED");

			long totalTime = System.currentTimeMillis() - startTime;

			log.info("=====================================================");
			log.info("🏁 COMPLETE REGISTRATION AUTOMATION FINISHED");
			log.info("⏱️ TOTAL TIME = {} ms", totalTime);
			log.info("=====================================================");

			String finalResponse = automationResponse + "\n\n----------------------------------\n" + retryResponse;

			return ResponseEntity.ok(finalResponse);

		} catch (Exception ex) {

			long totalTime = System.currentTimeMillis() - startTime;

			log.error("❌ COMPLETE REGISTRATION AUTOMATION FAILED");
			log.error("⏱️ FAILED AFTER = {} ms", totalTime, ex);

			return ResponseEntity.internalServerError().body("COMPLETE AUTOMATION FAILED : " + ex.getMessage());
		}
	}

	// =========================================================
	// COMPLETE AUTOMATION FLOW
	// 1. ALERT API-->case-alert-automatically
	// 2. ARN HANDLER-->arn-handler-automatically
	// 3. REGISTRATION API-->all-registration-automatically
	// =========================================================

	@GetMapping("/complete-registration-automation")
	public ResponseEntity<String> completeRegistrationAutomation() {

		log.info("=====================================================");
		log.info("▶️ COMPLETE REGISTRATION AUTOMATION STARTED");
		log.info("=====================================================");

		long startTime = System.currentTimeMillis();

		try {
			if (gstUserSessionServices.isSessionExpired(USERNAME)) {

				log.error("❌ SESSION EXPIRED BEFORE 6 HOUR");

				return ResponseEntity.badRequest().body("SESSION EXPIRED BEFORE 6 HOUR");
			}

			String finalResponse = gstinService.executeCompleteAutomation();

			long totalTime = System.currentTimeMillis() - startTime;

			log.info("=====================================================");
			log.info("🏁 COMPLETE REGISTRATION AUTOMATION COMPLETED");
			log.info("⏱️ TOTAL TIME={} ms", totalTime);
			log.info("=====================================================");

			return ResponseEntity.ok(finalResponse);

		} catch (Exception ex) {

			long totalTime = System.currentTimeMillis() - startTime;

			log.error("❌ COMPLETE AUTOMATION FAILED");
			log.error("⏱️ FAILED AFTER={} ms", totalTime, ex);

			return ResponseEntity.internalServerError().body("COMPLETE AUTOMATION FAILED : " + ex.getMessage());
		}
	}

	// startDateTime=2026-06-15 00:00:000&
	// endDateTime=2026-06-16 00:00:00
	@GetMapping("/case-alert-by-date-range-for-exception")
	public String processAlertByDateRange(
			@RequestParam("startDateTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime currentStartDateTime,

			@RequestParam("endDateTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime finalEndDateTime)
			throws UnsupportedEncodingException {

		log.info("Received request to process ALERT from {} to {}", currentStartDateTime, finalEndDateTime);

		return gstinService.processAlertByDateRange(currentStartDateTime, finalEndDateTime);
	}

	@GetMapping("/retry-alert-exception")
	public String retryAlertException() {

		log.info("Received request to retry ALERT exceptions");

		String response = gstinService.retryAlertException();

		log.info("Retry ALERT exception completed");

		return response;
	}

	@GetMapping("/case-alert-automatically")
	public String getCrnForCaseType() throws UnsupportedEncodingException {

		log.info("Received request to fetch ALERT automatically");

		String response = gstinService.processAlertAutomatically();

		log.info("ALERT fetch completed successfully");

		return response;
	}

	@GetMapping("/arn-handler-automatically")
	public String arnHanddler() throws UnsupportedEncodingException {

		log.info("Received request to fetch ARN automatically");

		String response = arnHandlerForRegistration.processJson();

		log.info("ALERT fetch completed successfully");

		return response;
	}

	@GetMapping("/all-registration-automatically")
	public String registrationHanddler() throws UnsupportedEncodingException {

		log.info("Received request to fetch ALERT automatically");

		String response = arnHandlerForRegistration.processRegistration();

		log.info("ALERT fetch completed successfully");

		return response;
	}

	// not in use please don't use these below

	// ===========Fetch CRN based on case type and month range====//
	@GetMapping("/case-alert")
	public String getCrnForCaseType(@RequestParam int year, @RequestParam int startMonth, @RequestParam int endMonth)
			throws UnsupportedEncodingException {

		log.info("Received request to fetch CRN |  year={} startMonth={} endMonth={}", year, startMonth, endMonth);

		String response = gstinService.processDateAccordingToStartAndEndMonth(year, startMonth, endMonth);

		log.info("ALERT fetch completed successfully for year={}", year);

		return response;
	}

	// API 2: Process Excel from server path
	@PostMapping("/process")
	public ResponseEntity<String> processExcelFromPath(@RequestParam String filePath) {

		try {

			gstinExcelService.processCsv(filePath);

			return ResponseEntity.ok("✅ Excel processed successfully");

		} catch (Exception e) {

			return ResponseEntity.internalServerError().body("❌ Error processing file: " + e.getMessage());
		}
	}

	/**
	 * 1️⃣ Download registration data from GSTN and save into folder + DB
	 */
	@GetMapping("/gstn/{identityType}")
	public ResponseEntity<String> downloadFromGstn(@PathVariable String identityType) {

		return executeWithLogging(identityType, "downloadFromGstn",
				() -> gstinService.getAllAndSaveIntoDbViaGstin(identityType));
	}

	/**
	 * 2️⃣ Process JSON files and save into database
	 */
	@GetMapping("/process/{identityType}")
	public ResponseEntity<String> processRegistration(@PathVariable String identityType) {

		return executeWithLogging(identityType, "processRegistrationJson",
				() -> gstinService.processRegistrationJson(identityType));
	}

	/**
	 * Common logging wrapper for controller APIs
	 */
	private ResponseEntity<String> executeWithLogging(String identityType, String apiName,
			Supplier<String> serviceCall) {

		log.info("▶️ [START] {} | IdentityType={}", apiName, identityType);

		long startTime = System.currentTimeMillis();

		String response = serviceCall.get();

		long timeTaken = System.currentTimeMillis() - startTime;

		log.info("🏁 [END] {} | IdentityType={} | Response={} | TimeTaken={} ms", apiName, identityType, response,
				timeTaken);

		return ResponseEntity.ok(response);
	}
	//
	// GET /common/registration/gstn/normal
	// GET /common/registration/gstn/tds
	// GET /common/registration/gstn/tcs
	// GET /common/registration/gstn/composition

	// GET /common/registration/process/normal
	// GET /common/registration/process/tds
	// GET /common/registration/process/tcs
	// GET /common/registration/process/composition

	@PostMapping("/upload")
	public ResponseEntity<String> uploadExcel(@RequestParam("file") MultipartFile file) {

		try {

			gstinExcelService.processExcel(file);

			return ResponseEntity.ok("✅ Excel data imported successfully");

		} catch (Exception e) {

			e.printStackTrace();
			return ResponseEntity.internalServerError().body("❌ Error processing Excel file: " + e.getMessage());
		}
	}

}