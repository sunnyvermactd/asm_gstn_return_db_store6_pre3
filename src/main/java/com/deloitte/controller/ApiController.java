package com.deloitte.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deloitte.common.bean.LastUpdateDTO;
import com.deloitte.common.bean.ReportRequestDTO;
import com.deloitte.common.bean.ReportResponseDTO;
import com.deloitte.service.impl.ReportFileServiceImpl;
import com.deloitte.service.impl.ReportServiceImpl;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiController {

	private final ReportServiceImpl reportServiceImpl;

	private final ReportFileServiceImpl reportFileServiceImpl;

	@PostMapping("/report")
	public List<ReportResponseDTO> report(@RequestBody ReportRequestDTO request) {

		log.info("Report request received. FromDate={}, ToDate={}, TY={}", request.getFromDate(), request.getToDate(),
				request.getTy());

		long start = System.currentTimeMillis();

		List<ReportResponseDTO> response = reportServiceImpl.generateReport(request);

		log.info("Report generated successfully. Records={}, TimeTaken={} ms", response.size(),
				(System.currentTimeMillis() - start));

		return response;
	}

//	@GetMapping("/last-update")
//	public List<LastUpdateDTO> getLastUpdateReport() {
//
//		return reportServiceImpl.getLastUpdateReport();
//
//	}

	@GetMapping("/last-update")
	public List<LastUpdateDTO> getLastUpdateReport() {

		return reportServiceImpl.getLastUpdateReport();

	}

	@GetMapping("/last-update-crn")
	public List<LastUpdateDTO> getLastUpdateCrnReport() {

		return reportServiceImpl.getLastUpdateCrnReport();

	}

	@GetMapping("/last-update-registration")
	public List<LastUpdateDTO> getLastUpdateRegistrationReport() {

		return reportServiceImpl.getLastUpdateRegistrationReport();

	}

	@GetMapping("/last-update-eway-bill")
	public List<LastUpdateDTO> getLastUpdateEwayBillReport() {

		return reportServiceImpl.getLastUpdateEwayBillReport();

	}

	@PostMapping("/excel")
	public void downloadExcel(@RequestBody ReportRequestDTO request, HttpServletResponse response) throws Exception {

		List<ReportResponseDTO> data = reportServiceImpl.generateReport(request);

		reportFileServiceImpl.generateExcel(data, request, response);
	}

	@PostMapping("/pdf")
	public void downloadPdf(@RequestBody ReportRequestDTO request, HttpServletResponse response) throws Exception {

		List<ReportResponseDTO> data = reportServiceImpl.generateReport(request);

		reportFileServiceImpl.generatePdf(data, request, response);
	}

}