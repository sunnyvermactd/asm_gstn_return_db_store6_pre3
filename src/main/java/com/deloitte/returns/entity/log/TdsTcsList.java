package com.deloitte.returns.entity.log;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tds_tcs_list", schema = "gst_api_registration")
@Data
public class TdsTcsList {

	@Column(name = "gstin")
	private String gstin;

	@Column(name = "ty")
	private String ty;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "jsondata")
	private JsonNode jsondata;

	@Column(name = "\"isSuccess\"")
	private String isSuccess;

	@Column(name = "\"errorMessage\"")
	private String errorMessage;

	@Column(name = "\"Tds_Tcs_Id\"")
	private Long tdsTcsId;

	@Id
	@Column(name = "\"Id\"")
	private Long id;

	@Column(name = "is_missing")
	private Boolean isMissing;

	@Column(name = "counter_attempt") //
	private int counterAttempt;

	@Column(name = "insert_tm")
	private LocalDateTime insertTm;
}
