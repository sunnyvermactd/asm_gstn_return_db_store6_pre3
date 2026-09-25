package com.deloitte.returns.repositoryCommon;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deloitte.returns.entity.LedgerRegistrationData;

public interface LedgerRegistrationDataRepository extends JpaRepository<LedgerRegistrationData, Long> {

}
