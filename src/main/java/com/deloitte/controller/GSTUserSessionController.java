package com.deloitte.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deloitte.common.entity.GSTUserSession;
import com.deloitte.service.impl.GSTUserSessionServicesImpl;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/common/userSession")
@RequiredArgsConstructor
public class GSTUserSessionController {

	private final GSTUserSessionServicesImpl apiServices;

	private static String username = "GSTG2G18";

	@GetMapping("/authenticate/{username}")
	public ResponseEntity<GSTUserSession> authenticate(@PathVariable String username) {
		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
	}

	@Scheduled(cron = "0 01 01 * * *")
	public ResponseEntity<GSTUserSession> authenticateV1() {
		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
	}

	@Scheduled(cron = "0 08 03 * * *")
	public ResponseEntity<GSTUserSession> authenticateV2() {
		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
	}

	@Scheduled(cron = "0 01 05 * * *")
	public ResponseEntity<GSTUserSession> authenticateV3() {
		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
	}

	@Scheduled(cron = "0 01 07 * * *")
	public ResponseEntity<GSTUserSession> authenticateV4() {
		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
	}

	@Scheduled(cron = "0 33 10 * * *")
	public ResponseEntity<GSTUserSession> authenticateV5() {
		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
	}

	@Scheduled(cron = "0 16 12 * * *")
	public ResponseEntity<GSTUserSession> authenticateV6() {
		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
	}

	@Scheduled(cron = "0 01 15 * * *")
	public ResponseEntity<GSTUserSession> authenticateV7() {
		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
	}

	@Scheduled(cron = "0 01 18 * * *")
	public ResponseEntity<GSTUserSession> authenticateV8() {
		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
	}

	@Scheduled(cron = "0 01 21 * * *")
	public ResponseEntity<GSTUserSession> authenticateV9() {
		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
	}

	@Scheduled(cron = "0 01 23 * * *")
	public ResponseEntity<GSTUserSession> authenticateV10() {
		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
	}
//	
//	@Scheduled(cron = "0 01 11 * * *")
//	public ResponseEntity<GSTUserSession> authenticateV11() {
//		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
//	}
//	
//	@Scheduled(cron = "0 01 12 * * *")
//	public ResponseEntity<GSTUserSession> authenticate12() {
//		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
//	}
//	
//	@Scheduled(cron = "0 01 13 * * *")
//	public ResponseEntity<GSTUserSession> authenticate13() {
//		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
//	}
//	
//	@Scheduled(cron = "0 01 15 * * *")
//	public ResponseEntity<GSTUserSession> authenticate15() {
//		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
//	}
//	
//	@Scheduled(cron = "0 01 18 * * *")
//	public ResponseEntity<GSTUserSession> authenticate18() {
//		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
//	}
//	
//	@Scheduled(cron = "0 01 20 * * *")
//	public ResponseEntity<GSTUserSession> authenticate20() {
//		return new ResponseEntity<>(apiServices.createUserSession(username), HttpStatus.CREATED);
//	}
}
