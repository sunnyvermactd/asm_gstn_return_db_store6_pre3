package com.deloitte.controller;

import java.io.UnsupportedEncodingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deloitte.returns.service.GstUserSessionServices;
import com.deloitte.service.impl.CommonCrnServiceImpl;
import com.deloitte.service.impl.CrnDetailMigrationService;
import com.deloitte.service.support.crn.CommonCrnServicePreImpl;
import com.deloitte.service.utility.procedure.CrnProcedureService;

import lombok.extern.log4j.Log4j2;

@Log4j2
@RestController
@RequestMapping("/api/crn")
public class CrnController {

	@Autowired
	private CommonCrnServiceImpl commonCrnServiceImpl;

	@Autowired
	private CommonCrnServicePreImpl commonCrnServicePreImpl;

	@Autowired
	private CrnProcedureService crnProcedureService;

	@Autowired
	protected GstUserSessionServices gstUserSessionServices;

	@Autowired
	private CrnDetailMigrationService crnDetailMigrationService;
	
	@GetMapping("/process-crn-detail-migration")
	public void processCrnDetailMigrationService() {
		crnDetailMigrationService.migrateFyData();
	}
	
	
	private static final String USERNAME = "GSTG2G18";

	@GetMapping("/process-crn-scheduler")
	public void processCrnScheduler() {

		log.info("========== CRN Scheduler Started ==========");

		try {

			// Session Validation
			if (gstUserSessionServices.isSessionExpired(USERNAME)) {
				log.error("SESSION EXPIRED BEFORE 6 HOUR");
				return;
			}

			// STEP 1
			log.info("Starting Automatic CRN Processing...");
			String response1 = commonCrnServiceImpl.processCrnAutomatically();
			log.info("Automatic CRN Processing Completed: {}", response1);

			// STEP 2 (starts only after STEP 1 completes)
			log.info("Starting Pending CRN Processing...");
			String response2 = commonCrnServicePreImpl.processPendingCrns();
			log.info("Pending CRN Processing Completed: {}", response2);

		} catch (Exception e) {
			log.error("CRN Scheduler Failed", e);
		}

		log.info("========== CRN Scheduler Finished ==========");
	}

	@GetMapping("/process-crn-automatically")
	public String processCrnAutomatically() throws UnsupportedEncodingException {

		log.info("Download request processCrnAutomatically");

		// SESSION VALIDATION
		if (gstUserSessionServices.isSessionExpired(USERNAME)) {

			log.error("❌ SESSION EXPIRED BEFORE 6 HOUR");

			return "SESSION EXPIRED BEFORE 6 HOUR";
		}

		String response = commonCrnServiceImpl.processCrnAutomatically();

		log.info("Download completed successfully for processCrnAutomatically eND");

		return response;
	}

	@GetMapping("/process-pending")
	public ResponseEntity<String> processPendingCrn() {

		long startTime = System.currentTimeMillis();

		log.info("============== CRN Processing Started ==============");

		try {
			// SESSION VALIDATION
			if (gstUserSessionServices.isSessionExpired(USERNAME)) {

				log.error("❌ SESSION EXPIRED BEFORE 6 HOUR");

				return ResponseEntity.badRequest().body("SESSION EXPIRED BEFORE 6 HOUR");
			}
			String response = commonCrnServicePreImpl.processPendingCrns();

			long endTime = System.currentTimeMillis();

			log.info("============== CRN Processing Completed ==============");
			log.info("Total execution time : {} ms", (endTime - startTime));

			return ResponseEntity.ok(response);

		} catch (Exception e) {

			log.error("CRN processing failed", e);

			return ResponseEntity.internalServerError().body("CRN processing failed: " + e.getMessage());
		}
	}

	/**
	 * SINGLE CLICK COMPLETE FLOW
	 * 
	 * STEP 1 -> Download CRN Data STEP 2 -> Execute PostgreSQL Procedure STEP 3 ->
	 * Process Pending CRNs
	 */
	@GetMapping("/complete-process")
	public ResponseEntity<String> completeProcess(@RequestParam String username, @RequestParam String startDateTime,
			@RequestParam String endDateTime, @RequestParam String caseType) throws UnsupportedEncodingException {

		long totalStartTime = System.currentTimeMillis();

		log.info("=======================================================");
		log.info("🚀 COMPLETE CRN PROCESS STARTED");
		log.info("=======================================================");

		log.info("Input Parameters | username={} | caseType={} | startDateTime={} | endDateTime={}", username, caseType,
				startDateTime, endDateTime);

		try {

			// =====================================================
			// STEP 1 : DOWNLOAD CRN DATA
			// =====================================================

			log.info("-------------------------------------------------------");
			log.info("▶️ STEP 1 : DOWNLOADING CRN DATA");
			log.info("-------------------------------------------------------");

			long step1Start = System.currentTimeMillis();

			String downloadResponse = commonCrnServiceImpl.getCrnListByStartAndEndTime(startDateTime, endDateTime);

			long step1End = System.currentTimeMillis();

			log.info("✅ STEP 1 COMPLETED");
			log.info("Download Response = {}", downloadResponse);
			log.info("STEP 1 Execution Time = {} ms", (step1End - step1Start));

			// =====================================================
			// STEP 2 : EXECUTE PROCEDURE
			// =====================================================

			log.info("-------------------------------------------------------");
			log.info("▶️ STEP 2 : EXECUTING POSTGRESQL PROCEDURE");
			log.info("-------------------------------------------------------");

			long step2Start = System.currentTimeMillis();

			String procedureResponse = crnProcedureService.executeProcedureFromDatabase();

			long step2End = System.currentTimeMillis();

			log.info("✅ STEP 2 COMPLETED");
			log.info("Procedure Response = {}", procedureResponse);
			log.info("STEP 2 Execution Time = {} ms", (step2End - step2Start));

			// =====================================================
			// STEP 3 : PROCESS PENDING CRNs
			// =====================================================

			log.info("-------------------------------------------------------");
			log.info("▶️ STEP 3 : PROCESSING PENDING CRNs");
			log.info("-------------------------------------------------------");

			long step3Start = System.currentTimeMillis();

			String processResponse = commonCrnServicePreImpl.processPendingCrns();

			long step3End = System.currentTimeMillis();

			log.info("✅ STEP 3 COMPLETED");
			log.info("Process Response = {}", processResponse);
			log.info("STEP 3 Execution Time = {} ms", (step3End - step3Start));

			// =====================================================
			// FINAL SUCCESS RESPONSE
			// =====================================================

			long totalEndTime = System.currentTimeMillis();

			log.info("=======================================================");
			log.info("✅ COMPLETE CRN PROCESS FINISHED SUCCESSFULLY");
			log.info("⏱️ TOTAL EXECUTION TIME = {} ms", (totalEndTime - totalStartTime));
			log.info("=======================================================");

			String finalResponse = """
					COMPLETE PROCESS EXECUTED SUCCESSFULLY

					STEP 1 : DOWNLOAD COMPLETED
					STEP 2 : PROCEDURE EXECUTED
					STEP 3 : PENDING CRNs PROCESSED
					""";

			return ResponseEntity.ok(finalResponse);

		} catch (Exception ex) {

			long totalEndTime = System.currentTimeMillis();

			log.error("=======================================================");
			log.error("🔥 COMPLETE CRN PROCESS FAILED");
			log.error("⏱️ TOTAL EXECUTION TIME = {} ms", (totalEndTime - totalStartTime));
			log.error("ERROR = {}", ex.getMessage(), ex);
			log.error("=======================================================");

			return ResponseEntity.internalServerError().body("Complete CRN Process Failed : " + ex.getMessage());
		}
	}

	// ===========Download CRN data based on user name and time range====//
	@GetMapping("/download")
	public String downloadCrnData(@RequestParam String username, @RequestParam String startDateTime,
			@RequestParam String endDateTime, @RequestParam String caseType) throws UnsupportedEncodingException {

		log.info("Download request received | username={} caseType={} startDateTime={} endDateTime={}", username,
				caseType, startDateTime, endDateTime);

		String response = commonCrnServiceImpl.getCrnListByStartAndEndTime(startDateTime, endDateTime);

		log.info("Download completed successfully for user={}", username);

		return response;
	}

	@GetMapping("/execute-procedure")
	public ResponseEntity<String> executeProcedure() {

		String response = crnProcedureService.executeProcedureFromDatabase();

		return ResponseEntity.ok(response);
	}

	// caseType=
	// ADJDT,ADJGP,ADJND,ADJNF,ADJPA,ADJAT,ADJRA,ADJSA,ADJSR,ADJUR,ADJVP,ADJAE,APPEL,Rfund,ARAPA,ARARA,ARARF,RCPID,RCMOR,APLHA,RVORD,APLTD,APPEAL

	// schema name=refund_details
	// schema name=refund
	//

	// ADJDT,ADJGP,ADJND,ADJNF,ADJPA,ADJSA,ADJSR,APPEL
	// http://localhost:8018/refund/crn/downloadAgain?year=2024&startMonth=09&endMonth=12
	// http://localhost:8018/refund/crn/downloadAgainData?year=2024&startMonth=04&endMonth=12&caseType=ADJDT

//	@GetMapping("/saveDataFromJsonToDb")
//	public String saveDataFromJsonToDb() throws IOException {
//		String info = commonCrnServiceImpl.saveDataFromJsonToDbCommon();
//		return "Data imported successfully from " + info;
//	}
//
//	@GetMapping("/extractJsonFileNameToDbPathManual")
//	public String extractJsonFileNameToDbPathManual(@RequestParam String directoryPath,
//			@RequestParam(name = "application") String application) {
//		return commonCrnServiceImpl.extractJsonFileNameToDbManual(directoryPath, application);
//
//	}
	// tested
	// ADJDT,
	// =========== Download Again + Process Pending Automatically ========== //

	// ===========Fetch CRN based on case type and month range====//
	@GetMapping("/case-type")
	public String getCrnForCaseType(@RequestParam String caseType, @RequestParam int year, @RequestParam int startMonth,
			@RequestParam int endMonth) throws UnsupportedEncodingException {

		log.info("Received request to fetch CRN | caseType={} year={} startMonth={} endMonth={}", caseType, year,
				startMonth, endMonth);

		String response = commonCrnServiceImpl.processDateAccordingToStartAndEndMonth(year, startMonth, endMonth);

		log.info("CRN fetch completed successfully for caseType={}", caseType);

		return response;
	}

	@GetMapping("/download-again")
	public ResponseEntity<String> downloadAgainData() {

		long overallStartTime = System.currentTimeMillis();

		log.info("==================================================================");
		log.info("▶️ [CRN DOWNLOAD PROCESS STARTED]");
		log.info("==================================================================");

		try {

			// ================= DOWNLOAD PROCESS =================

			long downloadStartTime = System.currentTimeMillis();

			log.info("📡 Step-1 : Starting CRN historical download process");

			String downloadResponse = commonCrnServiceImpl.processCrnData();

			long downloadEndTime = System.currentTimeMillis();

			log.info("✅ Step-1 Completed : CRN historical download completed successfully");
			log.info("⏱ Download Process Time Taken = {} ms", (downloadEndTime - downloadStartTime));

			// ================= PENDING PROCESS =================

			long pendingStartTime = System.currentTimeMillis();

			log.info("📡 Step-2 : Starting pending CRN processing");

			String pendingResponse = commonCrnServicePreImpl.processPendingCrns();

			long pendingEndTime = System.currentTimeMillis();

			log.info("✅ Step-2 Completed : Pending CRN processing completed successfully");
			log.info("⏱ Pending Process Time Taken = {} ms", (pendingEndTime - pendingStartTime));

			// ================= FINAL LOG =================

			long overallEndTime = System.currentTimeMillis();

			log.info("==================================================================");
			log.info("🏁 [COMPLETE CRN FLOW FINISHED SUCCESSFULLY]");
			log.info("⏱ Total Execution Time = {} ms", (overallEndTime - overallStartTime));
			log.info("==================================================================");

			String finalResponse = "CRN Download Completed Successfully.\n"
					+ "Pending CRN Processing Completed Successfully.\n\n" + "Download Response : " + downloadResponse
					+ "\n" + "Pending Response : " + pendingResponse;

			return ResponseEntity.ok(finalResponse);

		} catch (Exception e) {

			long overallEndTime = System.currentTimeMillis();

			log.error("==================================================================");
			log.error("❌ [CRN COMPLETE FLOW FAILED]");
			log.error("⏱ Total Time Before Failure = {} ms", (overallEndTime - overallStartTime));
			log.error("❌ Error Message = {}", e.getMessage(), e);
			log.error("==================================================================");

			return ResponseEntity.internalServerError().body("CRN process failed : " + e.getMessage());
		}
	}

}
