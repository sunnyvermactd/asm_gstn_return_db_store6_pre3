package com.deloitte.controller;

import java.util.function.Supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deloitte.service.support.CommonControllerGstrUtilityImpl;
import com.deloitte.service.support.CommonControllerGstrUtilityImplOptimized;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("/common/ledger")
@Log4j2
@RequiredArgsConstructor
public class LedgerController {

	final CommonControllerGstrUtilityImpl commonControllerGstrUtilityImpl;

	final CommonControllerGstrUtilityImplOptimized commonControllerGstrUtilityImplOptimized;

	@GetMapping("/schedule-ledger-on-automatic")
	public ResponseEntity<String> scheduleLedgerOnAutomatic(@RequestParam String action, @RequestParam String fr_dt,
			@RequestParam String to_dt) {

		long start = System.currentTimeMillis();

		log.info("=================================================");
		log.info("▶ LEDGER SCHEDULER API STARTED");
		log.info("📌 action={} | fr_dt={} | to_dt={}", action, fr_dt, to_dt);
		log.info("=================================================");

		try {

			String response = commonControllerGstrUtilityImpl.getLedgerForMultipleGSTNAutomatically(action, fr_dt,
					to_dt);

			long end = System.currentTimeMillis();

			log.info("=================================================");
			log.info("✅ LEDGER SCHEDULER API COMPLETED");
			log.info("⏱ Total Time={} ms", (end - start));
			log.info("=================================================");

			return ResponseEntity.ok(response);

		} catch (Exception e) {

			log.error("❌ Controller Exception", e);

			return ResponseEntity.internalServerError().body("FAILED : " + e.getMessage());
		}
	}

	@GetMapping("/schedule-ledger-function-depedent")
	public ResponseEntity<String> scheduleLedgerFunctionDependent(@RequestParam String action,
			@RequestParam String fr_dt, @RequestParam String to_dt) {

		long start = System.currentTimeMillis();

		log.info("=================================================");
		log.info("▶ LEDGER SCHEDULER API STARTED");
		log.info("📌 action={} | fr_dt={} | to_dt={}", action, fr_dt, to_dt);
		log.info("=================================================");

		try {

			String response = commonControllerGstrUtilityImplOptimized.getLedgerForMultipleGSTN(action, fr_dt,
					to_dt);

			long end = System.currentTimeMillis();

			log.info("=================================================");
			log.info("✅ LEDGER SCHEDULER API COMPLETED");
			log.info("⏱ Total Time={} ms", (end - start));
			log.info("=================================================");

			return ResponseEntity.ok(response);

		} catch (Exception e) {

			log.error("❌ Controller Exception", e);

			return ResponseEntity.internalServerError().body("FAILED : " + e.getMessage());
		}
	}

	// http://localhost:8019/common/gstr/scheduleLedgerCashForSingleGSTN?action=CASH&fr_dt=01-04-2023&to_dt=31-03-2024
	// http://localhost:8020/common/gstr/scheduleLedgerCashForSingleGSTN?action=TAX&fr_dt=042023&to_dt=032024

	// http://localhost:8019/common/gstr/scheduleLedger?action=ITC&fr_dt=01-04-2023&to_dt=31-03-2024
	// CASH TAX ITC NRTN

	// http://localhost:8020/common/gstr/scheduleLedger?action=CASH&fr_dt=01-04-2023&to_dt=31-03-2024
	// https://boapi.internal.gst.gov.in/govtapi/v0.3/ledgers?fr_dt=042025&to_dt=032026&action=TAX&gstin=18ADIPL6822C1ZN
	// http://localhost:8020/common/gstr/scheduleLedgerCashForSingleGSTN?action=TAX&fr_dt=042023&to_dt=032024

	@GetMapping("/single")
	public ResponseEntity<String> singleLedger(@RequestParam String action, @RequestParam String gstin,
			@RequestParam String fr_dt, @RequestParam String to_dt) {

		return executeWithLogging("singleLedger", gstin,
				() -> commonControllerGstrUtilityImpl.getLedgerFromGstn(action, gstin, fr_dt, to_dt));
	}

	@GetMapping("/schedule")
	public ResponseEntity<String> scheduleLedger(@RequestParam String action, @RequestParam String fr_dt,
			@RequestParam String to_dt) {

		return executeWithLogging("scheduleLedger", action,
				() -> commonControllerGstrUtilityImpl.getLedgerForMultipleGSTN(action, fr_dt, to_dt));
	}

	private ResponseEntity<String> executeWithLogging(String apiName, String application, Supplier<String> supplier) {

		log.info("▶️ [START] {} | Application={}", apiName, application);
		long startTime = System.currentTimeMillis();

		try {
			String response = supplier.get();
			long timeTaken = System.currentTimeMillis() - startTime;

			log.info("🏁 [END] {} | Application={} | Response={} | TimeTaken={} ms", apiName, application, response,
					timeTaken);

			return ResponseEntity.ok(response);

		} catch (Exception ex) {
			log.error("❌ [ERROR] {} | Application={}", apiName, application, ex);
			return ResponseEntity.internalServerError().body("Internal Server Error while executing " + apiName);
		}
	}

}
