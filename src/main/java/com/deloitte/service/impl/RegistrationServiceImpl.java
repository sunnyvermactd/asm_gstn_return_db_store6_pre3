package com.deloitte.service.impl;

import java.time.format.DateTimeFormatter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.deloitte.common.bean.GSTCommonResponseBean;
import com.deloitte.common.constant.Constants;
import com.deloitte.common.entity.APIDetails;
import com.deloitte.common.entity.GSTUserSession;
import com.deloitte.common.entity.MasterData;
import com.deloitte.returns.entity.GstinCollectionDetailsOnTheFly;
import com.deloitte.returns.entity.ReturnComparisonReportGstin;
import com.deloitte.returns.entity.ReturnComparisonReportGstinJson;
import com.deloitte.returns.entity.DownloadDocument.FileNameDocument;
import com.deloitte.returns.entity.DownloadDocument.RegisDcupdtlsTesting;
import com.deloitte.returns.entity.DownloadDocument.RegistrationDownloadDocument;
import com.deloitte.returns.entity.log.GstinCollectionDetails;
import com.deloitte.returns.entity.log.LedgerInitialJson;
import com.deloitte.returns.entity.log.TdsTcsList;
import com.deloitte.returns.entity.registration.RegDocuments;
import com.deloitte.returns.entity.registration.RentActivePpbzdtls;
import com.deloitte.returns.repository.GstinCollectionDetailsOnTheFlyRepository;
import com.deloitte.returns.repository.GstinCollectionDetailsRepository;
import com.deloitte.returns.repository.RegDocumentsRepository;
import com.deloitte.returns.repository.RegisDcupdtlsRepository;
import com.deloitte.returns.repository.RegistrationDownloadDocumentRepository;
import com.deloitte.returns.repository.ReturnComparisonReportGstinJsonRepository;
import com.deloitte.returns.repository.ReturnComparisonReportGstinRepository;
import com.deloitte.returns.repository.TdsTcsListRepository;
import com.deloitte.returns.repository.common.LedgerInitialJsonRepository;
import com.deloitte.returns.repositoryCommon.FileNameDocumentRepository;
import com.deloitte.returns.service.AuthenticationHelper;
import com.deloitte.returns.service.GstUserSessionServices;
import com.deloitte.service.helper.REST.call.RestClientHelper;
import com.deloitte.service.support.AESEncryption;
import com.deloitte.service.utility.SftpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class RegistrationServiceImpl {

	// common-autowired-start
	@Value("${normal-compostion.file.location}")
	private String normalAndCompositionFileLocation;

	private static final String BASE_PATH = "/var/gst_files/GST_FILES/Return_Auto/Registration_Deloitte/filter";

	@Value("${tds-tcs.file.location}")
	private String tdsAndTcsFileLocation;

	@Value("${document.file.location}")
	private String documentFileLocation;

	@Autowired
	private MasterDataServiceImpl masterDataService;

	@Autowired
	private GstUserSessionServices gstUserSessionServices;

	@Autowired
	private AuthenticationHelper authenticationHelper;

	@Autowired
	private RestClientHelper restClient;

	@Autowired
	private APIDetailsImpl apiDetailsImpl;

	@Autowired
	private RegistrationDownloadDocumentRepository registrationDownloadDocumentRepository;

	@Autowired
	private RegisDcupdtlsRepository regisDcupdtlsRepository;

	@Autowired
	private FileNameDocumentRepository fileNameDocumentRepository;

	@Autowired
	private RegDocumentsRepository regDocumentsRepository;

	@Autowired
	private ReturnComparisonReportGstinJsonRepository returnComparisonReportGstinJsonRepository;

	@Autowired
	private GstinCollectionDetailsRepository gstinCollectionDetailsRepository;

	@Autowired
	private TdsTcsListRepository tdsTcsListRepository;

	@Autowired
	private ReturnComparisonReportGstinRepository returnComparisonReportGstinRepository;

	@Autowired
	private GstinCollectionDetailsOnTheFlyRepository gstinCollectionDetailsOnTheFlyRepository;

	@Autowired
	private LedgerInitialJsonRepository ledgerInitialJsonRepository;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final ConcurrentHashMap<String, Boolean> folderCache = new ConcurrentHashMap<>();

	private static final int BATCH_SIZE = 10; // Adjust the batch size as needed
	private static final int THREAD_POOL_SIZE = 150; // Adjust the number of threads as needed

	// anyId=GSTIM
	// anyIdty=type like normal tds tcs or composition
	public String getAllByIdtyAndIdIntoDrive(String anyId, String anyIdty) {
		String username = "GSTG2G22";
		log.info("Method{} :getAllByIdtyAndIdIntoDrive");
		String getReturnEntity = null;

		MasterData masterData = masterDataService.getMasterdatabyName(username);

		if (masterData == null) {
			log.error("User {} not found in Master Data Table", username);
			return "ERROR: User not found in Master Data";
		}

		// GSTUserSession gstUserSessions =
		// gstUserSessionServices.getUserSessionsByName(username);
		GSTUserSession gstUserSessions = gstUserSessionServices.getUserSessionsByName(username);

		if (gstUserSessions == null) {
			log.error("User {} session is not authenticated", username);
			return "ERROR: Session not authenticated";
		}

		getReturnEntity = getEntityRequestIntoDrive(masterData, anyIdty, anyId, gstUserSessions);

		System.out.println(getReturnEntity);
		return getReturnEntity;
	}

	// 1
	private String getEntityRequestIntoDrive(MasterData masterData, String anyIdty, String anyId,
			GSTUserSession gstUserSessions) {
		APIDetails apiDetailsForFileCount = apiDetailsImpl.findByName(Constants.GET_REGISTRATION_NORMAL_TAX_PAYER);
		HttpHeaders headersForFileDetails = authenticationHelper.getDefaultHeaders(masterData,
				gstUserSessions.getAuthToken(), apiDetailsForFileCount.getApiContentType());
		Map<String, String> paramsForFileDetails = getParamsForRequestEntity(masterData, anyIdty, anyId,
				apiDetailsForFileCount);
		String pathForFileDetails = authenticationHelper.getUriWithParam(
				authenticationHelper.getFullPath(masterData, apiDetailsForFileCount), paramsForFileDetails);
		GSTCommonResponseBean responseEntity = new GSTCommonResponseBean();
		responseEntity = restClient.get(pathForFileDetails, GSTCommonResponseBean.class, headersForFileDetails);
		if (null == responseEntity) {
			log.error("User {} session is not authenticated", responseEntity);
			return "ERROR: Session not authenticated";
		}
		String directoryPath = null;
		if (anyIdty.equalsIgnoreCase("UITD") || anyIdty.equalsIgnoreCase("UITC")) {
			directoryPath = tdsAndTcsFileLocation;
		} else {
			directoryPath = normalAndCompositionFileLocation;
		}

		String filePath = directoryPath + "\\" + anyId + ".json";
		Path path = Paths.get(filePath);
		if (Files.exists(path)) {
			String reason = "File already exists: " + filePath;
			log.debug(reason);
			return new String(" GSTN: " + anyId + " Data already exists.");
		}
		if (responseEntity.getRek() != null) {
			try {
				String jsonData = AESEncryption.baseDecode(Objects.requireNonNull(responseEntity).getData());
				Files.write(Paths.get(filePath), jsonData.getBytes());
				log.info("JSON data saved to file: " + filePath);
			} catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			return new String(" GSTN: " + anyId + " Data has not been added. Failed");
		}

		return new String(" GSTN: " + anyId + " Data has been added successfully");
	}

	private Map<String, String> getParamsForRequestEntity(MasterData masterData, String anyIdty, String anyId,
			APIDetails apiDetailsForFileCount) {

		Map<String, String> params = new HashMap<String, String>();
		params.put("action", apiDetailsForFileCount.getApiAction());
		params.put("state_cd", masterData.getStateCd());
		params.put("idty", anyIdty);
		params.put("id", anyId);
		return params;
	}

////////////////////////////////////////////////////////////////////////////////////////////////////////
	public String getDownloadDocumentViaGstin(RegDocuments regDocument, String username) {

		log.info("Started document download for gstin={} documentId={}", regDocument.getGstin(),
				regDocument.getDocumentId());

		try {

			MasterData masterData = masterDataService.getMasterdatabyName(username);

			if (masterData == null) {
				return "Master data not found";
			}

			GSTUserSession session = gstUserSessionServices.getUserSessionsByName(username);

			if (session == null) {
				return "Session not authenticated";
			}

			return getDownloadDataRequestIntoDrive(masterData, regDocument, session);

		} catch (Exception e) {

			log.error("Download failed for documentId={}", regDocument.getDocumentId(), e);

			saveError(regDocument, e.getMessage());

			return "Failed";
		}
	}

	private String getDownloadDataRequestIntoDrive(MasterData masterData, RegDocuments doc,
			GSTUserSession gstUserSessions) {

		try {

			log.info("Started document download | gstin={} | documentId={}", doc.getGstin(), doc.getDocumentId());

			APIDetails apiDetails = apiDetailsImpl.findByName(Constants.GET_REGISTRATION_DOWNLOAD_DOCUMENT);

			HttpHeaders headers = authenticationHelper.getDefaultHeaders(masterData, gstUserSessions.getAuthToken(),
					apiDetails.getApiContentType());

			Map<String, String> params = getParamsForRequestDownloadDataEntity(masterData, doc.getDocumentId(),
					apiDetails);

			String apiPath = authenticationHelper
					.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails), params);

			log.info("Calling GST API: {}", apiPath);

			GSTCommonResponseBean response = restClient.get(apiPath, GSTCommonResponseBean.class, headers);

			if (response == null || response.getData() == null) {
				log.error("Null response received from API for documentId={}", doc.getDocumentId());

				updateErrorRecord(doc, "Null response received from API");
				return "No data received";
			}

			byte[] fileBytes = Base64.getDecoder().decode(response.getData());

			// -----------------------------------------
			// OS Independent Folder Path
			// -----------------------------------------
			String folderPath = Paths.get(documentFileLocation, doc.getYy(), doc.getMm(), doc.getDd(),
					doc.getRegSection(), doc.getSectionType()).toString();

			Path directoryPath = Paths.get(folderPath);

			if (!Files.exists(directoryPath)) {
				Files.createDirectories(directoryPath);
				log.info("Directory created: {}", folderPath);
			}

			// -----------------------------------------
			// Dynamic File Name
			// -----------------------------------------
			String fileNameWithoutExt = doc.getGstin() + "_" + doc.getRegSection() + "_" + doc.getSectionType() + "_"
					+ doc.getYy() + doc.getMm() + doc.getDd() + "_" + doc.getDocumentId();

			String extension = getFileExtension(doc.getCt());

			if (extension == null) {
				updateErrorRecord(doc, "Unsupported document type: " + doc.getCt());
				return "Unsupported document type";
			}

			Path finalPath = Paths.get(folderPath, fileNameWithoutExt + extension);

			// -----------------------------------------
			// Skip if file already exists
			// -----------------------------------------
			if (Files.exists(finalPath)) {

				log.info("File already exists for documentId={} path={}", doc.getDocumentId(), finalPath);

				updateSuccessRecord(doc, folderPath, fileNameWithoutExt);

				return "File already exists";
			}

			// -----------------------------------------
			// Write file
			// -----------------------------------------
			Files.write(finalPath, fileBytes, StandardOpenOption.CREATE);

			// -----------------------------------------
			// Update existing DB record
			// -----------------------------------------
			updateSuccessRecord(doc, folderPath, fileNameWithoutExt);

			log.info("File downloaded successfully | documentId={} | path={}", doc.getDocumentId(), finalPath);

			return "File downloaded successfully";

		} catch (Exception e) {

			log.error("File download failed | gstin={} | documentId={}", doc.getGstin(), doc.getDocumentId(), e);

			updateErrorRecord(doc, e.getMessage());

			return "Failed";
		}
	}

	private void updateSuccessRecord(RegDocuments doc, String folderPath, String fileName) {

		doc.setFilePath(folderPath);
		doc.setFileName(fileName);
		doc.setIsSuccess(true);
		doc.setErrorMsg(null);
		doc.setInsertDt(new java.sql.Date(System.currentTimeMillis()));

		regDocumentsRepository.save(doc);

		log.info("DB record updated successfully for documentId={}", doc.getDocumentId());
	}

	private void updateErrorRecord(RegDocuments doc, String errorMessage) {

		try {
			ObjectMapper mapper = new ObjectMapper();

			ObjectNode errorNode = mapper.createObjectNode();

			errorNode.put("error_message", errorMessage);

			doc.setIsSuccess(false);
			doc.setErrorMsg(errorNode);
			doc.setInsertDt(new java.sql.Date(System.currentTimeMillis()));

			regDocumentsRepository.save(doc);

			log.error("Error updated in DB for documentId={} error={}", doc.getDocumentId(), errorMessage);

		} catch (Exception e) {
			log.error("Failed updating error record for documentId={}", doc.getDocumentId(), e);
		}
	}

	private String getFileExtension(String contentType) {

		if ("application/pdf".equalsIgnoreCase(contentType)) {
			return ".pdf";
		}

		if ("image/jpeg".equalsIgnoreCase(contentType)) {
			return ".jpg";
		}

		if ("image/png".equalsIgnoreCase(contentType)) {
			return ".png";
		}

		return null;
	}

	private void saveError(RegDocuments doc, String errorMessage) {

		try {
			ObjectMapper mapper = new ObjectMapper();

			ObjectNode errorNode = mapper.createObjectNode();

			errorNode.put("error_message", errorMessage);

			doc.setIsSuccess(false);
			doc.setErrorMsg(errorNode);
			doc.setInsertDt(new java.sql.Date(System.currentTimeMillis()));

			regDocumentsRepository.save(doc);

		} catch (Exception ex) {
			log.error("Failed to save error log for documentId={}", doc.getDocumentId(), ex);
		}
	}

	private String getDownloadDataRequestIntoDrive(MasterData masterData, String gstin, String documentId,
			String documentType, GSTUserSession gstUserSessions) {

		APIDetails apiDetailsForFileCount = apiDetailsImpl.findByName(Constants.GET_REGISTRATION_DOWNLOAD_DOCUMENT);

		HttpHeaders headersForFileDetails = authenticationHelper.getDefaultHeaders(masterData,
				gstUserSessions.getAuthToken(), apiDetailsForFileCount.getApiContentType());

		Map<String, String> paramsForFileDetails = getParamsForRequestDownloadDataEntity(masterData, documentId,
				apiDetailsForFileCount);

		String pathForFileDetails = authenticationHelper.getUriWithParam(
				authenticationHelper.getFullPath(masterData, apiDetailsForFileCount), paramsForFileDetails);

		log.info("Fetching document from API: " + pathForFileDetails);

		GSTCommonResponseBean responseEntity = restClient.get(pathForFileDetails, GSTCommonResponseBean.class,
				headersForFileDetails);

		List<FileNameDocument> fileDetailsList = new ArrayList<>();
		// RegistrationDownloadDocument registrationDownloadDocumentTemp = null;

		if (responseEntity == null) {
			log.error("Null response received from API");
		}

		String filePath = Paths.get(documentFileLocation, gstin + "_" + documentId + ".jpg").toString();
		Path path = Paths.get(filePath);

		if (Files.exists(path)) {
			log.debug("File already exists: " + filePath);
			return "GSTN: " + documentId + " Data already exists.";
		}

		if (responseEntity.getRek() != null && responseEntity.getData() != null) {

			try {
				byte[] fileBytes = Base64.getDecoder().decode(responseEntity.getData());
				Long docIdLong = Long.valueOf(documentId);

				// external add
				// log.info("Document Type from API: " + responseEntity);
				log.info("Document Type from API: " + documentType);
				log.info("REK: " + responseEntity.getRek());
				log.info("Data length: "
						+ (responseEntity.getData() != null ? responseEntity.getData().length() : "null"));

				// RegistrationDownloadDocument registrationDownloadDocumentTemp = null;

				File directory = new File(documentFileLocation);
				if (!directory.exists())
					directory.mkdirs();

				if (documentType.equalsIgnoreCase("image/jpeg") || documentType.equalsIgnoreCase("image/png")) {

					String extension = documentType.equalsIgnoreCase("image/png") ? "png" : "jpg";

					String imageFilePath = Paths.get(documentFileLocation, gstin + "_" + documentId + "." + extension)
							.toString();

					BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileBytes));

					if (image == null) {
						return "GSTN: " + documentId + " Invalid image data.";
					}
					File outputFile = new File(imageFilePath);
					ImageIO.write(image, extension, outputFile);

					String imageFileName = getFileName(imageFilePath);
					String folderName = getDocumentFolderName(documentId);
					if (!fileAlreadySaved(gstin, imageFileName)) {
						fileDetailsList.add(docdocumentFileDetails(gstin, imageFileName));
						fileNameDocumentRepository.saveAll(fileDetailsList);
					} else {
						log.info("Record already exists in FileNameDocument: " + imageFileName);
					}

					RegisDcupdtlsTesting doc = regisDcupdtlsRepository.findFirstByGstinAndJsonIdDcupdtls(gstin,
							docIdLong);
					if (doc != null) {
						doc.setIsProcessed(true);
						doc.setFoundInfo(true);
						regisDcupdtlsRepository.save(doc);
					}

					return "GSTN: " + documentId + " Image has been saved successfully.";
				}

				else if (documentType.equalsIgnoreCase("application/pdf")) {
					String pdfFilePath = Paths.get(documentFileLocation, gstin + "_" + documentId + ".pdf").toString();
					try (FileOutputStream fos = new FileOutputStream(pdfFilePath)) {
						fos.write(fileBytes);
					}

					String pdfFileName = getFileName(pdfFilePath);
					String folderName = getDocumentFolderName(documentId);
					if (!fileAlreadySaved(gstin, pdfFileName)) {
						fileDetailsList.add(docdocumentFileDetails(gstin, pdfFileName));
						fileNameDocumentRepository.saveAll(fileDetailsList);
					} else {
						log.info("Record already exists in FileNameDocument: " + pdfFileName);
					}

					RegisDcupdtlsTesting doc = regisDcupdtlsRepository.findFirstByGstinAndJsonIdDcupdtls(gstin,
							docIdLong);
					if (doc != null) {
						doc.setIsProcessed(true);
						doc.setFoundInfo(true);
						regisDcupdtlsRepository.save(doc);
					}
					return "GSTN: " + documentId + " PDF has been saved successfully.";
				} else {
					return "GSTN: " + documentId + " Unsupported document type.";
				}
			} catch (Exception e) {
				log.error("Error saving file for GSTN: " + documentId, e);
				return "GSTN: " + documentId + " Failed to save the file.";
			}
		}
		return "GSTN: " + documentId + " Data has not been added. Failed.";
	}

	private Map<String, String> getParamsForRequestDownloadDataEntity(MasterData masterData, String documentId,
			APIDetails apiDetailsForFileCount) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("action", apiDetailsForFileCount.getApiAction());
		params.put("state_cd", masterData.getStateCd());
		params.put("docid", documentId);
		return params;

	}

	private Map<String, String> getParamsForRequestGetComparisonReport(String gstin, String year) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "COMPREPORT");
		params.put("fy", year);
		params.put("gstin", gstin);
		return params;

	}

	private String getFileName(String fullPath) {
		return Paths.get(fullPath).getFileName().toString();
	}

	private boolean fileAlreadySaved(String gstin, String fileName) {
		return fileNameDocumentRepository.existsByGstinAndFileName(gstin, fileName);
	}

	private String getDocumentFolderName(String documentId) {
		String baseFolder = documentFileLocation;
		StringBuilder path = new StringBuilder(baseFolder)
				.append(RegistrationDownloadDocument.class.getSimpleName().toUpperCase()).append("\\")
				.append(documentId).append("\\");
		return path.toString();
	}

	private FileNameDocument docdocumentFileDetails(String gstin, String fileName) {
		FileNameDocument filedetails = new FileNameDocument();
		filedetails.setIsProcessed(false);
		filedetails.setFileName(fileName);

		String baseFolder = documentFileLocation;
		if (fileName.startsWith("\\") || fileName.startsWith("/")) {
			fileName = fileName.substring(1);
		}
		filedetails.setFilePath(baseFolder + File.separator + fileName);

		filedetails.setGstin(gstin);
		filedetails.setApplication("REGISTRATIONDOWNLOADDOCUMENT");
		return filedetails;
	}

	public String documentFileToDatabase() throws IOException {
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

		List<FileNameDocument> listOfFiles = fileNameDocumentRepository.findAllByIsProcessedFalseOrderById();

		int totalFiles = listOfFiles.size();

		for (int i = 0; i < totalFiles; i += BATCH_SIZE) {
			int start = i;
			int end = Math.min(i + BATCH_SIZE, totalFiles);

			executorService.submit(() -> {
				for (int j = start; j < end; j++) {
					FileNameDocument fileNameDocument = listOfFiles.get(j);
					String filePath = fileNameDocument.getFilePath();
					String application = fileNameDocument.getApplication();
					String fileName = fileNameDocument.getFileName();

					try {
						if (application.equalsIgnoreCase("REGISTRATIONDOWNLOADDOCUMENT")) {

							byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));

							String contentType;
							if (fileName.toLowerCase().endsWith(".pdf")) {
								contentType = "application/pdf";
							} else if (fileName.toLowerCase().endsWith(".jpg")
									|| fileName.toLowerCase().endsWith(".jpeg")) {
								contentType = "image/jpeg";
							} else if (fileName.toLowerCase().endsWith(".png")) {
								contentType = "image/png";
							} else {
								contentType = "application/octet-stream";
							}

							RegistrationDownloadDocument document = new RegistrationDownloadDocument();
							document.setGstinNumber(fileName.replaceAll("\\.(pdf|jpg|jpeg|png)$", ""));
							document.setData(fileBytes);
							document.setContentType(contentType);

							RegistrationDownloadDocument savedDoc = registrationDownloadDocumentRepository
									.save(document);

							if (savedDoc.getId() != null && savedDoc.getId() > 0) {
								fileNameDocument.setIsProcessed(true);
								fileNameDocumentRepository.save(fileNameDocument);
								log.info("Saved {} for GSTIN {} from {}", contentType, document.getGstinNumber(),
										filePath);
							}

						} else {
							log.error("Unknown application type: {}", application);
						}

					} catch (IOException e) {
						log.error("Failed to read file {}. Error: {}", filePath, e.getMessage());
					} catch (OutOfMemoryError e) {
						log.error("OutOfMemoryError while processing {}: {}", filePath, e.getMessage());
						Runtime.getRuntime().gc();
					} catch (Exception e) {
						log.error("Exception while processing {}: {}", filePath, e.getMessage());
					}
				}
			});
		}

		executorService.shutdown();
		while (!executorService.isTerminated()) {
			// Wait for all threads to finish
		}

		return "Binary documents saved to DB successfully.";
	}

//	public String processAllDocuments(String userName) {
//
//		long startTime = System.currentTimeMillis();
//
//		log.info("Started bulk document processing for user={}", userName);
//
//		MasterData masterData = masterDataService.getMasterdatabyName(userName);
//
//		if (masterData == null) {
//			return "Master data not found";
//		}
//
//		GSTUserSession gstUserSession = gstUserSessionServices.getUserSessionsByName(userName);
//
//		if (gstUserSession == null) {
//			return "Session not authenticated";
//		}
//
//		ExecutorService executor = Executors.newFixedThreadPool(50);
//
//		int pageNumber = 0;
//		int totalProcessed = 0;
//
//		try {
//
//			while (true) {
//
//				Pageable pageable = PageRequest.of(pageNumber, 5000);
//
//				Page<RegDocuments> page = regDocumentsRepository.findByIsSuccessIsNull(pageable);
//
//				if (page.isEmpty()) {
//					break;
//				}
//
//				List<RegDocuments> dbUpdateList = Collections.synchronizedList(new ArrayList<>());
//
//				List<CompletableFuture<Void>> futures = new ArrayList<>();
//
//				for (RegDocuments doc : page.getContent()) {
//
//					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//
//						try {
//							processSingleDocument(masterData, gstUserSession, doc, dbUpdateList);
//						} catch (Exception e) {
//							log.error("Failed documentId={}", doc.getDocumentId(), e);
//
//							updateErrorRecord(doc, e.getMessage(), dbUpdateList);
//						}
//
//					}, executor);
//
//					futures.add(future);
//				}
//
//				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//
//				if (!dbUpdateList.isEmpty()) {
//					regDocumentsRepository.saveAll(dbUpdateList);
//				}
//
//				totalProcessed += page.getContent().size();
//
//				log.info("Batch completed | page={} | processed={}", pageNumber, totalProcessed);
//
//				pageNumber++;
//			}
//
//		} catch (Exception e) {
//			log.error("Bulk processing failed", e);
//		} finally {
//			executor.shutdown();
//		}
//
//		long totalTime = System.currentTimeMillis() - startTime;
//
//		log.info("Completed bulk processing | totalProcessed={} | time={} ms", totalProcessed, totalTime);
//
//		return "Processed records: " + totalProcessed;
//	}
//

	private void updateSuccessRecord(RegDocuments doc, String path, String fileName, Queue<RegDocuments> queue) {

		doc.setFilePath(path);
		doc.setFileName(fileName);
		doc.setIsSuccess(true);
		doc.setErrorMsg(null);
		doc.setInsertDt(new java.sql.Date(System.currentTimeMillis()));

		queue.add(doc);
	}

	public void updateErrorRecord(RegDocuments doc, String errorMessage, Queue<RegDocuments> queue) {

		try {
			ObjectNode errorNode = MAPPER.createObjectNode();
			errorNode.put("error_message", errorMessage);

			doc.setIsSuccess(false);
			doc.setErrorMsg(errorNode);
			doc.setInsertDt(new java.sql.Date(System.currentTimeMillis()));

			queue.add(doc);

		} catch (Exception e) {
			log.error("Error updating record documentId={}", doc.getDocumentId(), e);
		}
	}

	private void shutdownExecutor(ExecutorService executor) {

		executor.shutdown();

		try {
			if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private String buildFolderPath(RegDocuments doc) {

		return Paths.get(documentFileLocation, doc.getYy(), doc.getMm(), doc.getDd(), doc.getRegSection(),
				doc.getSectionType()).toString();
	}

	private String buildFileName(RegDocuments doc) {

		return doc.getGstin() + "_" + doc.getRegSection() + "_" + doc.getSectionType() + "_" + doc.getYy() + doc.getMm()
				+ doc.getDd() + "_" + doc.getDocumentId();
	}

	private void createFolderIfNeeded(String folderPath) throws IOException {

		if (folderCache.putIfAbsent(folderPath, true) == null) {
			Files.createDirectories(Paths.get(folderPath));
		}
	}

//	private void updateSuccessRecord(RegDocuments doc, String folderPath, String fileName,
//			List<RegDocuments> dbUpdateList) {
//
//		doc.setFilePath(folderPath);
//		doc.setFileName(fileName);
//		doc.setIsSuccess(true);
//		doc.setErrorMsg(null);
//		doc.setInsertDt(new java.sql.Date(System.currentTimeMillis()));
//
//		dbUpdateList.add(doc);
//	}

	private void updateSuccessRecord(RegDocuments doc, String folderPath, String fileName,
			List<RegDocuments> dbUpdateList) {

		doc.setFilePath(folderPath);
		doc.setFileName(fileName);
		doc.setIsSuccess(true);
		doc.setErrorMsg(null);
		doc.setInsertDt(new java.sql.Date(System.currentTimeMillis()));

		dbUpdateList.add(doc);
		System.out.println("(dbUpdateList.size()::" + dbUpdateList.size());
		// 🔥 Auto batch save
		if (dbUpdateList.size() >= BATCH_SIZE) {
			regDocumentsRepository.saveAll(dbUpdateList);
			regDocumentsRepository.flush();
			dbUpdateList.clear();
		}
	}

	public void updateErrorRecord(RegDocuments doc, String errorMessage, List<RegDocuments> dbUpdateList) {

		try {

			ObjectNode errorNode = MAPPER.createObjectNode();
			errorNode.put("error_message", errorMessage);

			doc.setIsSuccess(false);
			doc.setErrorMsg(errorNode);
			doc.setInsertDt(new java.sql.Date(System.currentTimeMillis()));

			dbUpdateList.add(doc);

			// 🔥 Auto batch save
			if (dbUpdateList.size() >= BATCH_SIZE) {
				regDocumentsRepository.saveAll(dbUpdateList);
				regDocumentsRepository.flush();
				dbUpdateList.clear();
			}

		} catch (Exception e) {
			log.error("Failed updating error record for documentId={}", doc.getDocumentId(), e);
		}
	}

	public void processSingleDocumentLatest(MasterData masterData, GSTUserSession session, RegDocuments doc,
			BlockingQueue<RegDocuments> updateQueue) {

		try {

			APIDetails apiDetails = apiDetailsImpl.findByName(Constants.GET_REGISTRATION_DOWNLOAD_DOCUMENT);

			HttpHeaders headers = authenticationHelper.getDefaultHeaders(masterData, session.getAuthToken(),
					apiDetails.getApiContentType());

			Map<String, String> params = getParamsForRequestDownloadDataEntity(masterData, doc.getDocumentId(),
					apiDetails);

			String apiPath = authenticationHelper
					.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails), params);

			GSTCommonResponseBean response = restClient.get(apiPath, GSTCommonResponseBean.class, headers);

			if (response == null || response.getData() == null) {
				updateErrorRecord(doc, "Null response from API", updateQueue);
				return;
			}

			byte[] fileBytes = Base64.getDecoder().decode(response.getData());

			String fileNameWithoutExt = buildFileName(doc);
			String extension = getFileExtension(doc.getCt());

			if (extension == null) {
				updateErrorRecord(doc, "Unsupported file type", updateQueue);
				return;
			}

			String fileName = fileNameWithoutExt + extension;

			String remoteDir = BASE_PATH + "/" + doc.getYy() + "/" + doc.getMm() + "/" + doc.getDd() + "/"
					+ doc.getRegSection() + "/" + doc.getSectionType() + "/" + doc.getId();

			String remotePath = SftpUtil.uploadFileToSftp(fileBytes, remoteDir, fileName);

			updateSuccessRecord(doc, remotePath, fileNameWithoutExt, updateQueue);

		} catch (Exception e) {
			updateErrorRecord(doc, e.getMessage(), updateQueue);
		}
	}

//	public void updateErrorRecord(RegDocuments doc, String errorMessage, List<RegDocuments> dbUpdateList) {
//
//		try {
//
//			ObjectNode errorNode = MAPPER.createObjectNode();
//
//			errorNode.put("error_message", errorMessage);
//
//			doc.setIsSuccess(false);
//			doc.setErrorMsg(errorNode);
//			doc.setInsertDt(new java.sql.Date(System.currentTimeMillis()));
//
//			dbUpdateList.add(doc);
//
//		} catch (Exception e) {
//
//			log.error("Failed updating error record for documentId={}", doc.getDocumentId(), e);
//		}
//	}

	public void processSingleDocumentFew(MasterData masterData, GSTUserSession session, RentActivePpbzdtls doc,
			BlockingQueue<RentActivePpbzdtls> updateQueue) {

		try {

			APIDetails apiDetails = apiDetailsImpl.findByName(Constants.GET_REGISTRATION_DOWNLOAD_DOCUMENT);

			HttpHeaders headers = authenticationHelper.getDefaultHeaders(masterData, session.getAuthToken(),
					apiDetails.getApiContentType());

			Map<String, String> params = getParamsForRequestDownloadDataEntity(masterData, doc.getDocumentId(),
					apiDetails);

			String apiPath = authenticationHelper
					.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails), params);

			GSTCommonResponseBean response = restClient.get(apiPath, GSTCommonResponseBean.class, headers);

			if (response == null || response.getData() == null) {
				updateErrorRecordFew(doc, "Null response from API", updateQueue);
				return;
			}

			byte[] fileBytes = Base64.getDecoder().decode(response.getData());

			String fileNameWithoutExt = buildFileNameFew(doc);
			String extension = getFileExtension(doc.getCt());

			if (extension == null) {
				updateErrorRecordFew(doc, "Unsupported file type", updateQueue);
				return;
			}

			String fileName = fileNameWithoutExt + extension;

			String remoteDir = BASE_PATH + "/" + doc.getId();

			String remotePath = SftpUtil.uploadFileToSftp(fileBytes, remoteDir, fileName);

			updateSuccessRecordFew(doc, remotePath, fileNameWithoutExt, updateQueue);

		} catch (Exception e) {
			updateErrorRecordFew(doc, e.getMessage(), updateQueue);
		}
	}

	public void updateSuccessRecordFew(RentActivePpbzdtls doc, String path, String fileName,
			Queue<RentActivePpbzdtls> queue) {

		try {
			doc.setPath(path);
			doc.setFileName(fileName);
			doc.setIsProcessed(true);
			doc.setInsertDt(new java.sql.Date(System.currentTimeMillis()));

			queue.add(doc);

		} catch (Exception e) {
			log.error("Error updating record documentId={}", doc.getDocumentId(), e);
		}
	}

	public void updateErrorRecordFew(RentActivePpbzdtls doc, String errorMessage, Queue<RentActivePpbzdtls> queue) {

		try {

			doc.setIsProcessed(false);
			doc.setInsertDt(new java.sql.Date(System.currentTimeMillis()));

			queue.add(doc);

		} catch (Exception e) {
			log.error("Error updating record documentId={}", doc.getDocumentId(), e);
		}
	}

	private String buildFileNameFew(RentActivePpbzdtls doc) {

		return doc.getGstin() + "_" + doc.getRegSection() + "_" + doc.getSectionType() + "_" + doc.getDocumentId();
	}

//	// GET-COMPARISION-REPORT
//	public void processSingleSingleGstin(MasterData masterData, GSTUserSession session, ReturnComparisonReportGstin doc,
//			BlockingQueue<ReturnComparisonReportGstin> updateQueue) {
//
//		try {
//
//			log.info("Processing GSTIN={} FY={}", doc.getGstin(), doc.getFy());
//
//			APIDetails apiDetails = apiDetailsImpl.findByName(Constants.GET_COMPARISION_REPORT);
//
//			HttpHeaders headers = authenticationHelper.getDefaultHeaders(masterData, session.getAuthToken(),
//					apiDetails.getApiContentType());
//
//			Map<String, String> params = getParamsForRequestGetComparisonReport(doc.getGstin(), doc.getFy());
//
//			String apiPath = authenticationHelper
//					.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails), params);
//
//			log.info("Calling API URL={}", apiPath);
//
//			GSTCommonResponseBean response = restClient.get(apiPath, GSTCommonResponseBean.class, headers);
//
//			// =====================================================
//			// NULL CHECK
//			// =====================================================
//
//			if (response == null || response.getData() == null || response.getData().isBlank()) {
//
//				log.error("Empty response received GSTIN={} FY={}", doc.getGstin(), doc.getFy());
//
//				ReturnComparisonReportGstinJson jsonEntity = new ReturnComparisonReportGstinJson();
//
//				jsonEntity.setGstin(doc.getGstin());
//				jsonEntity.setFy(doc.getFy());
//				jsonEntity.setCounterAttempt(doc.getCounterAttempt());
//				jsonEntity.setIsProcessed(false);
//
//				returnComparisonReportGstinJsonRepository.save(jsonEntity);
//
//				updateErrorRecordGetComparisonReport(doc, updateQueue);
//				return;
//			}
//
//			// =====================================================
//			// BASE64 DECODE
//			// =====================================================
//
//			byte[] decodedBytes = Base64.getDecoder().decode(response.getData());
//
//			String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);
//
//			log.info("Decoded JSON received GSTIN={} FY={}", doc.getGstin(), doc.getFy());
//
//			// =====================================================
//			// CONVERT STRING -> JSON NODE
//			// =====================================================
//
//			JsonNode jsonNode = MAPPER.readTree(decodedJson);
//
//			// =====================================================
//			// SAVE JSON TABLE
//			// =====================================================
//
//			ReturnComparisonReportGstinJson jsonEntity = new ReturnComparisonReportGstinJson();
//
//			jsonEntity.setGstin(doc.getGstin());
//			jsonEntity.setFy(doc.getFy());
//			jsonEntity.setJsonData(jsonNode);
//			jsonEntity.setCounterAttempt(doc.getCounterAttempt());
//			jsonEntity.setIsProcessed(true);
//
//			returnComparisonReportGstinJsonRepository.save(jsonEntity);
//
//			log.info("JSON saved successfully GSTIN={} FY={}", doc.getGstin(), doc.getFy());
//
//			// =====================================================
//			// UPDATE MAIN TABLE
//			// =====================================================
//
//			updateSuccessRecordGetComparisonReport(doc, updateQueue);
//
//		} catch (Exception e) {
//
//			log.error("Error processing GSTIN={} FY={}", doc.getGstin(), doc.getFy(), e);
//
//			updateErrorRecordGetComparisonReport(doc, updateQueue);
//		}
//	}
//
//	// ================================================
//	// SUCCESS UPDATE METHOD
//	// ================================================
//
//	public void updateSuccessRecordGetComparisonReport(ReturnComparisonReportGstin doc,
//			Queue<ReturnComparisonReportGstin> queue) {
//
//		try {
//
//			doc.setIsProcessed(true);
//
//			queue.add(doc);
//
//			log.info("Marked SUCCESS GSTIN={} FY={}", doc.getGstin(), doc.getFy());
//
//		} catch (Exception e) {
//
//			log.error("Error updating success record GSTIN={}", doc.getGstin(), e);
//		}
//	}
//
//	// ================================================
//	// ERROR UPDATE METHOD
//	// ================================================
//
//	public void updateErrorRecordGetComparisonReport(ReturnComparisonReportGstin doc,
//			Queue<ReturnComparisonReportGstin> queue) {
//
//		try {
//
//			doc.setIsProcessed(false);
//
//			doc.setCounterAttempt(doc.getCounterAttempt() + 1);
//
//			queue.add(doc);
//
//			log.error("Marked FAILED GSTIN={} FY={} Attempt={}", doc.getGstin(), doc.getFy(), doc.getCounterAttempt());
//
//		} catch (Exception e) {
//
//			log.error("Error updating record GSTIN={}", doc.getGstin(), e);
//		}
//	}

	// =========================================================
	// MAIN PROCESS
	// =========================================================

	public void processSingleSingleGstin(MasterData masterData, GSTUserSession session,
			ReturnComparisonReportGstin doc) {

		try {

			log.info("------------------------------------------------");
			log.info("Processing GSTIN={} FY={}", doc.getGstin(), doc.getFy());

			// =========================================
			// MARK PROCESSING
			// =========================================

			doc.setIsProcessing(true);

			returnComparisonReportGstinRepository.save(doc);

			log.info("Marked processing GSTIN={} FY={}", doc.getGstin(), doc.getFy());

			// =========================================
			// API DETAILS
			// =========================================

			APIDetails apiDetails = apiDetailsImpl.findByName(Constants.GET_COMPARISION_REPORT);

			HttpHeaders headers = authenticationHelper.getDefaultHeaders(masterData, session.getAuthToken(),
					apiDetails.getApiContentType());

			Map<String, String> params = Map.of("gstin", doc.getGstin(), "action", "COMPREPORT", "year", doc.getFy());

			String apiPath = authenticationHelper
					.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails), params);

			log.info("Calling API={}", apiPath);

			// =========================================
			// API CALL
			// =========================================

			GSTCommonResponseBean response = restClient.get(apiPath, GSTCommonResponseBean.class, headers);

			// =========================================
			// EMPTY RESPONSE
			// =========================================

			if (response == null || response.getData() == null || response.getData().isBlank()) {

				log.error("Empty response GSTIN={} FY={}", doc.getGstin(), doc.getFy());

				updateErrorRecordGetComparisonReport(doc);

				return;
			}

			// =========================================
			// BASE64 DECODE
			// =========================================

			byte[] decodedBytes = Base64.getDecoder().decode(response.getData());

			String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);

			JsonNode jsonNode = MAPPER.readTree(decodedJson);

			// =========================================
			// CHECK DUPLICATE
			// =========================================

			boolean exists = returnComparisonReportGstinJsonRepository.existsByGstinAndFy(doc.getGstin(), doc.getFy());

			if (exists) {

				log.warn("Duplicate JSON already exists GSTIN={} FY={}", doc.getGstin(), doc.getFy());

			} else {

				ReturnComparisonReportGstinJson jsonEntity = new ReturnComparisonReportGstinJson();

				jsonEntity.setGstin(doc.getGstin());

				jsonEntity.setFy(doc.getFy());

				jsonEntity.setJsonData(jsonNode);

				jsonEntity.setCounterAttempt(doc.getCounterAttempt());

				jsonEntity.setIsProcessed(true);

				// =====================================
				// SAVE JSON IMMEDIATELY
				// =====================================

				returnComparisonReportGstinJsonRepository.save(jsonEntity);

				log.info("JSON saved GSTIN={} FY={}", doc.getGstin(), doc.getFy());
			}

			// =========================================
			// UPDATE SUCCESS
			// =========================================

			doc.setIsProcessed(true);

			doc.setIsProcessing(false);

			returnComparisonReportGstinRepository.save(doc);

			log.info("SUCCESS GSTIN={} FY={}", doc.getGstin(), doc.getFy());

		} catch (Exception e) {

			log.error("Processing failed GSTIN={} FY={}", doc.getGstin(), doc.getFy(), e);

			updateErrorRecordGetComparisonReport(doc);
		}
	}

	public void updateErrorRecordGetComparisonReport(ReturnComparisonReportGstin doc) {

		try {

			doc.setIsProcessed(false);

			doc.setIsProcessing(false);

			doc.setCounterAttempt(doc.getCounterAttempt() + 1);

			returnComparisonReportGstinRepository.save(doc);

			log.error("FAILED GSTIN={} FY={} ATTEMPT={}", doc.getGstin(), doc.getFy(), doc.getCounterAttempt());

		} catch (Exception e) {

			log.error("Error updating failed record GSTIN={}", doc.getGstin(), e);
		}
	}

	public String getComparisonReport(String userName) {

		long startTime = System.currentTimeMillis();

		log.info("====================================================");
		log.info("STARTED GET COMPARISON REPORT PROCESS");
		log.info("USERNAME={}", userName);
		log.info("====================================================");

		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger failedCount = new AtomicInteger();

		try {

			MasterData masterData = masterDataService.getMasterdatabyName(userName);

			if (masterData == null) {

				log.error("Master data not found USERNAME={}", userName);

				return "Master data not found";
			}

			GSTUserSession session = gstUserSessionServices.getUserSessionsByName(userName);

			if (session == null) {

				log.error("Session not found USERNAME={}", userName);

				return "Session not authenticated";
			}

			while (true) {

				Pageable pageable = PageRequest.of(0, 100);

				Page<ReturnComparisonReportGstin> page = returnComparisonReportGstinRepository.findPendingRecords(10,
						pageable);

				if (page.isEmpty()) {

					log.info("No pending records found");

					break;
				}

				log.info("Fetched pending records size={}", page.getContent().size());

				for (ReturnComparisonReportGstin doc : page.getContent()) {

					try {

						// ======================================
						// MARK PROCESSING
						// ======================================

						doc.setIsProcessing(true);

						returnComparisonReportGstinRepository.save(doc);

						log.info("PROCESSING STARTED GSTIN={} FY={}", doc.getGstin(), doc.getFy());

						// ======================================
						// PROCESS RECORD
						// ======================================

						processSingleSingleGstin(masterData, session, doc);

						successCount.incrementAndGet();

					} catch (Exception e) {

						failedCount.incrementAndGet();

						log.error("Processing failed GSTIN={} FY={}", doc.getGstin(), doc.getFy(), e);

						updateErrorRecordGetComparisonReport(doc);
					}
				}

				log.info("Current batch completed");
			}

		} catch (Exception e) {

			log.error("Bulk processing failed", e);
		}

		long totalTime = System.currentTimeMillis() - startTime;

		log.info("====================================================");
		log.info("PROCESS COMPLETED");
		log.info("SUCCESS={}", successCount.get());
		log.info("FAILED={}", failedCount.get());
		log.info("TOTAL TIME={} ms", totalTime);
		log.info("====================================================");

		return "Success=" + successCount.get() + " Failed=" + failedCount.get() + " Time(ms)=" + totalTime;
	}

	public String processDocumentsHim(String userName) {

		long startTime = System.currentTimeMillis();

		log.info("Started processDocumentsHim for user={}", userName);

		MasterData masterData = masterDataService.getMasterdatabyName(userName);

		if (masterData == null) {

			return "Master data not found";
		}

		GSTUserSession session = gstUserSessionServices.getUserSessionsByName(userName);

		if (session == null) {
			return "Session not authenticated";
		}

		ExecutorService executor = Executors.newFixedThreadPool(50);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failedCount = new AtomicInteger(0);

		try {

			while (true) {

				Pageable pageable = PageRequest.of(0, 500);

				Page<ReturnComparisonReportGstinJson> page = returnComparisonReportGstinJsonRepository
						.findByIsProcessedNullOrIsProcessedFalseAndCounterAttemptLessThan(4, pageable);

				if (page.isEmpty()) {

					log.info("No pending records found. Processing completed.");

					break;
				}

				List<ReturnComparisonReportGstinJson> records = page.getContent();

				log.info("Fetched {} records", records.size());

				List<CompletableFuture<Void>> futures = new ArrayList<>();

				for (ReturnComparisonReportGstinJson doc : records) {

					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

						try {

							processSingleDocumentHim(masterData, session, doc);

							successCount.incrementAndGet();

						} catch (Exception ex) {

							failedCount.incrementAndGet();

							log.error("Failed GSTIN={} FY={}", doc.getGstin(), doc.getFy(), ex);

							doc.setIsProcessed(false);

							doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

							doc.setErrorMessage(ex.getMessage());

							returnComparisonReportGstinJsonRepository.save(doc);
						}

					}, executor);

					futures.add(future);
				}

				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

				log.info("Batch Completed. Records Processed={}", records.size());
			}

		} catch (Exception ex) {

			log.error("Bulk processing failed", ex);

		} finally {

			executor.shutdown();

			try {

				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {

					executor.shutdownNow();
				}

			} catch (InterruptedException e) {

				executor.shutdownNow();

				Thread.currentThread().interrupt();
			}
		}

		long totalTime = System.currentTimeMillis() - startTime;

		log.info("Completed | Success={} | Failed={} | Time={} ms", successCount.get(), failedCount.get(), totalTime);

		return "Success=" + successCount.get() + ", Failed=" + failedCount.get() + ", Time(ms)=" + totalTime;
	}

	public void processSingleDocumentHim(MasterData masterData, GSTUserSession session,
			ReturnComparisonReportGstinJson doc) {

		try {

			APIDetails apiDetails = apiDetailsImpl.findByName(Constants.GET_COMPARISION_REPORT);

			HttpHeaders headers = authenticationHelper.getDefaultHeaders(masterData, session.getAuthToken(),
					apiDetails.getApiContentType());

			Map<String, String> params = Map.of("gstin", doc.getGstin(), "action", "COMPREPORT", "fy", doc.getFy());

			String apiPath = authenticationHelper
					.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails), params);

			log.info("Calling API GSTIN={} FY={}", doc.getGstin(), doc.getFy());

			GSTCommonResponseBean response = restClient.get(apiPath, GSTCommonResponseBean.class, headers);

			if (response == null || response.getData() == null || response.getData().isBlank()) {

				throw new RuntimeException("Null response from API");
			}

			byte[] decodedBytes = Base64.getDecoder().decode(response.getData());

			String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);

			JsonNode jsonNode = MAPPER.readTree(decodedJson);

			doc.setJsonData(jsonNode);

			doc.setIsProcessed(true);

			doc.setErrorMessage(null);

			if (doc.getCounterAttempt() == 0) {
				doc.setCounterAttempt(0);
			}

			returnComparisonReportGstinJsonRepository.save(doc);

			log.info("Successfully processed GSTIN={} FY={}", doc.getGstin(), doc.getFy());

		} catch (Exception ex) {

			log.error("Error GSTIN={} FY={}", doc.getGstin(), doc.getFy(), ex);

			doc.setIsProcessed(false);

			doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

			doc.setErrorMessage(ex.getMessage());

			returnComparisonReportGstinJsonRepository.save(doc);

			throw new RuntimeException(ex);
		}
	}

	public void updateErrorRecordHim(ReturnComparisonReportGstinJson doc, String errorMessage,
			Queue<ReturnComparisonReportGstinJson> queue) {

		try {

			doc.setIsProcessed(false);
			doc.setCounterAttempt(doc.getCounterAttempt() + 1);
			queue.add(doc);

		} catch (Exception e) {
			log.error("Error updating record Gstin={}", doc.getGstin(), e);
		}
	}

	public String getNormalTaxPayerPre(String userName) {

		long startTime = System.currentTimeMillis();

		log.info("Started getNormalTaxPayerPre for user={}", userName);

		MasterData masterData = masterDataService.getMasterdatabyName(userName);

		if (masterData == null) {
			return "Master data not found";
		}

		GSTUserSession session = gstUserSessionServices.getUserSessionsByName(userName);

		if (session == null) {
			return "Session not authenticated";
		}

		ExecutorService executor = Executors.newFixedThreadPool(50);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failedCount = new AtomicInteger(0);

		try {

			while (true) {

				Pageable pageable = PageRequest.of(0, 500);

				Page<GstinCollectionDetails> page = gstinCollectionDetailsRepository
						.findByIsMissingTrueAndCounterAttemptLessThan(4, pageable);

				if (page.isEmpty()) {

					log.info("No pending records found. Processing completed.");

					break;
				}

				List<GstinCollectionDetails> records = page.getContent();

				log.info("Fetched {} records", records.size());

				List<CompletableFuture<Void>> futures = new ArrayList<>();

				for (GstinCollectionDetails doc : records) {

					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

						try {

							processSingleGstinPre(masterData, session, doc);

							successCount.incrementAndGet();

						} catch (Exception ex) {

							failedCount.incrementAndGet();

							log.error("Failed GSTIN={}", doc.getGstin(), ex);

							doc.setIsMissing(true);

							doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

							doc.setMsg(MAPPER.createObjectNode().put("message", "Fail"));

							gstinCollectionDetailsRepository.save(doc);
						}

					}, executor);

					futures.add(future);
				}

				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

				log.info("Batch Completed. Records Processed={}", records.size());
			}

		} catch (Exception ex) {

			log.error("Bulk processing failed", ex);

		} finally {

			executor.shutdown();

			try {

				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {

					executor.shutdownNow();
				}

			} catch (InterruptedException e) {

				executor.shutdownNow();

				Thread.currentThread().interrupt();
			}
		}

		long totalTime = System.currentTimeMillis() - startTime;

		log.info("Completed | Success={} | Failed={} | Time={} ms", successCount.get(), failedCount.get(), totalTime);

		return "Success=" + successCount.get() + ", Failed=" + failedCount.get() + ", Time(ms)=" + totalTime;
	}

	public void processSingleGstinPre(MasterData masterData, GSTUserSession session, GstinCollectionDetails doc) {

		try {

			APIDetails apiDetails = apiDetailsImpl.findByName(Constants.GET_REGISTRATION_NORMAL_TAX_PAYER);

			HttpHeaders headers = authenticationHelper.getDefaultHeaders(masterData, session.getAuthToken(),
					apiDetails.getApiContentType());

			Map<String, String> params = Map.of("id", doc.getGstin(), "action", apiDetails.getApiAction(), "state_cd",
					masterData.getStateCd(), "idty", "GSTIN");

			String apiPath = authenticationHelper
					.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails), params);

			log.info("Calling API GSTIN={} Type={}", doc.getGstin(), "Normal");

			GSTCommonResponseBean response = restClient.get(apiPath, GSTCommonResponseBean.class, headers);

			if (response == null || response.getData() == null || response.getData().isBlank()) {

				doc.setIsMissing(true);

				doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

				doc.setMsg(MAPPER.createObjectNode().put("message", "Fail"));

				gstinCollectionDetailsRepository.save(doc);

			}

			byte[] decodedBytes = Base64.getDecoder().decode(response.getData());

			String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);

			JsonNode jsonNode = MAPPER.readTree(decodedJson);

			doc.setJsondata(jsonNode);

			doc.setIsMissing(false);
			doc.setIssuccess(true);
			doc.setInsertTm(LocalDateTime.now());

			doc.setMsg(MAPPER.createObjectNode().put("message", "Success"));

			if (doc.getCounterAttempt() == 0) {
				doc.setCounterAttempt(0);
			}

			gstinCollectionDetailsRepository.save(doc);

			log.info("Successfully processed GSTIN={} ", doc.getGstin());

		} catch (Exception ex) {

			log.error("Error GSTIN={} ", doc.getGstin(), ex);

			doc.setIsMissing(true);

			doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

			doc.setMsg(MAPPER.createObjectNode().put("message", "Fail"));

			gstinCollectionDetailsRepository.save(doc);

			log.error("GSTIN={} Error={}", doc.getGstin(), ex.getMessage(), ex);
		}
	}

	// ---------------------TDS-TCS--------------------------------------//
	public String getTdsTcsTaxPayerPre(String userName) {

		long startTime = System.currentTimeMillis();

		log.info("Started getTdsTcsTaxPayerPre for user={}", userName);

		MasterData masterData = masterDataService.getMasterdatabyName(userName);

		if (masterData == null) {
			return "Master data not found";
		}

		GSTUserSession session = gstUserSessionServices.getUserSessionsByName(userName);

		if (session == null) {
			return "Session not authenticated";
		}

		ExecutorService executor = Executors.newFixedThreadPool(50);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failedCount = new AtomicInteger(0);

		try {

			while (true) {

				Pageable pageable = PageRequest.of(0, 500);

				Page<TdsTcsList> page = tdsTcsListRepository.findByIsMissingTrueAndCounterAttemptLessThan(4, pageable);

				if (page.isEmpty()) {

					log.info("No pending records found. Processing completed.");

					break;
				}

				List<TdsTcsList> records = page.getContent();

				log.info("Fetched {} records", records.size());

				List<CompletableFuture<Void>> futures = new ArrayList<>();

				for (TdsTcsList doc : records) {

					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

						try {

							processSingleTdsTcsGstinPre(masterData, session, doc);

							successCount.incrementAndGet();

						} catch (Exception ex) {

							failedCount.incrementAndGet();

							log.error("Failed GSTIN={}", doc.getGstin(), ex);

							doc.setIsMissing(true);

							doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

							doc.setErrorMessage("Fail");

							tdsTcsListRepository.save(doc);
						}

					}, executor);

					futures.add(future);
				}

				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

				log.info("Batch Completed. Records Processed={}", records.size());
			}

		} catch (Exception ex) {

			log.error("Bulk processing failed", ex);

		} finally {

			executor.shutdown();

			try {

				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {

					executor.shutdownNow();
				}

			} catch (InterruptedException e) {

				executor.shutdownNow();

				Thread.currentThread().interrupt();
			}
		}

		long totalTime = System.currentTimeMillis() - startTime;

		log.info("Completed | Success={} | Failed={} | Time={} ms", successCount.get(), failedCount.get(), totalTime);

		return "Success=" + successCount.get() + ", Failed=" + failedCount.get() + ", Time(ms)=" + totalTime;
	}

	public void processSingleTdsTcsGstinPre(MasterData masterData, GSTUserSession session, TdsTcsList doc) {

		try {

			// Set insert/update timestamp
	        doc.setInsertTm(LocalDateTime.now());
	        
			APIDetails apiDetails = apiDetailsImpl.findByName(Constants.GET_REGISTRATION_NORMAL_TAX_PAYER);

			HttpHeaders headers = authenticationHelper.getDefaultHeaders(masterData, session.getAuthToken(),
					apiDetails.getApiContentType());

			String idty = "APLTD".equalsIgnoreCase(doc.getTy()) ? "UITD" : "UITC";

			Map<String, String> params = Map.of("id", doc.getGstin(), "action", apiDetails.getApiAction(), "state_cd",
					masterData.getStateCd(), "idty", idty);

			String apiPath = authenticationHelper
					.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails), params);

			log.info("Calling API GSTIN={} Type={}", doc.getGstin(), idty);

			GSTCommonResponseBean response = restClient.get(apiPath, GSTCommonResponseBean.class, headers);
			
			if (response == null || response.getData() == null || response.getData().isBlank()) {

				doc.setIsMissing(true);

				doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

				doc.setErrorMessage("Fail");

				tdsTcsListRepository.save(doc);

			}

			byte[] decodedBytes = Base64.getDecoder().decode(response.getData());

			String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);

			JsonNode jsonNode = MAPPER.readTree(decodedJson);

			doc.setJsondata(jsonNode);

			doc.setIsMissing(false);
			doc.setIsSuccess("T");

			if (doc.getCounterAttempt() == 0) {
				doc.setCounterAttempt(0);
			}

			tdsTcsListRepository.save(doc);

			log.info("Successfully processed GSTIN={} ", doc.getGstin());

		} catch (Exception ex) {

			log.error("Error GSTIN={} ", doc.getGstin(), ex);

			doc.setIsMissing(true);

			doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

			doc.setErrorMessage("Fail");

			tdsTcsListRepository.save(doc);

			log.error("GSTIN={} Error={}", doc.getGstin(), ex.getMessage(), ex);
		}
	}

	// ------------GET-ENTITY-ENFORECEMTN-OFFICER

	public String GetEntityEnforcementOfficer(String userName) {

		long startTime = System.currentTimeMillis();

		log.info("Started GetEntityEnforcementOfficer for user={}", userName);

		MasterData masterData = masterDataService.getMasterdatabyName(userName);

		if (masterData == null) {
			return "Master data not found";
		}

		GSTUserSession session = gstUserSessionServices.getUserSessionsByName(userName);

		if (session == null) {
			return "Session not authenticated";
		}

		ExecutorService executor = Executors.newFixedThreadPool(50);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failedCount = new AtomicInteger(0);

		try {

			while (true) {

				Pageable pageable = PageRequest.of(0, 500);

				Page<GstinCollectionDetailsOnTheFly> page = gstinCollectionDetailsOnTheFlyRepository
						.findByIsMissingTrueAndCounterAttemptLessThan(4, pageable);

				if (page.isEmpty()) {

					log.info("No pending records found. Processing completed.");

					break;
				}

				List<GstinCollectionDetailsOnTheFly> records = page.getContent();

				log.info("Fetched {} records", records.size());

				List<CompletableFuture<Void>> futures = new ArrayList<>();

				for (GstinCollectionDetailsOnTheFly doc : records) {

					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

						try {

							processSingleEntityEnforcementOfficerGstinPre(masterData, session, doc);

							successCount.incrementAndGet();

						} catch (Exception ex) {

							failedCount.incrementAndGet();

							log.error("Failed GSTIN={}", doc.getGstin(), ex);

							doc.setIsMissing(true);

							doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

							doc.setMsg(MAPPER.createObjectNode().put("message", "Fail"));

							gstinCollectionDetailsOnTheFlyRepository.save(doc);
						}

					}, executor);

					futures.add(future);
				}

				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

				log.info("Batch Completed. Records Processed={}", records.size());
			}

		} catch (Exception ex) {

			log.error("Bulk processing failed", ex);

		} finally {

			executor.shutdown();

			try {

				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {

					executor.shutdownNow();
				}

			} catch (InterruptedException e) {

				executor.shutdownNow();

				Thread.currentThread().interrupt();
			}
		}

		long totalTime = System.currentTimeMillis() - startTime;

		log.info("Completed | Success={} | Failed={} | Time={} ms", successCount.get(), failedCount.get(), totalTime);

		return "Success=" + successCount.get() + ", Failed=" + failedCount.get() + ", Time(ms)=" + totalTime;
	}

	public void processSingleEntityEnforcementOfficerGstinPre(MasterData masterData, GSTUserSession session,
			GstinCollectionDetailsOnTheFly doc) {

		try {

			APIDetails apiDetails = apiDetailsImpl.findByName(Constants.ENFORCEMENTOFFICERGETENTITY);

			HttpHeaders headers = authenticationHelper.getDefaultHeadersEnforcement(masterData, session.getAuthToken(),
					apiDetails.getApiContentType());

//				Map<String, String> params = Map.of("id", doc.getGstin(), "action", apiDetails.getApiAction(), "state_cd",
//						masterData.getStateCd(), "idty", idty);

			Map<String, String> params = Map.of("id", doc.getGstin(), "action", "ENFREGENT", "state_cd",
					masterData.getStateCd(), "idty", doc.getIdty());

			String apiPath = authenticationHelper
					.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails), params);

			log.info("Calling API GSTIN={} Type={}", doc.getGstin(), doc.getIdty());

			GSTCommonResponseBean response = restClient.get(apiPath, GSTCommonResponseBean.class, headers);

			if (response == null || response.getData() == null || response.getData().isBlank()) {

				doc.setIsMissing(true);

				doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

				doc.setMsg(MAPPER.createObjectNode().put("message", "Fail"));

				gstinCollectionDetailsOnTheFlyRepository.save(doc);

			}

			byte[] decodedBytes = Base64.getDecoder().decode(response.getData());

			String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);

			JsonNode jsonNode = MAPPER.readTree(decodedJson);

			doc.setJsondata(jsonNode);

			doc.setIsMissing(false);
			doc.setIssuccess(true);
			doc.setInsertTm(LocalDateTime.now());

			doc.setMsg(MAPPER.createObjectNode().put("message", "Success"));

			if (doc.getCounterAttempt() == 0) {
				doc.setCounterAttempt(0);
			}

			gstinCollectionDetailsOnTheFlyRepository.save(doc);

			log.info("Successfully processed GSTIN={} ", doc.getGstin());

		} catch (Exception ex) {

			log.error("Error GSTIN={} ", doc.getGstin(), ex);

			doc.setIsMissing(true);

			doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

			doc.setMsg(MAPPER.createObjectNode().put("message", "Fail"));

			gstinCollectionDetailsOnTheFlyRepository.save(doc);

			log.error("GSTIN={} Error={}", doc.getGstin(), ex.getMessage(), ex);
		}
	}

	// ------------GET-LEDGER-ITC-ONLY

	public String GetLedgerItcOnly(String userName) {

		long startTime = System.currentTimeMillis();

		log.info("Started GetLedgerItcOnly for user={}", userName);

		MasterData masterData = masterDataService.getMasterdatabyName(userName);

		if (masterData == null) {
			return "Master data not found";
		}

		GSTUserSession session = gstUserSessionServices.getUserSessionsByName(userName);

		if (session == null) {
			return "Session not authenticated";
		}

		ExecutorService executor = Executors.newFixedThreadPool(50);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failedCount = new AtomicInteger(0);

		try {

			while (true) {

				Pageable pageable = PageRequest.of(0, 500);

				Page<LedgerInitialJson> page = ledgerInitialJsonRepository
						.findByIsSuccessIsNullAndCounterAttemptLessThan(4, pageable);

				if (page.isEmpty()) {

					log.info("No pending records found. Processing completed.");

					break;
				}

				List<LedgerInitialJson> records = page.getContent();

				log.info("Fetched {} records", records.size());

				List<CompletableFuture<Void>> futures = new ArrayList<>();

				for (LedgerInitialJson doc : records) {

					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {

						try {

							processLedgerItcOnly(masterData, session, doc);

							successCount.incrementAndGet();

						} catch (Exception ex) {

							failedCount.incrementAndGet();

							log.error("Failed GSTIN={}", doc.getGstin(), ex);

							doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

							doc.setMsg("Fail");

							ledgerInitialJsonRepository.save(doc);
						}

					}, executor);

					futures.add(future);
				}

				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

				log.info("Batch Completed. Records Processed={}", records.size());
			}

		} catch (Exception ex) {

			log.error("Bulk processing failed", ex);

		} finally {

			executor.shutdown();

			try {

				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {

					executor.shutdownNow();
				}

			} catch (InterruptedException e) {

				executor.shutdownNow();

				Thread.currentThread().interrupt();
			}
		}

		long totalTime = System.currentTimeMillis() - startTime;

		log.info("Completed | Success={} | Failed={} | Time={} ms", successCount.get(), failedCount.get(), totalTime);

		return "Success=" + successCount.get() + ", Failed=" + failedCount.get() + ", Time(ms)=" + totalTime;
	}

	public void processLedgerItcOnly(MasterData masterData, GSTUserSession session, LedgerInitialJson doc) {

		try {

			APIDetails apiDetails = apiDetailsImpl.findByName(Constants.GET_RETURN_LEDGER);

			HttpHeaders headers = authenticationHelper.getDefaultHeadersEnforcement(masterData, session.getAuthToken(),
					apiDetails.getApiContentType());
			
		
		
			//http://localhost:8019/common/gstr/scheduleLedger?action=ITC&fr_dt=01-04-2023&to_dt=31-03-2024

			LocalDate fromDate = doc.getFrDt();
			LocalDate toDate = doc.getToDt();

			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

			Map<String, String> params = Map.of(
			        "gstin", doc.getGstin(),
			        "action", "ITC",
			        "state_cd", masterData.getStateCd(),
			        "fr_dt", fromDate.format(formatter),
			        "to_dt", toDate.format(formatter)
			);

			String apiPath = authenticationHelper
					.getUriWithParam(authenticationHelper.getFullPath(masterData, apiDetails), params);

			log.info("Calling API GSTIN={} action={}", doc.getGstin(), "ITC");

			GSTCommonResponseBean response = restClient.get(apiPath, GSTCommonResponseBean.class, headers);

			if (response == null || response.getData() == null || response.getData().isBlank()) {

				doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

				doc.setMsg("Fail");

				ledgerInitialJsonRepository.save(doc);

			}

			byte[] decodedBytes = Base64.getDecoder().decode(response.getData());

			String decodedJson = new String(decodedBytes, StandardCharsets.UTF_8);

			JsonNode jsonNode = MAPPER.readTree(decodedJson);

			doc.setJsondata(jsonNode);

			doc.setMsg("SUCCESS");
			doc.setIsSuccess(true);

			if (doc.getCounterAttempt() == 0) {
				doc.setCounterAttempt(0);
			}

			ledgerInitialJsonRepository.save(doc);

			log.info("Successfully processed GSTIN={} ", doc.getGstin());

		} catch (Exception ex) {

			log.error("Error GSTIN={} ", doc.getGstin(), ex);

			doc.setMsg("Fail");

			doc.setCounterAttempt(doc.getCounterAttempt() == 0 ? 1 : doc.getCounterAttempt() + 1);

			ledgerInitialJsonRepository.save(doc);

			log.error("GSTIN={} Error={}", doc.getGstin(), ex.getMessage(), ex);
		}
	}
}
