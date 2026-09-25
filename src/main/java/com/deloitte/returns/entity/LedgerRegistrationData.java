package com.deloitte.returns.entity;

import java.sql.Timestamp;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ledger_registration_data", schema = "common")
public class LedgerRegistrationData {


	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String filePath;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "json_data")
	private JsonNode jsonData;

	private int fileNumber;

	private Boolean downloadFileStatus;

	private Boolean isSuccess = false;

	private Boolean isProcessed = false;

	private String action;

	private String gstin;

	private String fromDate;

	private String toDate;

	private Timestamp insertDt;

	@Column(name = "download_attempt", nullable = false)
	private Integer downloadAttempt = 0;


}
