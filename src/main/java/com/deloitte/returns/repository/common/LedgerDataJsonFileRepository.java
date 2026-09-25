package com.deloitte.returns.repository.common;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.deloitte.returns.entity.LedgerDataJsonFile;
import com.deloitte.returns.entity.type.LedgerCash.LedgerAttemptProjection;

@Repository
public interface LedgerDataJsonFileRepository extends JpaRepository<LedgerDataJsonFile, Long> {

	List<LedgerDataJsonFile> findTop100ByActionAndIsProcessedFalseOrderByIdAsc(String action);

	Optional<LedgerDataJsonFile> findByGstinAndFromDateAndToDateAndAction(String gstin, String fromDate, String toDate,
			String action);

	boolean existsByActionIgnoreCaseAndGstinAndFromDateAndToDateAndIsSuccessTrue(String action, String gstin,
			String fromDate, String toDate);

	Optional<LedgerDataJsonFile> findTopByActionIgnoreCaseAndGstinAndFromDateAndToDateOrderByIdDesc(String action,
			String gstin, String fromDate, String toDate);

	@Query(value = """
			SELECT
			    gstin,
			    COALESCE(MAX(download_attempt), 0) AS download_attempt,
			    BOOL_OR(COALESCE(is_success, false)) AS is_success
			FROM common.ledger_data_json_file
			WHERE LOWER(action) = LOWER(:action)
			  AND from_date = :fromDate
			  AND to_date = :toDate
			GROUP BY gstin
			""", nativeQuery = true)
	List<LedgerAttemptProjection> findLedgerStatusForGSTINs(@Param("action") String action,
			@Param("fromDate") String fromDate, @Param("toDate") String toDate);

	Page<LedgerDataJsonFile> findByIsSuccessNullOrIsSuccessFalseAndDownloadAttemptLessThan(int i, Pageable pageable);

	Optional<LedgerDataJsonFile> findByActionAndGstinAndFromDateAndToDate(String action, String gstin, String fromDate,
			String toDate);

	Optional<LedgerDataJsonFile> findByGstinAndFromDateAndToDateAndActionAndIsSuccess(String gstin, String fromDate,
			String toDate, String action, boolean b);

	Page<LedgerDataJsonFile> findByActionAndIsSuccessNullOrIsSuccessFalseAndDownloadAttemptLessThan(String action,
			int i, Pageable pageable);

}
