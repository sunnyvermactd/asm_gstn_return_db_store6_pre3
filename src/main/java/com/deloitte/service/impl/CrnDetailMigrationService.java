package com.deloitte.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deloitte.returns.entity.filecounter.CrnDetailCommonNik;
import com.deloitte.returns.repository.common.CrnDetailCommonNikRepository;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;



@Service
@RequiredArgsConstructor
public class CrnDetailMigrationService {

	private static final Logger log = LoggerFactory.getLogger(CrnDetailMigrationService.class);

	private static final int BATCH_SIZE = 500;

	private final CrnDetailCommonNikRepository repository;

	@Transactional
	public void migrateFyData() {
		log.info("Starting migration of fy data for CrnDetailCommonNik");
		Instant start = Instant.now();

		long totalProcessed = 0;

		int pageNumber = 0;

		while (true) {

			Page<CrnDetailCommonNik> page = repository.findPending(PageRequest.of(pageNumber, BATCH_SIZE));

			if (page.isEmpty()) {
				break;
			}

			for (CrnDetailCommonNik entity : page.getContent()) {
				entity.setCounterAttempt(entity.getCounterAttempt() + 1);
				try {

					JsonNode root = entity.getJsonData();

					if (root == null) {
						continue;
					}

					entity.setDof(extract(root, "dof"));
					entity.setGstin(extract(root, "gstin"));

					if (entity.getFy() == null) {
						entity.setFy(findFirstFy(root));
					}

				} catch (Exception ex) {

					log.error("Error processing id={} crn={}", entity.getId(), entity.getCrn(), ex);
				}
			}

			repository.saveAll(page.getContent());

			totalProcessed += page.getNumberOfElements();

			log.info("Batch completed. Page={}, Records={}, TotalProcessed={}", pageNumber, page.getNumberOfElements(),
					totalProcessed);

			pageNumber++;
		}

		log.info("Migration completed. Total records processed={} Time={} sec", totalProcessed,
				Duration.between(start, Instant.now()).toSeconds());
	}

	private String extract(JsonNode root, String field) {

		JsonNode node = root.get(field);

		if (node == null || node.isNull()) {
			return null;
		}

		return node.asText();
	}

	private String findFirstFy(JsonNode node) {

		if (node == null) {
			return null;
		}

		if (node.isObject()) {

			JsonNode fyNode = node.get("fy");

			if (fyNode != null && !fyNode.isNull()) {
				return fyNode.asText();
			}

			Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

			while (fields.hasNext()) {

				Map.Entry<String, JsonNode> entry = fields.next();

				String fy = findFirstFy(entry.getValue());

				if (fy != null) {
					return fy;
				}
			}
		}

		if (node.isArray()) {

			for (JsonNode child : node) {

				String fy = findFirstFy(child);

				if (fy != null) {
					return fy;
				}
			}
		}

		return null;
	}
}