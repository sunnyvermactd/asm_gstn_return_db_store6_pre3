package com.deloitte.service.support;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.deloitte.common.bean.DateHepler;
import com.deloitte.returns.entity.ReturnDateLog;
import com.deloitte.returns.entity.filecounter.DateCountData;
import com.deloitte.returns.entity.filecounter.ReturnFileCountResponse;
import com.deloitte.returns.repository.common.ReturnDateLogRepository;
import com.deloitte.returns.repository.common.ReturnFileCountResponseRepository;
import com.deloitte.returns.repositoryCommon.DateCountDataRepository;
import com.deloitte.service.impl.CommonServiceGstrImpl;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class CommonServiceGstrUtilityImpl {

	@Autowired
	private CommonServiceGstrImpl commonServiceGstrImpl;

	@Autowired
	private DateCountDataRepository dateCountDataRepository;

	@Autowired
	private ReturnFileCountResponseRepository returnFileCountResponseRepository;

	@Autowired
	private CommonServiceGstrImplSupport commonServiceGstrImplSupport;

	@Autowired
	private FileDownloadHelperCommon fileDownloadHelperCommon;

	@Autowired
	private ReturnDateLogRepository returnDateLogRepository;

	private static final String SUCCESS = "SUCCESS";
	private static final String FAIL = "FAIL";
	private static final String IN_PROGRESS = "IN_PROGRESS";

	// =========================================================
	// MAIN METHOD
	// =========================================================

	public String scheduleDownload(String application) {

		long overallStartTime = System.currentTimeMillis();

		final String username = "GSTG2G18";

		final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

		log.info("====================================================");
		log.info("SCHEDULE DOWNLOAD STARTED | APP={}", application);
		log.info("====================================================");

		try {

		
			ReturnDateLog logData = returnDateLogRepository.findTopByOrderByIdDesc();

			String startDate;

			if (logData != null) {
				startDate = logData.getStartDate();
			} else {
				startDate = "07-08-2026";
			}

			String endDate = LocalDate.now().minusDays(1).format(formatter);
			startDate = "18-08-2026";
			//endDate = "30-08-2026";

			log.info("NORMAL PROCESS STARTED | APP={} | START_DATE={} | END_DATE={}", application, startDate, endDate);

			/*
			 * ===================================================== STEP-4 : PROCESS DATE
			 * RANGE =====================================================
			 */
			String response = getForDateRange(username, startDate, endDate, application);

			long totalTime = System.currentTimeMillis() - overallStartTime;

			log.info("SCHEDULE DOWNLOAD COMPLETED | APP={} | TOTAL_TIME={} ms", application, totalTime);

			return response;

		} catch (Exception ex) {

			long totalTime = System.currentTimeMillis() - overallStartTime;

			log.error("SCHEDULE DOWNLOAD FAILED | APP={} | TOTAL_TIME={} ms | ERROR={}", application, totalTime,
					ex.getMessage(), ex);

			throw ex;
		}
	}
	// =========================================================
	// DATE RANGE PROCESS
	// =========================================================

	private String getForDateRange(String username, String startDate, String endDate, String application) {

		log.info("START getForDateRange | User={} | App={} | Start={} | End={}", username, application, startDate,
				endDate);

		if (StringUtils.isAnyBlank(username, startDate, endDate, application)) {

			return "Invalid Input";
		}

		List<String> dateList = DateHepler.getDateRange(startDate, endDate);

		if (CollectionUtils.isEmpty(dateList)) {

			return "Date List Empty";
		}

		log.info("Total Dates To Process={}", dateList.size());

		dateList.forEach(date -> {

			try {

				String response = processDateForGstr(username, date, application);

				log.info("Date Processed | Date={} | Response={}", date, response);

			} catch (Exception ex) {

				log.error("Error Processing Date={}", date, ex);
			}
		});

		return "Completed";
	}

	// =========================================================
	// MAIN DATE PROCESSING
	// =========================================================

	private String processDateForGstr(String username, String date, String application) {

		long startTime = System.currentTimeMillis();

		log.info("START :: processDateForGstr | App={} | Date={}", application, date);

		ReturnFileCountResponse entity = null;

		try {

			Date sqlDate = Date.valueOf(LocalDate.parse(date, DateTimeFormatter.ofPattern("dd-MM-yyyy")));


			Optional<ReturnFileCountResponse> existingOpt = returnFileCountResponseRepository.findByTyAndDt(application,
					sqlDate);

			if (existingOpt.isPresent()) {

				entity = existingOpt.get();

				log.info("EXISTING_RECORD_FOUND | App={} | Date={} | Attempt={} | Success={}", application, date,
						entity.getCounterAttempt(), entity.getIsSuccess());

				/*
				 * ===================================================== SKIP SUCCESS RECORD
				 * =====================================================
				 */
				if (Boolean.TRUE.equals(entity.getIsSuccess())) {

					log.info("ALREADY_SUCCESS | App={} | Date={}", application, date);

					return "Already SUCCESS";
				}
				
				if (entity.getCounterAttempt()>5) {

					log.info("COUNTER_ATTEMPT MORE THAN 5  | App={} | Attempt={}  | Date={}", application,entity.getCounterAttempt(), date);

					return "Already MORE THAN 5 ATTEMPT";
				}

				/*
				 * ===================================================== RETRY LIMIT CHECK
				 * =====================================================
				 */
				Integer currentAttempt = entity.getCounterAttempt();

				if (currentAttempt == null) {
					currentAttempt = 0;
				}

				if (currentAttempt >= 10) {

					log.warn("RETRY_LIMIT_EXCEEDED | App={} | Date={} | Attempt={}", application, date, currentAttempt);

					return "Retry Limit Exceeded";
				}

				/*
				 * ===================================================== UPDATE STATUS TO
				 * IN_PROGRESS =====================================================
				 */
				entity.setMsg(IN_PROGRESS);

			} else {

				/*
				 * ===================================================== CREATE NEW ENTRY
				 * =====================================================
				 */
				entity = new ReturnFileCountResponse();

				entity.setTy(application);
				entity.setDt(sqlDate);
				entity.setInsertDt(new Timestamp(System.currentTimeMillis()));
				entity.setCounterAttempt(0);
				entity.setIsSuccess(false);
				entity.setMsg(IN_PROGRESS);

				log.info("NEW_RECORD_CREATED | App={} | Date={}", application, date);
			}

			/*
			 * ========================================================= STEP-4 : SAVE
			 * IN_PROGRESS STATUS =========================================================
			 */
			returnFileCountResponseRepository.save(entity);

			log.info("STATUS_UPDATED_TO_IN_PROGRESS | App={} | Date={}", application, date);

			/*
			 * ========================================================= STEP-5 : START GSTN
			 * PROCESS =========================================================
			 */
			String response = beforeGetFromGstn(username, date, application);

			log.info("GSTN_PROCESS_COMPLETED | App={} | Date={} | Response={}", application, date, response);

			/*
			 * ========================================================= STEP-6 : FETCH DATE
			 * COUNT DATA =========================================================
			 */
			String applicationCheck = commonServiceGstrImplSupport.normalizeApplication(application);
			List<DateCountData> dateCountDataList = dateCountDataRepository.findByDateAndApplication(date,
					applicationCheck);

			log.info("DATE_COUNT_DATA_FETCHED | App={} | Date={} | Count={}", applicationCheck, date,
					dateCountDataList.size());
			
			if(response.equalsIgnoreCase("No files available for download."))
			{
				Optional<ReturnFileCountResponse> optionalReturnFileCountResponse = returnFileCountResponseRepository
						.findByTyAndDt(application, sqlDate);

				ReturnFileCountResponse objReturnFileCountResponse = optionalReturnFileCountResponse.get();
				
				Integer currentAttempt = entity.getCounterAttempt();
				objReturnFileCountResponse.setIsSuccess(false);

				objReturnFileCountResponse.setMsg(FAIL);

				objReturnFileCountResponse.setCounterAttempt(currentAttempt + 1);
				returnFileCountResponseRepository.save(objReturnFileCountResponse);

				long totalTime = System.currentTimeMillis() - startTime;

				log.info("END :: processDateForGstr | App={} | Date={} | Success={} | Attempt={} | Time={} ms", application,
						date, entity.getIsSuccess(), entity.getCounterAttempt(), totalTime);

				return response;
				
			}

			/*
			 * ========================================================= STEP-7 : CHECK FAIL
			 * RECORDS =========================================================
			 */
			boolean failExists = dateCountDataList.stream()
					.anyMatch(data -> FAIL.equalsIgnoreCase(data.getIsProcessed()));

			/*
			 * ========================================================= STEP-8 : FINAL
			 * STATUS UPDATE =========================================================
			 */
			Optional<ReturnFileCountResponse> optionalReturnFileCountResponse = returnFileCountResponseRepository
					.findByTyAndDt(application, sqlDate);

			ReturnFileCountResponse objReturnFileCountResponse = optionalReturnFileCountResponse.get();
			if (failExists) {

				Integer currentAttempt = entity.getCounterAttempt();

				if (currentAttempt == null) {
					currentAttempt = 0;
				}

				objReturnFileCountResponse.setCounterAttempt(currentAttempt + 1);

				objReturnFileCountResponse.setIsSuccess(false);

				objReturnFileCountResponse.setMsg(FAIL);

				long failCount = dateCountDataList.stream().filter(data -> FAIL.equalsIgnoreCase(data.getIsProcessed()))
						.count();

				log.warn("FAIL_RECORDS_FOUND | App={} | Date={} | FailCount={} | Attempt={}", application, date,
						failCount, entity.getCounterAttempt());

			} else {

				if(response.equalsIgnoreCase("No files available for download."))
				{
					Integer currentAttempt = entity.getCounterAttempt();
					objReturnFileCountResponse.setIsSuccess(false);

					objReturnFileCountResponse.setMsg(FAIL);

					objReturnFileCountResponse.setCounterAttempt(currentAttempt + 1);
				}
				objReturnFileCountResponse.setIsSuccess(true);

				objReturnFileCountResponse.setMsg(SUCCESS);

				objReturnFileCountResponse.setCounterAttempt(0);

				log.info("ALL_RECORDS_SUCCESS | App={} | Date={}", application, date);
			}

			/*
			 * ========================================================= STEP-9 : SAVE FINAL
			 * STATUS =========================================================
			 */
			returnFileCountResponseRepository.save(objReturnFileCountResponse);

			long totalTime = System.currentTimeMillis() - startTime;

			log.info("END :: processDateForGstr | App={} | Date={} | Success={} | Attempt={} | Time={} ms", application,
					date, entity.getIsSuccess(), entity.getCounterAttempt(), totalTime);

			return response;

		} catch (Exception ex) {

			/*
			 * ========================================================= EXCEPTION HANDLING
			 * =========================================================
			 */
			try {

				if (entity != null) {

					Integer currentAttempt = entity.getCounterAttempt();

					if (currentAttempt == null) {
						currentAttempt = 0;
					}

					entity.setCounterAttempt(currentAttempt + 1);

					entity.setIsSuccess(false);

					entity.setMsg(FAIL);

					returnFileCountResponseRepository.save(entity);
				}

			} catch (Exception dbEx) {

				log.error("DB_UPDATE_FAILED_IN_EXCEPTION_BLOCK | App={} | Date={}", application, date, dbEx);
			}

			log.error("ERROR_PROCESSING_DATE | App={} | Date={} | Error={}", application, date, ex.getMessage(), ex);

			return "FAILED";
		}
	}
	// =========================================================
	// GSTN CALL
	// =========================================================

	private String beforeGetFromGstn(String username, String date, String application) {

		String info = "FutureAttributes";

		String responseBody;

		if (application.equalsIgnoreCase("recon")) {

			responseBody = commonServiceGstrImpl.getReconFromGstn(username, date);

		} else {

			responseBody = commonServiceGstrImpl.getCommonGstrAndOtherDownloadAsm(username, date, application, info);
		}

		log.info("GSTN Response | App={} | Date={} | Response={}", application, date, responseBody);

		return responseBody;
	}

	// =========================================================
	// SAVE JSON TO DB
	// =========================================================

	public long saveDataFromJsonToDb(String application) {

		return fileDownloadHelperCommon.saveDataFromJsonToDb(application);
	}
}