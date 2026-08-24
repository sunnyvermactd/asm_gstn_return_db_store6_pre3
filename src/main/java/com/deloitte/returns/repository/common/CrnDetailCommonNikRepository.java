package com.deloitte.returns.repository.common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.deloitte.returns.entity.filecounter.CrnDetailCommonNik;

public interface CrnDetailCommonNikRepository extends JpaRepository<CrnDetailCommonNik, Long> {

	@Query("""
			SELECT c
			FROM CrnDetailCommonNik c
			WHERE c.jsonData IS NOT NULL
			  AND c.isSuccess = true
			  AND c.fy IS NULL
			  AND c.originalCaseTyp IN ('ADJDT', 'ADJSR', 'ADJVP')
			  AND c.counterAttempt < 3
			""")
	Page<CrnDetailCommonNik> findPending(Pageable pageable);

}
