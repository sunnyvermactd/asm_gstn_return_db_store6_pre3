package com.deloitte.service.impl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.deloitte.common.bean.GSTCommonResponseBean;
import com.deloitte.common.constant.Constants;
import com.deloitte.common.entity.APIDetails;
import com.deloitte.common.entity.GSTUserSession;
import com.deloitte.common.entity.MasterData;
import com.deloitte.returns.entity.LedgerDataJsonFile;
import com.deloitte.returns.entity.LedgerRegistrationData;
import com.deloitte.returns.entity.type.LedgerCash.LedgerCash;
import com.deloitte.returns.entity.type.LedgerItc.LedgerItc;
import com.deloitte.returns.entity.type.LedgerLiability.LedgerLiability;
import com.deloitte.returns.entity.type.LedgerOther.LedgerOther;
import com.deloitte.returns.repository.common.LedgerDataJsonFileRepository;
import com.deloitte.returns.repositoryCommon.LedgerRegistrationDataRepository;
import com.deloitte.service.abs.CommonServiceImplAbs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public class CommonServiceGstrLedgerImpl extends CommonServiceImplAbs {

	private final LedgerDataJsonFileRepository ledgerDataJsonFileRepository;

	private final LedgerRegistrationDataRepository ledgerRegistrationDataRepository;

	private final ObjectMapper objectMapper;

	// Ledger

	public String getLedgerFromGstn(String username, String action, String gstin, String fromDate, String toDate) {

		log.info("Method :getLedgerFrom, Gstn:{} ,ActionType:{} ,From Date:{} ,To Date:{}", gstin, action, fromDate,
				toDate);

		String response = StringUtils.EMPTY;

		MasterData masterData = masterDataService.getMasterdatabyName(username);

		if (masterData == null) {
			log.error("User {} not found in Master Data Table", username);
			return "User not found in Master Data Table";
		}

		GSTUserSession gstUserSessions = gstUserSessionServices.getUserSessionsByName(username);

		if (gstUserSessions == null) {
			log.error("{} session is not authenticated. Please authenticate", username);
			return "Session not authenticated. Please authenticate";
		}

		response = getReturnFileLedger(masterData, gstUserSessions, action, gstin, fromDate, toDate);

		return response;
	}

	private String getReturnFileLedger(MasterData masterData, GSTUserSession gstUserSessions, String action,
			String gstin, String fromDate, String toDate) {

		String msg = null;

		try {

			APIDetails apiDetails;

			if (action.equalsIgnoreCase("NRTN")) {
				apiDetails = apiDetailsImpl.findByName(Constants.GET_OTHER_THAN_RETURN_LEDGER_DETAILS);
			} else {
				apiDetails = apiDetailsImpl.findByName(Constants.GET_RETURN_LEDGER);
			}

			HttpHeaders headers = authenticationHelper.getDefaultHeaders(masterData, gstUserSessions.getAuthToken(),
					apiDetails.getApiContentType());

			Map<String, String> params = getParamsForGetReturnFileLedger(apiDetails, masterData, action, gstin,
					fromDate, toDate);

			String path = authenticationHelper.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails),
					params);

			GSTCommonResponseBean response = restClient.get(path, GSTCommonResponseBean.class, headers);

			if ("1".equals(response.getStatus_cd())) {

				// ✅ RETURN MESSAGE FROM METHOD
				msg = saveLedgerJsonFile(response, action, gstin, fromDate, toDate);

			} else {
				// for the fail
				saveLedgerJsonFileFailed(response, action, gstin, fromDate, toDate, path);
				// saveLedgerJsonFileFail(response, action, gstin, fromDate, toDate);
				log.error("GST server error for gstin {}", gstin);
				msg = "❌ GST server error for GSTIN: " + gstin;
			}

		} catch (Exception e) {

			log.error("Exception for gstin {}", gstin, e);
			msg = "❌ Exception while processing GSTIN: " + gstin;
		}

		return msg;
	}

	private void saveLedgerJsonFileFail(GSTCommonResponseBean response, String action, String gstin, String fromDate,
			String toDate) {
		LedgerRegistrationData ledgerRegistrationData = new LedgerRegistrationData();
		ledgerRegistrationData.setAction(action);
		ledgerRegistrationData.setGstin(gstin);
		ledgerRegistrationData.setFromDate(fromDate);
		ledgerRegistrationData.setToDate(toDate);
		ledgerRegistrationData.setFilePath("Failed");
		ledgerRegistrationData.setInsertDt(new Timestamp(System.currentTimeMillis()));
		ledgerRegistrationDataRepository.save(ledgerRegistrationData);
	}

	private void saveLedgerJsonFileFailed(GSTCommonResponseBean response, String action, String gstin, String fromDate,
			String toDate, String path) {

		log.info("saveLedgerJsonFileFailed called for GSTIN: {}, Action: {}, FromDate: {}, ToDate: {}", gstin, action,
				fromDate, toDate);
		LedgerDataJsonFile entity = null;
		Optional<LedgerDataJsonFile> existingEntity = ledgerDataJsonFileRepository.findByActionAndGstinAndFromDateAndToDate(action, gstin,fromDate, toDate);

		if (existingEntity.isPresent()) {
			// Record already exists -> same object use karo
			entity = existingEntity.get();
		} else {
			// Record nahi hai -> new object banao
			entity = new LedgerDataJsonFile();

		}

		entity.setAction(action);
		entity.setGstin(gstin);
		entity.setFromDate(fromDate);
		entity.setToDate(toDate);
		entity.setFilePath("Failed");
		entity.setIsSuccess(false);
		entity.setUrlPath(path);
		entity.setInsertDt(new Timestamp(System.currentTimeMillis()));
		entity.setDownloadAttempt(entity.getDownloadAttempt() + 1);
		
		
		
		

		ledgerDataJsonFileRepository.save(entity);
	}

	private Map<String, String> getParamsForGetReturnFileLedger(APIDetails apiDetails, MasterData masterData,
			String action, String gstin, String fromDate, String toDate) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("action", action);
		params.put("gstin", gstin);
		if (action.equalsIgnoreCase("NRTN")) {
			params.put("fromDate", fromDate);
			params.put("toDate", toDate);
		} else {
			params.put("fr_dt", fromDate);
			params.put("to_dt", toDate);
		}
		return params;
	}

	private String saveLedgerJsonFile(GSTCommonResponseBean response, String action, String gstin, String fromDate,
			String toDate) {

		LedgerDataJsonFile entity;

		Optional<LedgerDataJsonFile> existingEntity = ledgerDataJsonFileRepository
				.findByGstinAndFromDateAndToDateAndAction(gstin, fromDate, toDate, action);

		if (existingEntity.isPresent()) {
			// Record already exists -> same object use karo
			entity = existingEntity.get();
		} else {
			// Record nahi hai -> new object banao
			entity = new LedgerDataJsonFile();

			entity.setAction(action);
			entity.setGstin(gstin);
			entity.setFromDate(fromDate);
			entity.setToDate(toDate);
			entity.setInsertDt(new Timestamp(System.currentTimeMillis()));
		}

		try {

			// Duplicate success record check
			if (Boolean.TRUE.equals(entity.getIsSuccess())) {
				return "⚠ Already exists for GSTIN: " + gstin;
			}

			// REK null
			if (response.getRek() == null) {

				entity.setDownloadFileStatus(false);

				Integer attempt = entity.getDownloadAttempt();
				entity.setDownloadAttempt(attempt == null ? 1 : attempt + 1);

				ledgerDataJsonFileRepository.save(entity);

				return "⚠ REK missing for GSTIN: " + gstin;
			}

			String jsonString = new String(Base64.getDecoder().decode(response.getData()), StandardCharsets.UTF_8);

			JsonNode jsonNode = objectMapper.readTree(jsonString);

			Path folderPath = Paths.get(LedgerFileLocation, action);
			Files.createDirectories(folderPath);

			String fileName = gstin + "_" + fromDate + "_" + toDate + ".json";
			Path filePath = folderPath.resolve(fileName);

			if (!Files.exists(filePath)) {
				Files.write(filePath, jsonString.getBytes(StandardCharsets.UTF_8));
			}

			entity.setFilePath(filePath.toString());
			entity.setDownloadFileStatus(true);
			entity.setJsonData(jsonNode);
			entity.setIsSuccess(true);

			ledgerDataJsonFileRepository.save(entity);

			return "✅ File saved successfully for GSTIN: " + gstin;

		} catch (Exception e) {

			entity.setDownloadFileStatus(false);

			Integer attempt = entity.getDownloadAttempt();
			entity.setDownloadAttempt(attempt == null ? 1 : attempt + 1);

			ledgerDataJsonFileRepository.save(entity);

			log.error("Error saving ledger JSON for GSTIN {}", gstin, e);

			return "❌ Error saving file for GSTIN: " + gstin;
		}
	}

//	private String saveLedgerJsonFile(GSTCommonResponseBean response, String action, String gstin, String fromDate,
//			String toDate) {
//		
//		Optional<LedgerDataJsonFile> entity= ledgerDataJsonFileRepository
//				.findByGstinAndFromDateAndToDateAndAction(gstin, fromDate, toDate, action);
//
//		LedgerDataJsonFile entity = new LedgerDataJsonFile();
//
//		try {
//
//			entity.setAction(action);
//			entity.setGstin(gstin);
//			entity.setFromDate(fromDate);
//			entity.setToDate(toDate);
//			entity.setInsertDt(new Timestamp(System.currentTimeMillis()));
//
//			// ✅ Duplicate check
//			Optional<LedgerDataJsonFile> existing = ledgerDataJsonFileRepository
//					.findByGstinAndFromDateAndToDateAndActionAndIsSuccess(gstin, fromDate, toDate, action,true);
//
//			if (existing.isPresent()) {
//				return "⚠ Already exists for GSTIN: " + gstin;
//			}
//
//			// ❌ REK null
//			if (response.getRek() == null) {
//				entity.setDownloadFileStatus(false);
//				entity.setDownloadAttempt(entity.getDownloadAttempt() + 1);
//				ledgerDataJsonFileRepository.save(entity);
//				return "⚠ REK missing for GSTIN: " + gstin;
//			}
//
//			String jsonString = new String(Base64.getDecoder().decode(response.getData()), StandardCharsets.UTF_8);
//
//			JsonNode jsonNode = objectMapper.readTree(jsonString);
//
//			Path folderPath = Paths.get(LedgerFileLocation, action);
//			Files.createDirectories(folderPath);
//
//			String fileName = gstin + "_" + fromDate + "_" + toDate + ".json";
//			Path filePath = folderPath.resolve(fileName);
//
//			if (!Files.exists(filePath)) {
//				Files.write(filePath, jsonString.getBytes(StandardCharsets.UTF_8));
//			}
//
//			entity.setFilePath(filePath.toString());
//			entity.setDownloadFileStatus(true);
//			entity.setJsonData(jsonNode);
//			entity.setIsSuccess(true);
//
//			ledgerDataJsonFileRepository.save(entity);
//
//			return "✅ File saved successfully for GSTIN: " + gstin;
//
//		} catch (Exception e) {
//
//			entity.setAction(action);
//			entity.setGstin(gstin);
//			entity.setFromDate(fromDate);
//			entity.setToDate(toDate);
//			entity.setInsertDt(new Timestamp(System.currentTimeMillis()));
//			entity.setDownloadFileStatus(false);
//			entity.setDownloadAttempt(entity.getDownloadAttempt() + 1);
//
//			ledgerDataJsonFileRepository.save(entity);
//
//			log.error("Error saving ledger JSON for GSTIN {}", gstin, e);
//
//			return "❌ Error saving file for GSTIN: " + gstin;
//		}
//	}

	public String processLedgerFromFile(String action) {

		ObjectMapper objectMapper = new ObjectMapper();

		int totalProcessed = 0;

		while (true) {

			// ✅ fetch next 100 unprocessed records
			List<LedgerDataJsonFile> list = ledgerDataJsonFileRepository
					.findTop100ByActionAndIsProcessedFalseOrderByIdAsc(action);

			// 🚨 STOP condition
			if (list.isEmpty()) {
				break;
			}

			for (LedgerDataJsonFile file : list) {

				try {

					JsonNode jsonNode = file.getJsonData();

					if (jsonNode == null || jsonNode.isEmpty()) {
						log.error("❌ Empty JSON for ID: {}", file.getId());
						file.setIsProcessed(false);
						continue;
					}

					if (action.equalsIgnoreCase("CASH")) {

						LedgerCash ledgerCash = objectMapper.treeToValue(jsonNode, LedgerCash.class);

						if (ledgerCash.getGstin() != null && ledgerCash.getGstin().length() > 1) {
							ledgerCashRepository.save(ledgerCash);
						}

					} else if (action.equalsIgnoreCase("TAX")) {

						LedgerLiability obj = objectMapper.treeToValue(jsonNode, LedgerLiability.class);

						if (obj.getGstin() != null && obj.getGstin().length() > 1) {
							ledgerLiabilityRepository.save(obj);
						}

					} else if (action.equalsIgnoreCase("ITC")) {

						LedgerItc obj = objectMapper.treeToValue(jsonNode, LedgerItc.class);

						if (obj.getItcLdgDtls() != null && obj.getItcLdgDtls().getGstin() != null
								&& obj.getItcLdgDtls().getGstin().length() > 1) {

							ledgerItcRepository.save(obj);
						}

					} else if (action.equalsIgnoreCase("NRTN")) {

						LedgerOther obj = objectMapper.treeToValue(jsonNode, LedgerOther.class);

						if (obj.getGstin() != null && obj.getGstin().length() > 1) {
							ledgerOtherRepository.save(obj);
						}

					} else {
						log.error("❌ Invalid action: {}", action);
						file.setIsProcessed(false);
						continue;
					}

					// ✅ mark success
					file.setIsProcessed(true);
					totalProcessed++;

				} catch (Exception e) {

					log.error("❌ Error processing ID: {}", file.getId(), e);
					file.setIsProcessed(false);
				}
			}

			// 🔥 batch update DB (IMPORTANT FOR SPEED)
			ledgerDataJsonFileRepository.saveAll(list);

			log.info("✅ Batch completed. Total processed so far: {}", totalProcessed);
		}

		return "Processing completed. Total records processed: " + totalProcessed;
	}

	public String processLedgerFromFileOld(String action) {

		List<LedgerDataJsonFile> list = ledgerDataJsonFileRepository
				.findTop100ByActionAndIsProcessedFalseOrderByIdAsc(action);

		for (LedgerDataJsonFile file : list) {

			try {

				// ✅ FILE PATH
				String filePath = file.getFilePath();

				// ✅ READ JSON FROM FILE
				String jsonString = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);

				ObjectMapper mapper = new ObjectMapper();

				// 🔥 YOUR OLD LOGIC (SAME AS IT IS)
				if (action.equalsIgnoreCase("CASH")) {

					LedgerCash ledgerCash = mapper.readValue(jsonString, LedgerCash.class);

					if (ledgerCash.getGstin() != null && ledgerCash.getGstin().length() > 1) {
						ledgerCashRepository.save(ledgerCash);
					}

				} else if (action.equalsIgnoreCase("TAX")) {

					LedgerLiability obj = mapper.readValue(jsonString, LedgerLiability.class);

					if (obj.getGstin() != null && obj.getGstin().length() > 1) {
						ledgerLiabilityRepository.save(obj);
					}

				} else if (action.equalsIgnoreCase("ITC")) {

					LedgerItc obj = mapper.readValue(jsonString, LedgerItc.class);

					if (obj.getItcLdgDtls() != null && obj.getItcLdgDtls().getGstin() != null
							&& obj.getItcLdgDtls().getGstin().length() > 1) {

						ledgerItcRepository.save(obj);
					}

				} else if (action.equalsIgnoreCase("NRTN")) {

					LedgerOther obj = mapper.readValue(jsonString, LedgerOther.class);

					if (obj.getGstin() != null && obj.getGstin().length() > 1) {
						ledgerOtherRepository.save(obj);
					}

				} else {
					log.error("❌ Invalid action: {}", action);
					continue;
				}

				// ✅ MARK PROCESSED
				file.setIsProcessed(true);
				ledgerDataJsonFileRepository.save(file);

			} catch (Exception e) {

				log.error("❌ Error processing file: {}", file.getFilePath(), e);

				file.setIsProcessed(false);
				ledgerDataJsonFileRepository.save(file);
			}
		}

		return "Processed records for action: " + action;
	}

}
