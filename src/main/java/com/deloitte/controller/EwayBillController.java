package com.deloitte.controller;

import java.io.UnsupportedEncodingException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deloitte.returns.entity.AEwayBill.EWayBillAuthBean;
import com.deloitte.service.impl.EWayBillAuthService;
import com.deloitte.service.impl.EwayBillApiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("/EwayBill")
@Log4j2
@RequiredArgsConstructor
public class EwayBillController {

	private final EWayBillAuthService eWayBillAuthService;

	private final EwayBillApiService eWayBillApiService;

	@GetMapping("/authenticate")
	public EWayBillAuthBean authenticate()
			throws JsonMappingException, JsonProcessingException, UnsupportedEncodingException {
		EWayBillAuthBean kk = eWayBillAuthService.authenticateAndProcess();
		return kk;

	}

	@GetMapping("/schedule-PARTA")
	public String schedulePartADownload() {
		String category = "PARTA";
		return eWayBillApiService.scheduleEwayBillDownload(category);

	}

	@GetMapping("/schedule-PARTB")
	public String schedulePartBDownload() {
		String category = "PARTB";
		return eWayBillApiService.scheduleEwayBillDownload(category);

	}

}

//https://dex.ewaybillgst.gov.in/v1.3/Authenticate
// https://dex.ewaybillgst.gov.in/v1.3/api/ewayApi/GetEWBFile?action=FILECNT&ewbdt=10/04/2022&cat=PARTA
// https://dex.ewaybillgst.gov.in/v1.3/api/ewayApi/GetEWBFile?action=FILEDET&ewbdt=10/04/2022&filenum=4&cat=PARTA
// https://dex.ewaybillgst.gov.in/v1.3/api/Files/GetEWBFile/PARTA1731405965x1090957
