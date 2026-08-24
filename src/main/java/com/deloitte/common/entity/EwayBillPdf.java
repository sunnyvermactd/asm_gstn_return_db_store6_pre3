package com.deloitte.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table(name = "eway_bill_pdf_file", schema = "eway_live_eway_bill_new")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class EwayBillPdf {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "Ewb_nos")
	private String ewayBillNo;
	
	@Column(name = "ewb_dt")
	private String generatedDate;
	
//	@Column(name = "Ewb_nos")
//	private String generatedBy;
//	
//	@Column(name = "Ewb_nos")
//	private String validUpto;
	
	@Column(name = "fr_gstin")
	private String fromGstin;
	
	@Column(name = "fr_name")
	private String fromName;
	
	@Column(name = "fr_stat")
	private String fromState;

	@Column(name = "to_gstin")
	private String toGstin;
	
	@Column(name = "to_name")
	private String toName;
	
	@Column(name = "to_stat")
	private String toState;

	//@Column(name = "Ewb_nos")
	private String invoiceNo;
	
	//@Column(name = "Ewb_nos")
	private String invoiceDate;

//	@Column(name = "Ewb_nos")
	private String vehicleNo;
	


	@Column(name = "irn")
	private String irn;

	@Column(name = "ass_val")
	private Double taxableAmount;
	
	@Column(name = "igst_val")
	private Double igstAmount;
}

