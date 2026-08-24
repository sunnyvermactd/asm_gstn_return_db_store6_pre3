package com.deloitte.returns.repositoryCommon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.deloitte.common.entity.EwayBillPdf;

@Repository
public interface EwayBillPdfRepository extends JpaRepository<EwayBillPdf, Long> {
}
