package com.deloitte.controller;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deloitte.common.bean.EwayBillComparisonResponse;
import com.deloitte.common.bean.EwayBillViewResponse;
import com.deloitte.returns.service.GstUserSessionServices;
import com.deloitte.service.impl.EwayBillApiService;
import com.deloitte.service.impl.EwayBillReportServiceImpl;
import com.deloitte.service.impl.RegistrationServiceImpl;
import com.deloitte.service.support.CommonServiceGstrUtilityImpl;
import com.deloitte.service.support.GstinServiceRegistration;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("/common/gstr")
@Log4j2
@RequiredArgsConstructor
public class ReturnController {

	private final CommonServiceGstrUtilityImpl commonControllerGstrUtilityImpl;

	private final GstinServiceRegistration gstinServiceRegistration;

	private final GstUserSessionServices gstUserSessionServices;

	private final RegistrationServiceImpl registrationServiceImpl;

	private final EwayBillApiService eWayBillApiService;

	private final EwayBillReportServiceImpl ewayBillReportServiceImpl;

	private static final String USERNAME = "GSTG2G18";

	private ResponseEntity<String> scheduleWithLogging(String application, String apiName) {

		long startTime = System.currentTimeMillis();
		log.info("🚀 [START] API={} | Application={}", apiName, application);

		try {

			if (gstUserSessionServices.isSessionExpired(USERNAME)) {

				log.error("❌ SESSION EXPIRED BEFORE 6 HOUR");

				return ResponseEntity.badRequest().body("SESSION EXPIRED BEFORE 6 HOUR");
			}

			// Step 1: Schedule Download
			log.info("📥 [STEP-1] Starting schedule download | Application={}", application);
			String response = commonControllerGstrUtilityImpl.scheduleDownload(application);
			log.info("✅ [STEP-1] Schedule download completed | Response={}", response);

			return ResponseEntity.ok(response);

		} catch (Exception ex) {
			long timeTaken = System.currentTimeMillis() - startTime;

			log.error("❌ [ERROR] API={} | Application={} | TimeTaken={} ms | Message={}", apiName, application,
					timeTaken, ex.getMessage(), ex);

			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Failed to process request for application=" + application);
		}
	}

	// ========================== 1️⃣ DOWNLOAD APIs ==========================

	@GetMapping("/CM8") // ready
	public ResponseEntity<String> scheduleCmp08Download() {
		return scheduleWithLogging("CM8", "scheduleCmp08Download");
	}

	public ResponseEntity<String> scheduleItc02Download() {
		return scheduleWithLogging("ITC02", "scheduleItc02Download");
	}

	@GetMapping("/payment") // ready
	public ResponseEntity<String> schedulePaymentDownload() {
		return scheduleWithLogging("payment", "schedulePaymentDownload");
	}

	@GetMapping("/recon")
	public ResponseEntity<String> scheduleReconDataDownload() {
		return scheduleWithLogging("recon", "scheduleReconDataDownload");
	}

	@GetMapping("/R1") // ready
	public ResponseEntity<String> scheduleGstr1Download() {
		return scheduleWithLogging("R1", "scheduleGstr1Download");
	}

	@GetMapping("/R1A") // no need
	public ResponseEntity<String> scheduleGstr1aDownload() {
		return scheduleWithLogging("R1A", "scheduleGstr1aDownload");
	}

	@GetMapping("/R2B") // ready
	public ResponseEntity<String> scheduleGstr2bDownload() {
		return scheduleWithLogging("R2B", "scheduleGstr2bDownload");
	}

	@GetMapping("/R3B") // ready
	public ResponseEntity<String> scheduleGstr3bDownload() {
		return scheduleWithLogging("R3B", "scheduleGstr3bDownload");
	}

	@GetMapping("/R4") // ready
	public ResponseEntity<String> scheduleGstr4Download() {
		return scheduleWithLogging("R4", "scheduleGstr4Download");
	}

	@GetMapping("/R5") // ready not working
	public ResponseEntity<String> scheduleGstr5Download() {
		return scheduleWithLogging("R5", "scheduleGstr5Download");
	}

	@GetMapping("/R6") // ready
	public ResponseEntity<String> scheduleGstr6Download() {
		return scheduleWithLogging("R6", "scheduleGstr6Download"); // Completed
	}

	@GetMapping("/R7") // ready--
	public ResponseEntity<String> scheduleGstr7Download() {
		return scheduleWithLogging("R7", "scheduleGstr7Download");
	}

	@GetMapping("/R8") // ready
	public ResponseEntity<String> scheduleGstr8Download() {
		return scheduleWithLogging("R8", "scheduleGstr8Download");// Completed
	}

	@GetMapping("/R9") // ready--
	public ResponseEntity<String> scheduleGstr9Download() {
		return scheduleWithLogging("R9", "scheduleGstr9Download");
	}

	@GetMapping("/R9A") // ready
	public ResponseEntity<String> scheduleGstr9aDownload() {
		return scheduleWithLogging("R9A", "scheduleGstr9aDownload");
	}

	@GetMapping("/R98A") // ready-- no data
	public ResponseEntity<String> scheduleGstr98aDownload() {
		return scheduleWithLogging("R98A", "scheduleGstr98aDownload");
	}

	@GetMapping("/R9C") // ready---
	public ResponseEntity<String> scheduleGstr9cDownload() {
		return scheduleWithLogging("R9C", "scheduleGstr9cDownload");
	}

	@GetMapping("/R10") // ready---
	public ResponseEntity<String> scheduleGstr10Download() {
		return scheduleWithLogging("R10", "scheduleGstr10Download");
	}

	@GetMapping("/R11") // ready---
	public ResponseEntity<String> scheduleGstr11Download() {
		return scheduleWithLogging("R11", "scheduleGstr11Download");
	}

	@GetMapping("/R3") // --notReady
	public ResponseEntity<String> scheduleGstr3Download() {
		return scheduleWithLogging("R3", "scheduleGstr3Download");
	}

	@GetMapping("/R12") // --notReady
	public ResponseEntity<String> scheduleGstr12Download() {
		return scheduleWithLogging("R12", "scheduleGstr12Download");
	}

	@GetMapping("/R13") // --notReady
	public ResponseEntity<String> scheduleGstr13Download() {
		return scheduleWithLogging("R13", "scheduleGstr13Download");
	}

	@GetMapping("/R14") // --notReady
	public ResponseEntity<String> scheduleGstr14Download() {
		return scheduleWithLogging("R14", "scheduleGstr14Download");
	}

	@GetMapping("/PMT") // --notReady
	public ResponseEntity<String> schedulePmtDownload() {
		return scheduleWithLogging("PMT", "schedulePmtDownload");
	}

	@GetMapping("/R13B") // --notReady
	public ResponseEntity<String> scheduleGstr1r3bDownload() {
		return scheduleWithLogging("R13B", "scheduleGstr1r3bDownload");
	}

	@GetMapping("/saveDataIntoToDb")
	public ResponseEntity<String> saveDataFromJson(@RequestParam String application) {

		return executeWithLogging("SAVE_DATA_FROM_JSON_TO_DB", application, () -> {

			long totalRecords = commonControllerGstrUtilityImpl.saveDataFromJsonToDb(application);

			return "Successfully Processed Records : " + totalRecords;
		});
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

	@GetMapping("/downloadDocument") // not working
	public ResponseEntity<List<String>> getDownloadDocument() {
		String userName = "GSTG2G18";
		List<String> responses = gstinServiceRegistration.getDownloadDocumentViaGstin(userName);
		return ResponseEntity.ok(responses);
	}

	@GetMapping("/processAllDocuments") // need to change the table
	public ResponseEntity<String> processAllDocuments() {

		String response = gstinServiceRegistration.processAllDocuments(USERNAME);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/processFewDocuments") // need to change the table--
	public ResponseEntity<String> processFewDocuments() {

		String response = gstinServiceRegistration.processFewDocuments(USERNAME);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/get-comparison-report") // Get Comparison Report
	public ResponseEntity<String> getComparisonReport() {

		String response = registrationServiceImpl.processDocumentsHim(USERNAME);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/process-documents-dh") // need to change the table--
	public ResponseEntity<String> processDocumentsDh() {

		String response = gstinServiceRegistration.processDocumentsDh(USERNAME);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/get-normal-taxpayer") // Get Normal Tax Payer
	public ResponseEntity<String> getNormalTaxPayer() {

		String response = registrationServiceImpl.getNormalTaxPayerPre(USERNAME);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/get-tds-tcs-taxpayer") // Get Normal Tax Payer
	public ResponseEntity<String> getTdsTcs() {

		String response = registrationServiceImpl.getTdsTcsTaxPayerPre(USERNAME);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/GetEntityEnforcementOfficer") // Get Normal Tax Payer
	public ResponseEntity<String> GetEntityEnforcementOfficer() {

		String response = registrationServiceImpl.GetEntityEnforcementOfficer(USERNAME);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/GetLedgerItcOnly") // LEDGER ITC ONLY
	public ResponseEntity<String> GetLedgerItcOnly() {

		String response = registrationServiceImpl.GetLedgerItcOnly(USERNAME);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/{ewbNo}")
	public ResponseEntity<EwayBillViewResponse> getEwayBill(@PathVariable long ewbNo) {

		return ResponseEntity.ok(eWayBillApiService.getEwayBill(ewbNo));
	}

	@GetMapping("/compare/{ewbNo}")
	public ResponseEntity<EwayBillComparisonResponse> compare(@PathVariable long ewbNo) {

		return ResponseEntity.ok(eWayBillApiService.compare(ewbNo));
	}

	@GetMapping("/search")
	public ResponseEntity<Page<EwayBillViewResponse>> searchByDate(@RequestParam String docDate,
			@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {

		return ResponseEntity.ok(eWayBillApiService.searchByDate(docDate, status, page, size));
	}

	@GetMapping("/ewaybill/pdf")
	public ResponseEntity<byte[]> downloadPdf(@RequestParam String docDate,
			@RequestParam(defaultValue = "ALL") String status) {

		try {

			byte[] pdf = ewayBillReportServiceImpl.generatePdf(docDate, status);

			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ewaybill-report.pdf")
					.contentType(MediaType.APPLICATION_PDF).body(pdf);

		} catch (Exception e) {

			log.error("PDF generation failed", e);

			return ResponseEntity.internalServerError().build();
		}

	}

	@GetMapping("/ewaybill/word")
	public ResponseEntity<byte[]> downloadExcel(@RequestParam String docDate,
			@RequestParam(defaultValue = "ALL") String status) {

		try {

			byte[] excel = ewayBillReportServiceImpl.generateExcel(docDate, status);

			return ResponseEntity.ok()

					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ewaybill-report.xlsx")

					.contentType(MediaType.APPLICATION_OCTET_STREAM)

					.body(excel);

		} catch (Exception e) {

			log.error("Excel generation failed", e);

			return ResponseEntity.internalServerError().build();

		}

	}

	@PostMapping("/read")
	public String readPdf() throws Exception {

		ewayBillReportServiceImpl.readAndSave("D:/PDF/881720801117_EWB.pdf");

		return "PDF Read Successfully";
	}

}
