package com.deloitte.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deloitte.service.support.crn.ReactEntity;
import com.deloitte.service.support.crn.ReactService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/react")
@CrossOrigin
@RequiredArgsConstructor
public class ReactController {

	private final ReactService reactService;

	// GET by ID
	@GetMapping("/getValuesById")
	public ResponseEntity<ReactEntity> getValues(@RequestParam Long id) {
		return ResponseEntity.ok(reactService.getValuesById(id));
	}

	// GET all
	@GetMapping
	public ResponseEntity<List<ReactEntity>> getAll() {
		return ResponseEntity.ok(reactService.getAll());
	}

	// CREATE
	@PostMapping
	public ResponseEntity<ReactEntity> create(@RequestBody ReactEntity entity) {
		return ResponseEntity.ok(reactService.create(entity));
	}

	// UPDATE
	@PutMapping("/{id}")
	public ResponseEntity<ReactEntity> update(@PathVariable Long id, @RequestBody ReactEntity entity) {
		return ResponseEntity.ok(reactService.update(id, entity));
	}

	// DELETE
	@DeleteMapping("/{id}")
	public ResponseEntity<String> delete(@PathVariable Long id) {
		reactService.delete(id);
		return ResponseEntity.ok("Deleted successfully");
	}
}
