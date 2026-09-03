package com.deloitte.service.forest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deloitte.repository.forest.ApiMonthlyTrackingRepository;
import com.deloitte.repository.forest.ForestPaymentDetailsRepository;
import com.deloitte.returns.entity.Forest.ApiMonthlyTracking;
import com.deloitte.returns.entity.Forest.ForestPaymentDetails;
import com.deloitte.returns.entity.Forest.Model.ForestApiResponse;
import com.deloitte.returns.entity.Forest.Model.ForestPaymentDto;
import com.deloitte.service.utility.ForestApiClient;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ForestApiService {

	@Autowired
	private ForestApiClient apiClient;

	@Autowired
	private ForestPaymentDetailsRepository repository;

	@Autowired
	private ApiMonthlyTrackingRepository trackingRepo;

	public void downloadAndSaveMonthlyData() {

		//LocalDate start = LocalDate.of(2020, 4, 1);
		LocalDate start = LocalDate.of(2024, 10, 18);
		LocalDate end = LocalDate.of(2026, 8, 23);

		while (!start.isAfter(end)) {

			LocalDate fromDate = start.withDayOfMonth(1);
			LocalDate toDate   = start.withDayOfMonth(start.lengthOfMonth());

			boolean isAlreadySuccess =
			        trackingRepo.existsByFromDateAndToDateAndStatus(
			                fromDate, toDate, "SUCCESS");

			if (isAlreadySuccess) {
			    log.info("⏭ Skipping already SUCCESS processed month: {} to {}", fromDate, toDate);
			    start = start.plusMonths(1);
			    continue;
			}

			// else → process (status != SUCCESS OR no record)
			log.info("▶ Processing month: {} to {}", fromDate, toDate);

			ApiMonthlyTracking track = new ApiMonthlyTracking();
			track.setFromDate(fromDate);
			track.setToDate(toDate);
			track.setStartedAt(LocalDateTime.now());
			track.setStatus("RUNNING");

			trackingRepo.save(track);

			int totalCount = 0;

			try {
				int page = 1;
				boolean hasMore = true;

				while (hasMore) {

					ForestApiResponse response = apiClient.fetchWithRetry(fromDate, toDate, page, 3, 15);

					if (response == null || response.getResponse() == null)
						break;

					List<ForestPaymentDto> list = response.getResponse();
					savePageData(list);

					totalCount += list.size();
					hasMore = response.isHas_more();
					page++;
				}

				track.setTotalRecords(totalCount);
				track.setStatus("SUCCESS");
				track.setCompletedAt(LocalDateTime.now());

				trackingRepo.save(track);

				log.info("✅ Completed {} to {} → {} records", fromDate, toDate, totalCount);

			} catch (Exception ex) {

				track.setStatus("FAILED");
				track.setCompletedAt(LocalDateTime.now());
				track.setErrorMessage(ex.getMessage());

				trackingRepo.save(track);

				log.error("❌ Failed {} to {} : {}", fromDate, toDate, ex.getMessage());
			}

			start = start.plusMonths(1);
		}

		log.info("🎯 All Monthly Data Extraction Completed");
	}

	@Transactional
	public void savePageData(List<ForestPaymentDto> list) {

		List<ForestPaymentDetails> entities = list.stream().map(this::mapToEntity).filter(Objects::nonNull).toList();

		repository.saveAll(entities);
	}


	
	private ForestPaymentDetails mapToEntity(ForestPaymentDto dto) {
		try {
			ForestPaymentDetails e = new ForestPaymentDetails();
			e.setModuleName(dto.getModulename());
			e.setMineralName(dto.getMineral_name());
			e.setStakeholder(dto.getStakeholder());
			e.setCpin(dto.getCpin());
			e.setPan(dto.getPan());
			e.setGstNo(dto.getGst_no());
			e.setDfoGstNo(dto.getDfo_gstno());
			e.setPaymentType(dto.getPayment_type());
			e.setAmount(new BigDecimal(dto.getAmount()));
			e.setPaymentDate(LocalDate.parse(dto.getPayment_date()));
			return e;
		} catch (Exception ex) {
			log.error("Mapping failed: {}", dto);
			return null;
		}
	}
	
	//@Transactional
	public void downloadAndSaveDailyData() {

	    long jobStartTime = System.currentTimeMillis();
	    log.info("JOB START :: Daily Data Download Job Initiated");

	    LocalDate startDate = LocalDate.of(2022, 10, 18);
	    LocalDate endDate = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);

	    while (!startDate.isAfter(endDate)) {

	        String traceId = UUID.randomUUID().toString().substring(0, 8); // short trace
	        long dayStartTime = System.currentTimeMillis();

	        LocalDate fromDate = startDate;
	        LocalDate toDate = startDate;

	        log.info("TRACE_ID={} :: START :: Processing Date={}", traceId, fromDate);

	        boolean alreadyProcessed =
	                trackingRepo.countRecords(fromDate, toDate, "SUCCESS") > 0;

	        if (alreadyProcessed) {
	            log.info("TRACE_ID={} :: SKIPPED :: Date={} already SUCCESS", traceId, fromDate);
	            startDate = startDate.plusDays(1);
	            continue;
	        }

	        ApiMonthlyTracking tracking = new ApiMonthlyTracking();
	        tracking.setFromDate(fromDate);
	        tracking.setToDate(toDate);
	        tracking.setStartedAt(LocalDateTime.now());
	        tracking.setStatus("RUNNING");
	        tracking.setTotalRecords(0);

	        tracking = trackingRepo.save(tracking);

	        int totalCount = 0;
	        int page = 1;
	        boolean hasMore = true;

	        try {

	            while (hasMore) {

	                long pageStartTime = System.currentTimeMillis();

	                log.info("TRACE_ID={} :: PAGE_START :: Date={} Page={}", traceId, fromDate, page);

	                ForestApiResponse response =
	                        apiClient.fetchWithRetry(fromDate, toDate, page, 3, 15);

	                if (response == null || response.getResponse() == null) {
	                    log.warn("TRACE_ID={} :: NULL_RESPONSE :: Date={} Page={}", traceId, fromDate, page);
	                    break;
	                }

	                List<ForestPaymentDto> records = response.getResponse();

	                if (records.isEmpty()) {
	                    log.info("TRACE_ID={} :: NO_DATA :: Date={} Page={}", traceId, fromDate, page);
	                    break;
	                }

	                savePageData(records);

	                totalCount += records.size();
	                hasMore = response.isHas_more();

	                long pageEndTime = System.currentTimeMillis();

	                log.info(
	                        "TRACE_ID={} :: PAGE_END :: Date={} Page={} Records={} Total={} TimeTaken={} ms",
	                        traceId, fromDate, page, records.size(), totalCount,
	                        (pageEndTime - pageStartTime)
	                );

	                page++;
	            }

	            tracking.setTotalRecords(totalCount);
	            tracking.setStatus("SUCCESS");
	            tracking.setCompletedAt(LocalDateTime.now());
	            trackingRepo.save(tracking);

	            long dayEndTime = System.currentTimeMillis();

	            log.info(
	                    "TRACE_ID={} :: SUCCESS :: Date={} TotalRecords={} TimeTaken={} ms",
	                    traceId, fromDate, totalCount, (dayEndTime - dayStartTime)
	            );

	        } catch (Exception ex) {

	            log.error(
	                    "TRACE_ID={} :: FAILED :: Date={} Error={}",
	                    traceId, fromDate, ex.getMessage(), ex
	            );

	            tracking.setStatus("FAILED");
	            tracking.setCompletedAt(LocalDateTime.now());
	            tracking.setErrorMessage(ex.getMessage());
	            trackingRepo.save(tracking);
	        }

	        startDate = startDate.plusDays(1);
	    }

	    long jobEndTime = System.currentTimeMillis();

	    log.info("JOB END :: Daily Data Download Completed | TotalTime={} ms",
	            (jobEndTime - jobStartTime));
	}
}
