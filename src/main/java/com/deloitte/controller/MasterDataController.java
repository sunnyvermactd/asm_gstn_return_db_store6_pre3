package com.deloitte.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deloitte.common.entity.MasterData;
import com.deloitte.service.impl.MasterDataServiceImpl;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/masterData")
@RequiredArgsConstructor
public class MasterDataController {

	private final MasterDataServiceImpl masterDataServiceImpl;

	@PostMapping("/create")
	public ResponseEntity<MasterData> create(@RequestBody MasterData masterData) {
		return new ResponseEntity<>(masterDataServiceImpl.create(masterData), HttpStatus.CREATED);
	}

}
