package com.deloitte.returns.entity.type.LedgerCash;

public interface LedgerAttemptProjection {
	String getGstin();

	Integer getDownloadAttempt();

	Boolean getIsSuccess();

}
