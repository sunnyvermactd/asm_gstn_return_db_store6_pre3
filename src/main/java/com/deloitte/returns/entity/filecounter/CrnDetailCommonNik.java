package com.deloitte.returns.entity.filecounter;

import java.time.Instant;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "crn_detail_common", schema = "filecounter")
public class CrnDetailCommonNik {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "\"details_id\"")
	private Long id;

	@Column(name = "\"crn_id\"")
	private Long idReturnCountCrnJson;

	@Column(name = "\"crn\"")
	private String crn;

	@Column(name = "\"status\"")
	private String originalStatus;

	@Column(name = "\"casetyp\"")
	private String originalCaseTyp;

	@Column(name = "\"approvauth\"")
	private String originalApprovAuth;

	@Column(name = "\"issuccess\"")
	private Boolean isSuccess;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "msg")
	private JsonNode msg;

	@Column(name = "\"updatetmstmp\"")
	private String originalUpdateTmstmp;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "\"jsondata\"")
	private JsonNode jsonData;

	@Column(name = "is_processed")
	private Boolean isProcessed;

	@Column(name = "is_future")
	private Boolean isFuture;

	@Column(name = "url")
	private String url;

	@CreationTimestamp
	@Column(name = "insert_date", updatable = false)
	private LocalDateTime insertDate;

	@Column(name = "create_date_time", updatable = false)
	private Instant createDateTime;

	@Column(name = "updated_date_time")
	private Instant updatedDateTime;

	@Column(name = "type_registration_msg") // entitytyp
	private String typeRegistrationMsg;

	@Column(name = "counter_attempt") //
	private int counterAttempt;

	@PrePersist
	protected void onCreate() {
		Instant now = Instant.now();
		this.createDateTime = now;
		this.updatedDateTime = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedDateTime = Instant.now();
	}

	@Column(name = "dof")
	private String dof;

	@Column(name = "gstin")
	private String gstin;

	@Column(name = "fy")
	private String fy;
}
