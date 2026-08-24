package com.deloitte.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.deloitte.common.entity.EwayBillPdf;
import com.deloitte.returns.entity.EwayBill.EwayBill_Ewb;
import com.deloitte.returns.repository.eway.EwayBillEwbRepository;
import com.deloitte.returns.repositoryCommon.EwayBillPdfRepository;
import com.itextpdf.text.Document;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class EwayBillReportServiceImpl {

	@Autowired
	private EwayBillEwbRepository ewayBillEwbRepository;

	@Autowired
	private EwayBillPdfRepository ewayBillPdfRepository;

	public byte[] generatePdf(String docDate, String status) throws Exception {

		List<EwayBill_Ewb> list = getData(docDate, status);

		double totalIgst = list.stream().mapToDouble(EwayBill_Ewb::getIgstVal).sum();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		Document document = new Document(PageSize.A4.rotate());

		PdfWriter.getInstance(document, baos);

		document.open();

		Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);

		document.add(new Paragraph("E-Way Bill Report", titleFont));

		document.add(new Paragraph("Document Date : " + docDate));

		document.add(new Paragraph("Status : " + status));

		document.add(new Paragraph("Total E-Way Bill : " + list.size()));

		document.add(new Paragraph("Total IGST : " + totalIgst));

		document.add(new Paragraph(" "));

		PdfPTable table = new PdfPTable(8);

		table.addCell("EWB No");
		table.addCell("Date");
		table.addCell("Status");
		table.addCell("From GSTIN");
		table.addCell("To GSTIN");
		table.addCell("Doc No");
		table.addCell("Value");
		table.addCell("IGST");

		for (EwayBill_Ewb e : list) {

			table.addCell(String.valueOf(e.getEwbNo()));

			table.addCell(e.getEwbDt());

			table.addCell(e.getStatus());

			table.addCell(e.getFrGstin());

			table.addCell(e.getToGstin());

			table.addCell(e.getDocNo());

			table.addCell(String.valueOf(e.getAssVal()));

			table.addCell(String.valueOf(e.getIgstVal()));

		}

		document.add(table);

		document.close();

		return baos.toByteArray();

	}

	public byte[] generateExcel(String docDate, String status) throws Exception {

		List<EwayBill_Ewb> list = getData(docDate, status);

		double totalIgst = list.stream().mapToDouble(EwayBill_Ewb::getIgstVal).sum();

		Workbook workbook = new XSSFWorkbook();

		Sheet sheet = workbook.createSheet("EwayBill");

		int rowNum = 0;

		Row summary = sheet.createRow(rowNum++);

		summary.createCell(0).setCellValue("Total E-Way Bill");

		summary.createCell(1).setCellValue(list.size());

		Row igstRow = sheet.createRow(rowNum++);

		igstRow.createCell(0).setCellValue("Total IGST");

		igstRow.createCell(1).setCellValue(totalIgst);

		rowNum++;

		Row header = sheet.createRow(rowNum++);

		String[] columns = {

				"EWB No", "Date", "Status", "From GSTIN", "To GSTIN", "Doc No", "Value", "IGST"

		};

		for (int i = 0; i < columns.length; i++) {

			header.createCell(i).setCellValue(columns[i]);

		}

		for (EwayBill_Ewb e : list) {

			Row row = sheet.createRow(rowNum++);

			row.createCell(0).setCellValue(e.getEwbNo());

			row.createCell(1).setCellValue(e.getEwbDt());

			row.createCell(2).setCellValue(e.getStatus());

			row.createCell(3).setCellValue(e.getFrGstin());

			row.createCell(4).setCellValue(e.getToGstin());

			row.createCell(5).setCellValue(e.getDocNo());

			row.createCell(6).setCellValue(e.getAssVal());

			row.createCell(7).setCellValue(e.getIgstVal());

		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		workbook.write(baos);

		workbook.close();

		return baos.toByteArray();

	}

	private List<EwayBill_Ewb> getData(String docDate, String status) {

		LocalDate date = LocalDate.parse(docDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		String formattedDate = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

		if ("ALL".equalsIgnoreCase(status)) {

			return ewayBillEwbRepository.findByDocDt(formattedDate);

		}

		return ewayBillEwbRepository.findByDocDtAndStatus(formattedDate, status);

	}

	public void readAndSave(String pdfPath) throws IOException {

		long startTime = System.currentTimeMillis();

		log.info("PDF Processing Started. File={}", pdfPath);

		File file = new File(pdfPath);

		if (!file.exists()) {
			log.error("PDF file not found. File={}", pdfPath);
			throw new RuntimeException("PDF File Not Found : " + pdfPath);
		}

		try (PDDocument document = Loader.loadPDF(file)) {

			log.info("PDF Loaded Successfully. Pages={}", document.getNumberOfPages());

			PDFTextStripper stripper = new PDFTextStripper();
			String text = stripper.getText(document);

			log.debug("PDF Text Length={}", text.length());

			EwayBillPdf pdf = new EwayBillPdf();

			pdf.setEwayBillNo(find(text, "eWay Bill No:\\s*([0-9 ]+)"));
			pdf.setGeneratedDate(find(text, "Generated Date:\\s*([0-9/: AMPamp]+)"));
			// pdf.setGeneratedBy(find(text, "Generated By:\\s*([A-Z0-9 ]+)"));
			// pdf.setValidUpto(find(text, "Valid Upto:\\s*([0-9/]+)"));

			pdf.setInvoiceNo(find(text, "Document Details:.*?-\\s*([^\\-]+)-"));
			pdf.setInvoiceDate(find(text, "Document Details:.*?([0-9]{2}/[0-9]{2}/[0-9]{4})"));

			pdf.setVehicleNo(find(text, "Road\\s+([A-Z0-9]+)"));
			// pdf.setTransporterState(find(text, "Road\\s+[A-Z0-9]+\\s+([A-Za-z]+)"));

			// pdf.setIrn(find(text, "IRN:\\s*([A-Za-z0-9]+)"));

			// From GSTIN
			String fromGstin = find(text, "From\\s+GSTIN\\s*:\\s*([A-Z0-9 ]+)");
			pdf.setFromGstin(fromGstin == null ? null : fromGstin.replaceAll("\\s+", ""));

			// From Name
			pdf.setFromName(find(text, "From\\s+GSTIN\\s*:\\s*[A-Z0-9 ]+\\s*(.*?)\\s*::Dispatch From::"));

			// From State
			pdf.setFromState(find(text, "From\\s+GSTIN\\s*:\\s*[A-Z0-9 ]+\\s*.*?\\s+([A-Z ]+)\\s*::Dispatch From::"));

			// To GSTIN
			String toGstin = find(text, "To\\s+GSTIN\\s*:\\s*([A-Z0-9 ]+)");
			pdf.setToGstin(toGstin == null ? null : toGstin.replaceAll("\\s+", ""));

			// To Name
			pdf.setToName(find(text, "To\\s+GSTIN\\s*:\\s*[A-Z0-9 ]+\\s*(.*?)\\s*::Ship To::"));

			// To State
			pdf.setToState(find(text, "To\\s+GSTIN\\s*:\\s*[A-Z0-9 ]+\\s*.*?\\s+([A-Z ]+)\\s*::Ship To::"));

			String taxable = find(text, "Total Taxable Amt.?\\s*([0-9.]+)");
			if (taxable != null && !taxable.isBlank()) {
				pdf.setTaxableAmount(Double.parseDouble(taxable));
			}

			String igst = find(text, "IGST Amt.?\\s*([0-9.]+)");
			if (igst != null && !igst.isBlank()) {
				pdf.setIgstAmount(Double.parseDouble(igst));
			}

			ewayBillPdfRepository.save(pdf);

			log.info("PDF processed successfully. EWB={}, Invoice={}, Vehicle={}, FromGSTIN={}, ToGSTIN={}",
					pdf.getEwayBillNo(), pdf.getInvoiceNo(), pdf.getVehicleNo(), pdf.getFromGstin(), pdf.getToGstin());

		} catch (Exception ex) {

			log.error("Error while processing PDF. File={}", pdfPath, ex);
			throw ex;

		} finally {

			log.info("PDF Processing Completed. File={} TimeTaken={} ms", pdfPath,
					(System.currentTimeMillis() - startTime));
		}
	}

	private String find(String text, String regex) {

		if (text == null || text.isBlank()) {
			log.error("PDF text is null or empty. Regex={}", regex);
			return null;
		}

		if (regex == null || regex.isBlank()) {
			log.error("Regex pattern is null or empty.");
			return null;
		}

		try {

			Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

			Matcher matcher = pattern.matcher(text);

			if (matcher.find()) {

				String value = matcher.group(1);

				if (value != null) {
					value = value.replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ").trim();
				}

				log.debug("Regex matched successfully. Regex={} Value={}", regex, value);

				return value;
			}

			log.warn("No matching value found. Regex={}", regex);

		} catch (Exception ex) {

			log.error("Error while applying regex. Regex={}", regex, ex);
		}

		return null;
	}

}