package com.deloitte.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deloitte.service.impl.CommonEnforecementService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/enforcement")
@RequiredArgsConstructor
public class CommonEnforcementController {

	private final CommonEnforecementService commonEnforecementService;

	@GetMapping("/downloadLatest")
	public ResponseEntity<String> getCommonGstrAndOtherDownloadLatest(@RequestParam String username, // GSTG2G22
			@RequestParam String date, @RequestParam String application, @RequestParam String gstin,
			@RequestParam String retPeriod, @RequestParam String secName) {
		return new ResponseEntity<>(commonEnforecementService.getCommonGstrAndOtherDownloadLatest(username, date,
				application, gstin, retPeriod, secName), HttpStatus.OK);
	}

	// INSERT INTO enforcement_officer_gstr1.gstin(create_date_time, gstin,
	// is_processed, ret_period, updated_date_time,found_info) VALUES (now(),
	// '22AAAAB6252A1ZL', false, '092024', now(),true);

	@GetMapping("/getEnforcementGstr1Details")
	public ResponseEntity<String> getEnforcementGstr1DetailsFromGSTN(@RequestParam(name = "username") String username,
			@RequestParam(name = "section") List<String> section) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementGstr1DetailsAndSave(username, section),
				HttpStatus.OK);
	}

	@GetMapping("/getEnforcementGstr3bDetails")
	public ResponseEntity<String> getEnforcementGstr3bDetailsFromGSTN(@RequestParam String username) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementGstr3BDetailsAndSave(username),
				HttpStatus.OK);
	}

	@GetMapping("/getEnforcementGstr4Details")
	public ResponseEntity<String> getEnforcementGstr4DetailsFromGSTN(@RequestParam String username) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementGstr4DetailsAndSave(username),
				HttpStatus.OK);
	}

	@GetMapping("/getEnforcementGstr5Details")
	public ResponseEntity<String> getEnforcementGstr5DetailsFromGSTN(@RequestParam String username) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementGstr5DetailsAndSave(username),
				HttpStatus.OK);
	}

	@GetMapping("/getEnforcementGstr6Details")
	public ResponseEntity<String> getEnforcementGstr6DetailsFromGSTN(@RequestParam String username) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementGstr6DetailsAndSave(username),
				HttpStatus.OK);
	}

	@GetMapping("/getEnforcementGstr7Details")
	public ResponseEntity<String> getEnforcementGstr7DetailsFromGSTN(@RequestParam String username) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementGstr7DetailsAndSave(username),
				HttpStatus.OK);
	}

	@GetMapping("/getEnforcementGstr8Details")
	public ResponseEntity<String> getEnforcementGstr8DetailsFromGSTN(@RequestParam String username) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementGstr8DetailsAndSave(username),
				HttpStatus.OK);
	}

	@GetMapping("/getEnforcementGstr9Details")
	public ResponseEntity<String> getEnforcementGstr9DetailsFromGSTN(@RequestParam String username) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementGstr9DetailsAndSave(username),
				HttpStatus.OK);
	}

	@GetMapping("/getEnforcementGstr1SumDetails")
	public ResponseEntity<String> getEnforcementGstr1SumDetailsFromGSTN(@RequestParam String username) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementGstr1SumDetailsAndSave(username),
				HttpStatus.OK);
	}

	// INSERT INTO enforcement_officer_gstr2a.gstin(create_date_time, gstin,
	// is_processed, ret_period, updated_date_time,found_info) VALUES (now(),
	// '22AAKCS6296B1ZC', false, '072024', now(),true);
	@GetMapping("/getEnforcementGstr2ADetails")
	public ResponseEntity<String> getEnforcementGstrADetailsFromGSTN(@RequestParam String username,
			@RequestParam List<String> section) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementGstr2ADetailsAndSave(username, section),
				HttpStatus.OK);
	}

	// INSERT INTO enforcement_officer_rsp.gstin(create_date_time,
	// updated_date_time, gstin, ret_period, is_processed, found_info) VALUES
	// (now(), now(), '22ABCFS9984L2ZH', '2023-24', FALSE, TRUE);
	@GetMapping("/getEnforcementOfficerRecordSearchPayments")
	public ResponseEntity<String> getEnforcementOfficerRecordSearchPaymentsFromGSTN(@RequestParam String username) {
		return new ResponseEntity<>(
				commonEnforecementService.getEnforcementOfficerRecordSearchPaymentsFromGSTNAndSave(username),
				HttpStatus.OK);
	}

	@GetMapping("/getEnforcementOfficerRecordSearchEnforcement")
	public ResponseEntity<String> getEnforcementOfficerRecordSearchEnforcementFromGSTN(@RequestParam String username) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementOfficerRecordSearchEnforcement(username),
				HttpStatus.OK);
	}

	@GetMapping("/getEnforcementOfficerRecordSearchRegisteration")
	public ResponseEntity<String> getEnforcementOfficerRecordSearchRegisterationFromGSTN(
			@RequestParam String username) {
		return new ResponseEntity<>(commonEnforecementService.getEnforcementOfficerRecordSearchRegistration(username),
				HttpStatus.OK);
	}

	// INSERT INTO enforcement_officer_rsr.gstin(create_date_time,
	// updated_date_time, gstin, ret_period, is_processed, found_info) VALUES
	// (now(), now(), '22JBPP01091G1DD', '2023-2024', FALSE, TRUE);
	@GetMapping("/getEnforcementOfficerRecordSearchReturns")
	public ResponseEntity<String> getEnforcementOfficerRecordSearchReturnsFromGSTN(@RequestParam String username) {
		return new ResponseEntity<>(
				commonEnforecementService.getEnforcementOfficerRecordSearchReturnsFromGSTNAndSave(username),
				HttpStatus.OK);
	}

	@GetMapping("/saveDataFromJsonToDb")
	public String saveDataFromJsonToDb() throws IOException {
		String info = commonEnforecementService.saveDataFromJsonToDb();
		return "Data imported successfully from " + info;
	}

}
