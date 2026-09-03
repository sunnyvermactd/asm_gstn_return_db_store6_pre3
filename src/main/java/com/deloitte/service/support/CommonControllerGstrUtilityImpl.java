package com.deloitte.service.support;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.deloitte.common.bean.GSTCommonResponseBean;
import com.deloitte.common.constant.Constants;
import com.deloitte.common.entity.APIDetails;
import com.deloitte.common.entity.GSTUserSession;
import com.deloitte.common.entity.MasterData;
import com.deloitte.returns.entity.GstinEntity;
import com.deloitte.returns.entity.log.LedgerInitialJson;
import com.deloitte.returns.entity.registration.LedgerCommonDetails;
import com.deloitte.returns.entity.registration.RegularTaxpayer;
import com.deloitte.returns.repository.common.GstinRepository;
import com.deloitte.returns.repository.common.LedgerInitialJsonRepository;
import com.deloitte.returns.repository.ledger.LedgerCommonDetailsRepository;
import com.deloitte.returns.repository.registration.RegularTaxpayerRepository;
import com.deloitte.returns.service.AuthenticationHelper;
import com.deloitte.returns.service.GstUserSessionServices;
import com.deloitte.returns.service.MasterDataService;
import com.deloitte.service.helper.REST.call.RestClientHelper;
import com.deloitte.service.impl.APIDetailsImpl;
import com.deloitte.service.impl.CommonServiceGstrLedgerImpl;
import com.deloitte.service.impl.Gstr2aServiceImpl;
import com.deloitte.service.utility.procedure.LedgerGstinProjection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public class CommonControllerGstrUtilityImpl {

	private final GstinRepository gstinRepository;
	private final Gstr2aServiceImpl gstr2aServiceImpl;
	private final CommonServiceGstrLedgerImpl commonServiceGstrLedgerImpl;
	private final RegularTaxpayerRepository regularTaxpayerRepository;
	private final MasterDataService masterDataService;
	private final GstUserSessionServices gstUserSessionServices;

	private final RestClientHelper restClient;

	private final AuthenticationHelper authenticationHelper;
	private final APIDetailsImpl apiDetailsImpl;
	private final ObjectMapper objectMapper;
	private final LedgerCommonDetailsRepository ledgerCommonDetailsRepository;
	private final LedgerInitialJsonRepository ledgerInitialJsonRepository;
	

	protected static String USERNAME = "GSTG2G18";

//	fr_dt="072017";
//	to_dt="102024";
	public String getGstr2aDetailsAndSave(List<String> section, String fr_dt, String to_dt) {
		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("MMyyyy");

		LocalDate fromDate = LocalDate.parse(fr_dt, inputFormatter);
		LocalDate toDate = LocalDate.parse(to_dt, inputFormatter);

		if (fromDate.isAfter(toDate)) {
			System.out.println("Invalid date range: fromDate is after toDate.");
			return "Invalid date range";
		}

		List<String> monthYearRanges = new ArrayList<>();
		LocalDate current = fromDate;

		while (!current.isAfter(toDate)) {
			String monthYear = current.format(outputFormatter);
			monthYearRanges.add(monthYear);
			current = current.plusMonths(1); // Move to the next month
		}

		// Print divided month-year ranges
		System.out.println("Month-Year Ranges: " + monthYearRanges);

		List<String> responses = new ArrayList<>();
		List<GstinEntity> listOfData = gstinRepository.findAllByIsProcessedGstr2aFalseAndFoundInfoTrueOrderById();

		// Process data for each month
		for (String monthYear : monthYearRanges) {
			LocalDate monthStartDate = LocalDate.parse("01" + monthYear, DateTimeFormatter.ofPattern("ddMMyyyy"));
			LocalDate monthEndDate = monthStartDate.withDayOfMonth(monthStartDate.lengthOfMonth());

			if (monthEndDate.isAfter(toDate)) {
				monthEndDate = toDate; // Adjust the end date for the last month
			}

			// Process the entries for the current month range
			processGstr2AGstinEntries(listOfData, USERNAME, section, monthStartDate, monthEndDate, outputFormatter,
					responses);
		}

		System.out.println("Responses:\n" + responses);
		return responses.toString().trim(); // Remove trailing newline
	}

	private void processGstr2AGstinEntries(List<GstinEntity> listOfData, String userName, List<String> section,
			LocalDate fromDate, LocalDate toDate, DateTimeFormatter outputFormatter, List<String> responses) {

		String processingDate = toDate.format(outputFormatter);
		// System.out.println("Processing date : " + processingDate);

		listOfData.forEach(gstinEntry -> {
			String gstinNumber = gstinEntry.getGstin();
			String msg = getGstr2AFromGstn(userName, section, gstinNumber, processingDate);
			responses.add(msg);

			boolean isSuccess = msg.contains("Files have been saved successfully for gstin:");
			gstinEntry.setFoundInfo(isSuccess);
			gstinEntry.setIsProcessedGstr2a(true);
			gstinRepository.save(gstinEntry);
		});
	}

	public String getGstr2AFromGstn(String userName, List<String> section, String gstinNumber,
			String formattedFromDate) {
		return gstr2aServiceImpl.getCommonGstr2aDetailsAndSave(userName, section, gstinNumber, formattedFromDate);

	}

	public String getLedgerFromGstn(String action, String gstin, String fr_dt, String to_dt) {

		String userName = "GSTG2G18";
		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

		// Convert input strings to LocalDate
		LocalDate fromDate = LocalDate.parse(fr_dt, inputFormatter);
		LocalDate toDate = LocalDate.parse(to_dt, inputFormatter);

		// Debug: Print parsed dates
		System.out.println("Parsed fromDate: " + fromDate);
		System.out.println("Parsed toDate: " + toDate);

		// Validate date range
		if (fromDate.isAfter(toDate)) {
			System.out.println("Invalid date range: fromDate is after toDate.");
			return "Invalid date range";
		}

		// Check the duration between fromDate and toDate
		long monthsBetween = ChronoUnit.MONTHS.between(fromDate, toDate.plusDays(1));
		System.out.println("Months between: " + monthsBetween);

		List<String> responses = new ArrayList<>();

		if (monthsBetween > 12) {
			// Split the range into 12-month segments
			while (!fromDate.isAfter(toDate)) {
				LocalDate rangeEndDate = fromDate.plusMonths(12).minusDays(1);
				if (rangeEndDate.isAfter(toDate)) {
					rangeEndDate = toDate;
				}

				String formattedFromDate = fromDate.format(outputFormatter);
				String formattedToDate = rangeEndDate.format(outputFormatter);

				// Call the implementation method for each split range
				String response = commonServiceGstrLedgerImpl.getLedgerFromGstn(userName, action, gstin,
						formattedFromDate, formattedToDate);
				responses.add(response);

				fromDate = rangeEndDate.plusDays(1); // Move to the next segment
			}
		} else {
			// No need to split, process the entire range
			String formattedFromDate = fromDate.format(outputFormatter);
			String formattedToDate = toDate.format(outputFormatter);

			String response = commonServiceGstrLedgerImpl.getLedgerFromGstn(userName, action, gstin, formattedFromDate,
					formattedToDate);
			responses.add(response);
		}

		System.out.println("Responses:\n" + String.join("\n", responses));
		return String.join("\n", responses); // Join with newline

	}

	public String getLedgerForMultipleGSTN(String action, String fr_dt, String to_dt) {

		long startTime = System.currentTimeMillis();

		log.info("▶ Ledger Multiple GSTN Process START | action={}, fr_dt={}, to_dt={}", action, fr_dt, to_dt);

		String userName = "GSTG2G18";
		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

		LocalDate fromDate;
		LocalDate toDate;
		List<GstinEntity> listOfData = fetchGstinListByAction(action);
		try {

			fromDate = LocalDate.parse(fr_dt, inputFormatter);
			toDate = LocalDate.parse(to_dt, inputFormatter);
		} catch (Exception e) {
			log.error("❌ Invalid date format | fr_dt={}, to_dt={}", fr_dt, to_dt, e);
			return "Invalid date format, required yyyy-MM-dd";
		}

		if (fromDate.isAfter(toDate)) {
			log.warn("⚠ Invalid date range: fromDate={} is after toDate={}", fromDate, toDate);
			return "Invalid date range";
		}

		long monthsBetween = ChronoUnit.MONTHS.between(fromDate, toDate.plusDays(1));
		log.info("📅 Months between = {}", monthsBetween);

		List<String> responses = new ArrayList<>();

		log.info("📌 Total GSTIN records to process = {}", listOfData.size());

		if (monthsBetween > 12) {

			log.info("🔁 Date range > 12 months → Splitting into chunks");

			while (!fromDate.isAfter(toDate)) {

				LocalDate rangeEndDate = calculateRangeEndDate(fromDate, toDate);// 1

				log.info("▶ Processing Date Range: {} → {}", fromDate.format(outputFormatter),
						rangeEndDate.format(outputFormatter));

				processGstinEntries(listOfData, userName, action, fromDate, rangeEndDate, outputFormatter, responses);

				fromDate = rangeEndDate.plusDays(1);
			}

		} else {

			log.info("▶ Processing Full Date Range: {} → {}", fromDate.format(outputFormatter),
					toDate.format(outputFormatter));

			processGstinEntries(listOfData, userName, action, fromDate, toDate, outputFormatter, responses);
		}

		long totalTime = System.currentTimeMillis() - startTime;

		log.info("✅ Ledger Multiple GSTN Process COMPLETED | Total Responses={} | TimeTaken={} ms", responses.size(),
				totalTime);

		return responses.toString().trim();
	}

	private LocalDate calculateRangeEndDate(LocalDate fromDate, LocalDate toDate) {

		log.debug("▶ Calculating 12-month date window | fromDate={}, toDate={}", fromDate, toDate);
		LocalDate rangeEndDate = fromDate.plusMonths(12).minusDays(1);
		LocalDate finalEndDate = rangeEndDate.isAfter(toDate) ? toDate : rangeEndDate;
		log.debug("✅ Range calculated | rangeEndDate={}, finalEndDate={}", rangeEndDate, finalEndDate);
		return finalEndDate;
	}

	private void processGstinEntries(List<GstinEntity> listOfData, String userName, String action, LocalDate fromDate,
			LocalDate toDate, DateTimeFormatter outputFormatter, List<String> responses) {

		String formattedFromDate;
		String formattedToDate;

		// ================= Date Formatting =================

		if (action.equalsIgnoreCase("TAX")) {
			// Format as MMyyyy → Example: 042023
			formattedFromDate = fromDate.format(DateTimeFormatter.ofPattern("MMyyyy"));
			formattedToDate = toDate.format(DateTimeFormatter.ofPattern("MMyyyy"));
		} else {
			// Default format → Example: 01-04-2023
			formattedFromDate = fromDate.format(outputFormatter);
			formattedToDate = toDate.format(outputFormatter);
		}

		log.info("▶ Processing Date Window | Action={} | From={} | To={}", action, formattedFromDate, formattedToDate);

		long startTime = System.currentTimeMillis();
		int total = listOfData.size();

		log.info("▶ Total GSTINs to process in this window: {}", total);

		// ================= GSTIN Processing =================

		listOfData.forEach((gstinEntry) -> {

			String gstinNumber = gstinEntry.getGstin();
			long gstinStartTime = System.currentTimeMillis();

			log.debug("➡ Processing GSTIN={} | Window={} → {}", gstinNumber, formattedFromDate, formattedToDate);

			String msg = commonServiceGstrLedgerImpl.getLedgerFromGstn(userName, action, gstinNumber, formattedFromDate,
					formattedToDate);

			responses.add(msg);

			boolean isSuccess = msg != null && msg.contains("Files have been saved successfully for gstin:");

			gstinEntry.setFoundInfo(isSuccess);
			// Set processed flag based on action
			switch (action.toUpperCase()) {

			case "CASH":
				gstinEntry.setIsProcessedLedgerCash(true); // Get Cash Ledger
				break;

			case "TAX":
				gstinEntry.setIsProcessedLedgerTax(true);// Get Liability Ledger For Return Liability
				break;

			case "ITC":
				gstinEntry.setIsProcessedLedgerItc(true);// Get ITC Ledger
				break;

			default:// NRTN
				gstinEntry.setIsProcessedLedgerOther(true);// Get Other than Return Ledger Details
			}

			gstinRepository.save(gstinEntry);

			long gstinTime = System.currentTimeMillis() - gstinStartTime;

			if (isSuccess) {
				log.info("✅ GSTIN Success | {} | TimeTaken={} ms", gstinNumber, gstinTime);
			} else {
				log.warn("⚠ GSTIN Failed | {} | Response={} | TimeTaken={} ms", gstinNumber, msg, gstinTime);
			}
		});

		long totalTime = System.currentTimeMillis() - startTime;

		log.info("✅ Completed Window | Action={} | From={} | To={} | TotalGSTIN={} | TimeTaken={} ms", action,
				formattedFromDate, formattedToDate, total, totalTime);
	}

	private List<GstinEntity> fetchGstinListByAction(String action) {

		switch (action.toUpperCase()) {

		case "CASH":
			return gstinRepository.findAllByIsProcessedLedgerCashFalseOrderById();

		case "TAX":
			return gstinRepository.findAllByIsProcessedLedgerTaxFalseOrderById();

		case "ITC":
			return gstinRepository.findAllByIsProcessedLedgerItcFalseOrderById();

		default:// NRTN
			return gstinRepository.findAllByIsProcessedLedgerOtherFalseOrderById();
		}
	}

	public String processLedgerFromFile(String action) {
		// TODO Auto-generated method stub
		return commonServiceGstrLedgerImpl.processLedgerFromFile(action);
	}

	// ============================================================
	// MAIN METHOD
	// ============================================================

	public String getLedgerForMultipleGSTNAutomatically(String action, String fr_dt, String to_dt) {

		long startTime = System.currentTimeMillis();

		log.info("=================================================");
		log.info("▶ LEDGER MULTIPLE GSTIN PROCESS START");
		log.info("📌 action={} | fr_dt={} | to_dt={}", action, fr_dt, to_dt);
		log.info("=================================================");

		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

		LocalDate fromDate;
		LocalDate toDate;

		try {

			fromDate = LocalDate.parse(fr_dt, inputFormatter);

			toDate = LocalDate.parse(to_dt, inputFormatter);

		} catch (Exception e) {

			log.error("❌ Invalid Date Format", e);

			return "Invalid date format. Required yyyy-MM-dd";
		}

		if (fromDate.isAfter(toDate)) {

			log.warn("⚠ fromDate > toDate");

			return "Invalid Date Range";
		}

		// ============================================================
		// FETCH GSTINS
		// ============================================================

		List<RegularTaxpayer> gstinList = regularTaxpayerRepository.findLatestActiveGstin();

		log.info("📦 Total GSTIN Found={}", gstinList.size());

		int successCount = 0;
		int failCount = 0;
		int skippedCount = 0;
		int processed = 0;

		for (RegularTaxpayer taxpayer : gstinList) {

			processed++;

			String gstin = taxpayer.getGstin();

			LocalDate apprvDate = taxpayer.getApprvdt();

			log.info("-------------------------------------------------");
			log.info("🔄 Processing {}/{}", processed, gstinList.size());
			log.info("📌 GSTIN={}", gstin);
			log.info("📌 Approval Date={}", apprvDate);
			log.info("-------------------------------------------------");

			try {

				// ====================================================
				// CASE 1
				// APPROVAL DATE AFTER TO DATE
				// ====================================================

				if (apprvDate.isAfter(toDate)) {

					log.warn("⏭ Skipping GSTIN={} because apprvdt > toDate", gstin);

					saveSkippedRecord(gstin, action, fromDate, toDate, apprvDate, "APPROVAL_DATE_AFTER_TO_DATE",
							taxpayer.getId());

					skippedCount++;

					continue;
				}

				LocalDate finalFromDate = fromDate;

				// ====================================================
				// CASE 2
				// APPROVAL DATE BETWEEN RANGE
				// ====================================================

				if ((apprvDate.isEqual(fromDate) || apprvDate.isAfter(fromDate)) && apprvDate.isBefore(toDate)) {

					finalFromDate = apprvDate;

					log.info("📌 Adjusted FromDate={} for GSTIN={}", finalFromDate, gstin);
				}

				// ====================================================
				// PROCESS API
				// ====================================================

				callLedgerApi(gstin, action, finalFromDate, toDate, outputFormatter);

				successCount++;

			} catch (Exception e) {

				failCount++;

				log.error("❌ Failed GSTIN={}", gstin, e);
			}
		}

		long endTime = System.currentTimeMillis();

		log.info("=================================================");
		log.info("✅ LEDGER MULTIPLE GSTIN PROCESS COMPLETED");
		log.info("📌 Total={}", processed);
		log.info("✅ Success={}", successCount);
		log.info("❌ Failed={}", failCount);
		log.info("⏭ Skipped={}", skippedCount);
		log.info("⏱ Time Taken={} ms", (endTime - startTime));
		log.info("=================================================");

		return "Processed Successfully";
	}

	// ============================================================
	// CALL LEDGER API
	// ============================================================

	private void callLedgerApi(String gstin, String action, LocalDate fromDate, LocalDate toDate,
			DateTimeFormatter outputFormatter) throws Exception {

		String formattedFromDate;
		String formattedToDate;

		if ("TAX".equalsIgnoreCase(action)) {

			formattedFromDate = fromDate.format(DateTimeFormatter.ofPattern("MMyyyy"));

			formattedToDate = toDate.format(DateTimeFormatter.ofPattern("MMyyyy"));

		} else {

			formattedFromDate = fromDate.format(outputFormatter);

			formattedToDate = toDate.format(outputFormatter);
		}

		log.info("📌 API Date Range={} -> {}", formattedFromDate, formattedToDate);

		MasterData masterData = masterDataService.getMasterdatabyName(USERNAME);

		if (masterData == null) {

			throw new RuntimeException("MASTER_DATA_NOT_FOUND");
		}

		GSTUserSession gstUserSession = gstUserSessionServices.getUserSessionsByName(USERNAME);

		if (gstUserSession == null) {

			throw new RuntimeException("SESSION_NOT_FOUND");
		}

		APIDetails apiDetails;

		if ("NRTN".equalsIgnoreCase(action)) {

			apiDetails = apiDetailsImpl.findByName(Constants.GET_OTHER_THAN_RETURN_LEDGER_DETAILS);

		} else {

			apiDetails = apiDetailsImpl.findByName(Constants.GET_RETURN_LEDGER);
		}
		// ============================================================
		// CHECK DUPLICATE ENTRY
		// ============================================================

		boolean alreadyExists = ledgerCommonDetailsRepository.existsByGstinAndActionAndFromDateAndToDate(gstin, action,
				fromDate, toDate);

		if (alreadyExists) {

			log.warn("⚠ ENTRY ALREADY EXISTS GSTIN={} ACTION={} FROM={} TO={}", gstin, action, fromDate, toDate);

			LedgerCommonDetails entry = new LedgerCommonDetails();
			LedgerInitialJson ledgerInitialJson = new LedgerInitialJson();

			entry.setGstin(gstin);
			entry.setAction(action);
			entry.setFromDate(fromDate);
			entry.setToDate(toDate);
			entry.setIsProcessed(false);
			entry.setMsg("ALREADY EXISTS : " + fromDate + " TO " + toDate);
			entry.setStatus("ALREADY_EXISTS");

			ledgerInitialJson.setGstin(gstin);
			ledgerInitialJson.setFrDt(fromDate);
			ledgerInitialJson.setToDt(toDate);
			ledgerInitialJson.setMsg("ALREADY EXISTS : " + fromDate + " TO " + toDate);
			ledgerInitialJson.setRegularTaxpayerId(null);
			ledgerCommonDetailsRepository.save(entry);
			// ledgerInitialJsonRepository.save(ledgerInitialJson);

			log.info("💾 Duplicate Entry Saved GSTIN={}", gstin);

			return;
		}

		HttpHeaders headers = authenticationHelper.getDefaultHeaders(masterData, gstUserSession.getAuthToken(),
				apiDetails.getApiContentType());

		Map<String, String> params = getParamsForGetReturnFileLedger(apiDetails, masterData, action, gstin,
				formattedFromDate, formattedToDate);

		String path = authenticationHelper.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails),
				params);

		log.info("📡 API PATH={}", path);

		GSTCommonResponseBean response = restClient.get(path, GSTCommonResponseBean.class, headers);

		LedgerCommonDetails entity = new LedgerCommonDetails();

		entity.setGstin(gstin);
		entity.setAction(action);
		entity.setFromDate(fromDate);
		entity.setToDate(toDate);
		entity.setUrl(path);

		// ============================================================
		// SUCCESS
		// ============================================================

		if (response != null && "1".equals(response.getStatus_cd())) {

			byte[] decodedBytes = Base64.getDecoder().decode(response.getData());

			String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);

			entity.setEntityJson(decodedJson);

			entity.setIsProcessed(true);

			entity.setMsg("SUCCESS");

			entity.setStatus("UPDATED");

			log.info("✅ SUCCESS GSTIN={}", gstin);

		} else {

			entity.setIsProcessed(false);
			entity.setStatus("NOT_FOUND");
			entity.setEntityJson(objectMapper.writeValueAsString(response));

			String errorMsg = "GST_SERVER_ERROR";

			if (response != null && response.getError() != null && response.getError().get("message") != null) {

				errorMsg = response.getError().get("message");
			}

			entity.setMsg(errorMsg);

			log.error("❌ GST ERROR GSTIN={} msg={}", gstin, errorMsg);
		}

		ledgerCommonDetailsRepository.save(entity);

		log.info("💾 Ledger Saved GSTIN={}", gstin);
	}

	private void callLedgerApiByFunction(String gstin, String action, LocalDate fromDate, LocalDate toDate,
			DateTimeFormatter outputFormatter, LocalDate apprvDate, String authstatus, Long regulartaxpayerid)
			throws Exception {

		String formattedFromDate;
		String formattedToDate;

		if ("TAX".equalsIgnoreCase(action)) {

			formattedFromDate = fromDate.format(DateTimeFormatter.ofPattern("MMyyyy"));

			formattedToDate = toDate.format(DateTimeFormatter.ofPattern("MMyyyy"));

		} else {

			formattedFromDate = fromDate.format(outputFormatter);

			formattedToDate = toDate.format(outputFormatter);
		}

		log.info("📌 API Date Range={} -> {}", formattedFromDate, formattedToDate);

		MasterData masterData = masterDataService.getMasterdatabyName(USERNAME);

		if (masterData == null) {

			throw new RuntimeException("MASTER_DATA_NOT_FOUND");
		}

		GSTUserSession gstUserSession = gstUserSessionServices.getUserSessionsByName(USERNAME);

		if (gstUserSession == null) {

			throw new RuntimeException("SESSION_NOT_FOUND");
		}

		APIDetails apiDetails;

		if ("NRTN".equalsIgnoreCase(action)) {

			apiDetails = apiDetailsImpl.findByName(Constants.GET_OTHER_THAN_RETURN_LEDGER_DETAILS);

		} else {

			apiDetails = apiDetailsImpl.findByName(Constants.GET_RETURN_LEDGER);
		}
		// ============================================================
		// CHECK DUPLICATE ENTRY
		// ============================================================

		boolean alreadyExists = ledgerCommonDetailsRepository.existsByGstinAndActionAndFromDateAndToDate(gstin, action,
				fromDate, toDate);

		if (alreadyExists) {

			log.warn("⚠ ENTRY ALREADY EXISTS GSTIN={} ACTION={} FROM={} TO={}", gstin, action, fromDate, toDate);

			LedgerCommonDetails entry = new LedgerCommonDetails();
			LedgerInitialJson ledgerInitialJson = new LedgerInitialJson();

			entry.setGstin(gstin);
			entry.setAction(action);
			entry.setFromDate(fromDate);
			entry.setToDate(toDate);
			entry.setIsProcessed(false);
			entry.setApprvdt(apprvDate);
			entry.setAuthstatus(authstatus);
			entry.setMsg("ALREADY EXISTS : " + fromDate + " TO " + toDate);
			entry.setStatus("ALREADY_EXISTS");

			ledgerInitialJson.setGstin(gstin);
			ledgerInitialJson.setFrDt(fromDate);
			ledgerInitialJson.setToDt(toDate);
			ledgerInitialJson.setRegularTaxpayerId(regulartaxpayerid);
			ledgerInitialJson.setMsg("ALREADY EXISTS : " + fromDate + " TO " + toDate);
			ledgerCommonDetailsRepository.save(entry);
			// ledgerInitialJsonRepository.save(ledgerInitialJson);

			log.info("💾 Duplicate Entry Saved GSTIN={}", gstin);

			return;
		}

		HttpHeaders headers = authenticationHelper.getDefaultHeaders(masterData, gstUserSession.getAuthToken(),
				apiDetails.getApiContentType());

		Map<String, String> params = getParamsForGetReturnFileLedger(apiDetails, masterData, action, gstin,
				formattedFromDate, formattedToDate);

		String path = authenticationHelper.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails),
				params);

		log.info("📡 API PATH={}", path);

		GSTCommonResponseBean response = restClient.get(path, GSTCommonResponseBean.class, headers);

		LedgerCommonDetails entity = new LedgerCommonDetails();
		LedgerInitialJson ledgerInitialJson = new LedgerInitialJson();
		entity.setGstin(gstin);
		entity.setAction(action);
		entity.setFromDate(fromDate);
		entity.setToDate(toDate);
		entity.setUrl(path);

		ledgerInitialJson.setGstin(gstin);
		ledgerInitialJson.setLedgerTyp(action);
		ledgerInitialJson.setFrDt(fromDate);
		ledgerInitialJson.setToDt(toDate);
		ledgerInitialJson.setRegularTaxpayerId(regulartaxpayerid);

		// ============================================================
		// SUCCESS
		// ============================================================

		if (response != null && "1".equals(response.getStatus_cd())) {

			byte[] decodedBytes = Base64.getDecoder().decode(response.getData());

			String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);

			entity.setEntityJson(decodedJson);

			entity.setIsProcessed(true);

			entity.setMsg("SUCCESS");

			entity.setStatus("UPDATED");

			JsonNode jsonNode = objectMapper.readTree(decodedJson);
			ledgerInitialJson.setJsondata(jsonNode);
			ledgerInitialJson.setIsSuccess(true);
			ledgerInitialJson.setMsg("SUCCESS");

			log.info("✅ SUCCESS GSTIN={}", gstin);

		} else {

			entity.setIsProcessed(false);
			entity.setStatus("NOT_FOUND");
			entity.setEntityJson(objectMapper.writeValueAsString(response));

			ledgerInitialJson.setJsondata(objectMapper.valueToTree(response));
			ledgerInitialJson.setIsSuccess(false);

			String errorMsg = "GST_SERVER_ERROR";

			if (response != null && response.getError() != null && response.getError().get("message") != null) {

				errorMsg = response.getError().get("message");
			}

			entity.setMsg(errorMsg);
			ledgerInitialJson.setMsg(errorMsg);
			log.error("❌ GST ERROR GSTIN={} msg={}", gstin, errorMsg);
		}

		ledgerCommonDetailsRepository.save(entity);
		ledgerInitialJsonRepository.save(ledgerInitialJson);

		log.info("💾 Ledger Saved GSTIN={}", gstin);
	}

	// ============================================================
	// SAVE SKIPPED RECORD
	// ============================================================

	private void saveSkippedRecord(String gstin, String action, LocalDate fromDate, LocalDate toDate,
			LocalDate apprvDate, String msg, Long regulartaxpayerid) {

		try {

			LedgerCommonDetails entity = new LedgerCommonDetails();
			LedgerInitialJson ledgerInitialJson = new LedgerInitialJson();
			entity.setGstin(gstin);

			entity.setAction(action);

			entity.setFromDate(fromDate);

			entity.setToDate(toDate);

			entity.setApprvdt(apprvDate);

			entity.setIsProcessed(false);

			entity.setMsg(msg);

			ledgerInitialJson.setGstin(gstin);
			ledgerInitialJson.setFrDt(fromDate);
			ledgerInitialJson.setToDt(toDate);
			ledgerInitialJson.setIsSuccess(false);
			ledgerInitialJson.setRegularTaxpayerId(regulartaxpayerid);
			ledgerInitialJson.setApprvdt(apprvDate);
			ledgerInitialJson.setMsg("SKIPPED : " + fromDate + " TO " + toDate);

			ledgerCommonDetailsRepository.save(entity);
			ledgerInitialJsonRepository.save(ledgerInitialJson);

		} catch (Exception e) {

			log.error("❌ Failed Saving Skip Record GSTIN={}", gstin, e);
		}
	}

	// ============================================================
	// PARAMS
	// ============================================================

	private Map<String, String> getParamsForGetReturnFileLedger(APIDetails apiDetails, MasterData masterData,
			String action, String gstin, String fromDate, String toDate) {

		Map<String, String> params = new HashMap<>();

		params.put("action", action);

		params.put("gstin", gstin);

		params.put("fr_dt", fromDate);

		params.put("to_dt", toDate);

		params.put("state_cd", masterData.getStateCd());

		return params;
	}

	// ============================================================
	// PROCEDURE DEPENDENT
	// ============================================================

	public String getLedgerForMultipleGSTNFunctionDependent(String action, String fr_dt, String to_dt) {

		long startTime = System.currentTimeMillis();

		log.info("=================================================");
		log.info("▶ LEDGER MULTIPLE GSTIN PROCESS START");
		log.info("📌 action={} | fr_dt={} | to_dt={}", action, fr_dt, to_dt);
		log.info("=================================================");

		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

		LocalDate fromDate;
		LocalDate toDate;

		try {

			fromDate = LocalDate.parse(fr_dt, inputFormatter);

			toDate = LocalDate.parse(to_dt, inputFormatter);

		} catch (Exception e) {

			log.error("❌ Invalid Date Format", e);

			return "Invalid date format. Required yyyy-MM-dd";
		}

		if (fromDate.isAfter(toDate)) {

			log.warn("⚠ fromDate > toDate");

			return "Invalid Date Range";
		}

		// ============================================================
		// FETCH GSTINS
		// ============================================================

		List<LedgerGstinProjection> gstinList = regularTaxpayerRepository.getGstinForLedgerProcedure(fr_dt, to_dt,
				action);

		log.info("📦 Total GSTIN Found={}", gstinList.size());

		int successCount = 0;
		int failCount = 0;
		int skippedCount = 0;
		int processed = 0;

		for (LedgerGstinProjection taxpayer : gstinList) {

			processed++;

			String gstin = taxpayer.getGstin();

			LocalDate apprvDate = null;

			try {

				String apprvdtString = taxpayer.getApprvdt();

				if (apprvdtString != null && !apprvdtString.trim().isEmpty()
						&& !"null".equalsIgnoreCase(apprvdtString)) {

					apprvDate = LocalDate.parse(apprvdtString);
				}

			} catch (Exception e) {

				log.error("❌ Invalid Approval Date for GSTIN={} | apprvdt={}", gstin, taxpayer.getApprvdt(), e);

				skippedCount++;

				continue;
			}

			if (apprvDate == null) {

				log.warn("⏭ Skipping GSTIN={} because Approval Date is null/invalid", gstin);

				skippedCount++;

				continue;
			}

			log.info("-------------------------------------------------");
			log.info("🔄 Processing {}/{}", processed, gstinList.size());
			log.info("📌 GSTIN={}", gstin);
			log.info("📌 Approval Date={}", apprvDate);
			log.info("-------------------------------------------------");

			try {

				// ====================================================
				// CASE 1
				// APPROVAL DATE AFTER TO DATE
				// ====================================================

				if (apprvDate.isAfter(toDate)) {

					log.warn("⏭ Skipping GSTIN={} because apprvdt > toDate", gstin);

					saveSkippedRecord(gstin, action, fromDate, toDate, apprvDate, "APPROVAL_DATE_AFTER_TO_DATE",
							taxpayer.getRegulartaxpayerid());

					skippedCount++;

					continue;
				}

				LocalDate finalFromDate = fromDate;

				// ====================================================
				// CASE 2
				// APPROVAL DATE BETWEEN RANGE
				// ====================================================

//				if ((apprvDate.isEqual(fromDate) || apprvDate.isAfter(fromDate)) && apprvDate.isBefore(toDate)) {
//
//					finalFromDate = apprvDate;
//
//					log.info("📌 Adjusted FromDate={} for GSTIN={}", finalFromDate, gstin);
//				}
				if ((apprvDate.isEqual(fromDate) || apprvDate.isAfter(fromDate))
						&& (apprvDate.isEqual(toDate) || apprvDate.isBefore(toDate))) {

					finalFromDate = apprvDate;

					log.info("📌 Adjusted FromDate={} for GSTIN={}", finalFromDate, gstin);
				}

				// ====================================================
				// PROCESS API
				// ====================================================

				callLedgerApiByFunction(gstin, action, finalFromDate, toDate, outputFormatter, apprvDate,
						taxpayer.getAuthstatus(), taxpayer.getRegulartaxpayerid());

				successCount++;

			} catch (Exception e) {

				failCount++;

				log.error("❌ Failed GSTIN={}", gstin, e);
			}
		}

		long endTime = System.currentTimeMillis();

		log.info("=================================================");
		log.info("✅ LEDGER MULTIPLE GSTIN PROCESS COMPLETED");
		log.info("📌 Total={}", processed);
		log.info("✅ Success={}", successCount);
		log.info("❌ Failed={}", failCount);
		log.info("⏭ Skipped={}", skippedCount);
		log.info("⏱ Time Taken={} ms", (endTime - startTime));
		log.info("=================================================");

		return "Processed Successfully";
	}

	// TODO Auto-generated method stub

}
