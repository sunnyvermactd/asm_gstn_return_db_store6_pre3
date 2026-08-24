package com.deloitte.service.impl;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.deloitte.common.bean.LastUpdateChangeNameDTO;
import com.deloitte.common.bean.LastUpdateDTO;
import com.deloitte.common.bean.ReportRequestDTO;
import com.deloitte.common.bean.ReportResponseDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl {

	private final JdbcTemplate jdbcTemplate;

	public List<ReportResponseDTO> generateReport(ReportRequestDTO request) {

		if ("PARTA".equalsIgnoreCase(request.getTy())) {

			return generateEwbReport(request);
		}

		if ("PARTB".equalsIgnoreCase(request.getTy())) {

			return generateEwbReportPartB(request);
		}

		if ("payment".equalsIgnoreCase(request.getTy())) {

			return generatePaymentReport(request);
		}

		return generateReturnReport(request);
	}

	public List<ReportResponseDTO> generateReturnReport(ReportRequestDTO request) {

		long startTime = System.currentTimeMillis();

		String jsonTable = getJsonTable(request.getTy());

		log.info("====================================================");
		log.info("REPORT GENERATION STARTED");
		log.info("TY         : {}", request.getTy());
		log.info("FROM DATE  : {}", request.getFromDate());
		log.info("TO DATE    : {}", request.getToDate());
		log.info("JSON TABLE : {}", jsonTable);
		log.info("====================================================");

		String sql = """
				SELECT
				    t1.dt,
				    t1.num_files,
				    t1.file_num,
				    t1.num_filescnt,
				    t3.dt AS dt2,
				    t3.filenumber,
				    t3.jsoncount
				FROM
				(
				    SELECT
				        A.dt,
				        A.num_files,
				        B.file_num,
				        B.cnt AS num_filescnt
				    FROM filecounter."ReturnFileCount" A
				    LEFT JOIN filecounter."ReturnFileDetail" B
				        ON A."ReturnFileCountId" = B."ReturnFileCountId"
				    WHERE A.ty = ?
				      AND A.dt BETWEEN ? AND ?
				      AND A."IsSuccess" = '1'
				) t1
				LEFT JOIN
				(
				    SELECT
				        dt,
				        filenumber,
				        COUNT(1) AS jsoncount
				    FROM %s
				    WHERE dt BETWEEN ? AND ?
				    GROUP BY dt, filenumber
				) t3
				ON t1.dt = t3.dt
				AND t1.file_num = t3.filenumber
				ORDER BY t1.dt DESC, t1.file_num DESC
				""".formatted(jsonTable);

		String logQuery = sql.replaceFirst("\\?", "'" + request.getTy() + "'")
				.replaceFirst("\\?", "'" + request.getFromDate() + "'")
				.replaceFirst("\\?", "'" + request.getToDate() + "'")
				.replaceFirst("\\?", "'" + request.getFromDate() + "'")
				.replaceFirst("\\?", "'" + request.getToDate() + "'");

		log.info("FINAL QUERY:\n{}", logQuery);

		try {

			List<ReportResponseDTO> result = jdbcTemplate.query(sql, new Object[] { request.getTy(),
					request.getFromDate(), request.getToDate(), request.getFromDate(), request.getToDate() },
					(rs, rowNum) -> {

						ReportResponseDTO dto = new ReportResponseDTO();

						dto.setDt(rs.getDate("dt") != null ? rs.getDate("dt").toLocalDate() : null);

						dto.setNumFiles(rs.getObject("num_files", Integer.class));

						dto.setFileNum(rs.getObject("file_num", Integer.class));

						dto.setNumFilesCnt(rs.getObject("num_filescnt", Integer.class));

						dto.setDt2(rs.getDate("dt2") != null ? rs.getDate("dt2").toLocalDate() : null);

						dto.setFileNumber(rs.getObject("filenumber", Integer.class));

						dto.setJsonCount(rs.getObject("jsoncount", Long.class));

						return dto;
					});

			log.info("====================================================");
			log.info("REPORT GENERATED SUCCESSFULLY");
			log.info("TOTAL RECORDS : {}", result.size());
			log.info("TIME TAKEN    : {} ms", (System.currentTimeMillis() - startTime));
			log.info("====================================================");

			return result;

		} catch (Exception ex) {

			log.error("Error while generating report", ex);

			throw new RuntimeException("Unable to generate report. Please check logs.", ex);
		}
	}

	private String getJsonTable(String ty) {

		if (ty == null) {
			throw new IllegalArgumentException("TY cannot be null");
		}

		return switch (ty.toUpperCase()) {

		case "R1" -> "log.r1_initial_json";

		case "R1A" -> "log.r1a_initial_json";

		case "R2B" -> "log.r2b_initial_json";

		case "R3B" -> "log.r3b_initial_json";

		case "R4" -> "log.r4_initial_json";

		case "R5" -> "log.r5_initial_json";

		case "R6" -> "log.r6_initial_json";

		case "R7" -> "log.r7_initial_json";

		case "R8" -> "log.r8_initial_json";

		case "R9" -> "log.r9_initial_json";

		case "R9A" -> "log.r9a_initial_json";

		case "R9C" -> "log.r9c_initial_json";

		case "R98A" -> "log.r98a_initial_json";

		case "R10" -> "log.r10_initial_json";

		case "R11" -> "log.r11_initial_json";

		case "PAYMENT" -> "log.payment_initial_json";

		case "CM8" -> "log.cmp08_initial_json";

		case "PARTA" -> "log.ewb_parta_initial_json";

		case "PARTB" -> "log.ewb_partb_initial_json";

		default -> throw new IllegalArgumentException("Unsupported TY : " + ty);
		};
	}

	private List<ReportResponseDTO> generateEwbReportPartB(ReportRequestDTO request) {

		long startTime = System.currentTimeMillis();

		String jsonTable = "PARTA".equalsIgnoreCase(request.getTy()) ? "log.ewb_parta_initial_json"
				: "log.ewb_partb_initial_json";

		String sql = """
				SELECT
				    t1.dt,
				    t1.gen_file_cnt,
				    t1.file_num,
				    t1.num_filescnt,
				    t3.dt AS dt2,
				    t3.file_number,
				    t3.cntt
				FROM
				(
				    SELECT
				        to_date(A.dt,'DD-MM-YYYY') AS dt,
				        A.gen_file_cnt,
				        B.file_num,
				        B.total_records AS num_filescnt
				    FROM filecounter.ewb_count_data A
				    LEFT JOIN filecounter.ewb_file_detail B
				        ON A.id = B.return_file_count_id
				    WHERE
				        to_date(A.dt,'DD-MM-YYYY') BETWEEN ? AND ?
				        AND A.is_success = TRUE
				        AND A.ty = ?
				) t1
				LEFT JOIN
				(
				    SELECT
				        to_date(dt,'DD-MM-YYYY') AS dt,
				        file_number,
				        count(ewb->'Ewb_no') AS cntt
				    FROM %s
				    LEFT JOIN LATERAL
				        jsonb_array_elements(json_data->'ewb') AS ewb(value)
				        ON TRUE
				    WHERE
				        to_date(dt,'DD-MM-YYYY') BETWEEN ? AND ?
				    GROUP BY
				        to_date(dt,'DD-MM-YYYY'),
				        file_number
				) t3
				ON t1.dt = t3.dt
				AND t1.file_num = t3.file_number
				ORDER BY
				    t1.dt DESC,
				    t1.file_num DESC
				""".formatted(jsonTable);

		String logQuery = sql.replaceFirst("\\?", "'" + request.getFromDate() + "'")
				.replaceFirst("\\?", "'" + request.getToDate() + "'").replaceFirst("\\?", "'" + request.getTy() + "'")
				.replaceFirst("\\?", "'" + request.getFromDate() + "'")
				.replaceFirst("\\?", "'" + request.getToDate() + "'");

		log.info("PARTA/PARTB QUERY:\n{}", logQuery);

		List<ReportResponseDTO> result = jdbcTemplate.query(sql, new Object[] { request.getFromDate(),
				request.getToDate(), request.getTy(), request.getFromDate(), request.getToDate() }, (rs, rowNum) -> {

					ReportResponseDTO dto = new ReportResponseDTO();

					dto.setDt(rs.getDate("dt") != null ? rs.getDate("dt").toLocalDate() : null);

					dto.setNumFiles(rs.getObject("gen_file_cnt", Integer.class));

					dto.setFileNum(rs.getObject("file_num", Integer.class));

					dto.setNumFilesCnt(rs.getObject("num_filescnt", Integer.class));

					dto.setDt2(rs.getDate("dt2") != null ? rs.getDate("dt2").toLocalDate() : null);

					dto.setFileNumber(rs.getObject("file_number", Integer.class));

					dto.setJsonCount(rs.getObject("cntt", Long.class));

					return dto;
				});

		log.info("PARTA/PARTB Records Found={} Time Taken={} ms", result.size(),
				(System.currentTimeMillis() - startTime));

		return result;
	}

	private List<ReportResponseDTO> generateEwbReport(ReportRequestDTO request) {

		long startTime = System.currentTimeMillis();

		String jsonTable = "PARTA".equalsIgnoreCase(request.getTy()) ? "log.ewb_parta_initial_json"
				: "log.ewb_partb_initial_json";

		String sql = """
				SELECT
				    t1.dt,
				    t1.gen_file_cnt,
				    t1.file_num,
				    t1.num_filescnt,
				    t3.dt AS dt2,
				    t3.file_number,
				    t3.cntt
				FROM
				(
				    SELECT
				        to_date(A.dt,'DD-MM-YYYY') as dt,
				        A.gen_file_cnt,
				        B.file_num,
				        B.total_records as num_filescnt
				    FROM filecounter.ewb_count_data A
				    LEFT JOIN filecounter.ewb_file_detail B
				        ON A.id = B.return_file_count_id
				    WHERE
				        to_date(A.dt,'DD-MM-YYYY') BETWEEN ? AND ?
				        AND A.is_success = true
				        AND A.ty = ?
				) t1
				LEFT JOIN
				(
				    SELECT
				        to_date(dt,'DD-MM-YYYY') as dt,
				        file_number,
				        count(ewb->'EwbNo') as cntt
				    FROM %s
				    LEFT JOIN LATERAL
				        jsonb_array_elements(json_data->'ewb') as ewb(value)
				        ON true
				    WHERE
				        to_date(dt,'DD-MM-YYYY') BETWEEN ? AND ?
				    GROUP BY
				        to_date(dt,'DD-MM-YYYY'),
				        file_number
				) t3
				ON t1.dt = t3.dt
				AND t1.file_num = t3.file_number
				ORDER BY t1.dt DESC, t1.file_num DESC
				""".formatted(jsonTable);

		String logQuery = sql.replaceFirst("\\?", "'" + request.getFromDate() + "'")
				.replaceFirst("\\?", "'" + request.getToDate() + "'").replaceFirst("\\?", "'" + request.getTy() + "'")
				.replaceFirst("\\?", "'" + request.getFromDate() + "'")
				.replaceFirst("\\?", "'" + request.getToDate() + "'");

		log.info("PARTA/PARTB QUERY:\n{}", logQuery);

		List<ReportResponseDTO> result = jdbcTemplate.query(sql, new Object[] { request.getFromDate(),
				request.getToDate(), request.getTy(), request.getFromDate(), request.getToDate() }, (rs, rowNum) -> {

					ReportResponseDTO dto = new ReportResponseDTO();

					dto.setDt(rs.getDate("dt") != null ? rs.getDate("dt").toLocalDate() : null);

					dto.setNumFiles(rs.getObject("gen_file_cnt", Integer.class));

					dto.setFileNum(rs.getObject("file_num", Integer.class));

					dto.setNumFilesCnt(rs.getObject("num_filescnt", Integer.class));

					dto.setDt2(rs.getDate("dt2") != null ? rs.getDate("dt2").toLocalDate() : null);

					dto.setFileNumber(rs.getObject("file_number", Integer.class));

					dto.setJsonCount(rs.getObject("cntt", Long.class));

					return dto;
				});

		log.info("PARTA/PARTB Records Found={} Time Taken={} ms", result.size(),
				(System.currentTimeMillis() - startTime));

		return result;
	}

	private List<ReportResponseDTO> generatePaymentReport(ReportRequestDTO request) {

		long startTime = System.currentTimeMillis();

		log.info("====================================================");
		log.info("PAYMENT REPORT GENERATION STARTED");
		log.info("FROM DATE : {}", request.getFromDate());
		log.info("TO DATE   : {}", request.getToDate());
		log.info("====================================================");

		String sql = """
				SELECT
				    t1.dt,
				    t1.num_files,
				    t1.file_num,
				    t1.num_filescnt,
				    t3.dt AS dt2,
				    t3.filenumber,
				    t3.cntt AS jsoncount
				FROM
				(
				    SELECT
				        A.dt,
				        A.num_files,
				        B.file_num,
				        B.cnt AS num_filescnt
				    FROM filecounter."ReturnFileCount" A
				    LEFT JOIN filecounter."ReturnFileDetail" B
				        ON A."ReturnFileCountId" = B."ReturnFileCountId"
				    WHERE A.ty = 'payment'
				      AND A.dt BETWEEN ? AND ?
				      AND A."IsSuccess" = '1'
				) t1
				LEFT JOIN
				(
				    SELECT
				        dt,
				        filenumber,
				        CAST(SUM(cntt) AS BIGINT) AS cntt
				    FROM
				    (
				        SELECT
				            dt,
				            filenumber,
				            COUNT(epy->'cin') AS cntt
				        FROM log.payment_initial_json
				        LEFT JOIN LATERAL
				            jsonb_array_elements(jsondata->'cin'->'epy') epy(value)
				            ON TRUE
				        WHERE dt BETWEEN ? AND ?
				        GROUP BY dt, filenumber

				        UNION ALL

				        SELECT
				            dt,
				            filenumber,
				            COUNT(ner->'cin') AS cntt
				        FROM log.payment_initial_json
				        LEFT JOIN LATERAL
				            jsonb_array_elements(jsondata->'cin'->'ner') ner(value)
				            ON TRUE
				        WHERE dt BETWEEN ? AND ?
				        GROUP BY dt, filenumber

				        UNION ALL

				        SELECT
				            dt,
				            filenumber,
				            COUNT(otc->'cin') AS cntt
				        FROM log.payment_initial_json
				        LEFT JOIN LATERAL
				            jsonb_array_elements(jsondata->'cin'->'otc') otc(value)
				            ON TRUE
				        WHERE dt BETWEEN ? AND ?
				        GROUP BY dt, filenumber

				    ) x
				    GROUP BY dt, filenumber
				) t3
				ON t1.dt = t3.dt
				AND t1.file_num = t3.filenumber
				ORDER BY t1.dt DESC, t1.file_num DESC
				""";

		String logQuery = sql.replaceFirst("\\?", "'" + request.getFromDate() + "'")
				.replaceFirst("\\?", "'" + request.getToDate() + "'")
				.replaceFirst("\\?", "'" + request.getFromDate() + "'")
				.replaceFirst("\\?", "'" + request.getToDate() + "'")
				.replaceFirst("\\?", "'" + request.getFromDate() + "'")
				.replaceFirst("\\?", "'" + request.getToDate() + "'")
				.replaceFirst("\\?", "'" + request.getFromDate() + "'")
				.replaceFirst("\\?", "'" + request.getToDate() + "'");

		log.info("PAYMENT QUERY:\n{}", logQuery);

		try {

			List<ReportResponseDTO> result = jdbcTemplate.query(sql,
					new Object[] { request.getFromDate(), request.getToDate(),

							request.getFromDate(), request.getToDate(),

							request.getFromDate(), request.getToDate(),

							request.getFromDate(), request.getToDate() },
					(rs, rowNum) -> {

						ReportResponseDTO dto = new ReportResponseDTO();

						dto.setDt(rs.getDate("dt") != null ? rs.getDate("dt").toLocalDate() : null);

						dto.setNumFiles(rs.getObject("num_files", Integer.class));

						dto.setFileNum(rs.getObject("file_num", Integer.class));

						dto.setNumFilesCnt(rs.getObject("num_filescnt", Integer.class));

						dto.setDt2(rs.getDate("dt2") != null ? rs.getDate("dt2").toLocalDate() : null);

						dto.setFileNumber(rs.getObject("filenumber", Integer.class));

						dto.setJsonCount(rs.getObject("jsoncount") == null ? 0L : rs.getLong("jsoncount"));

						return dto;
					});

			log.info("====================================================");
			log.info("PAYMENT REPORT GENERATED SUCCESSFULLY");
			log.info("TOTAL RECORDS : {}", result.size());
			log.info("TIME TAKEN    : {} ms", (System.currentTimeMillis() - startTime));
			log.info("====================================================");

			return result;

		} catch (Exception ex) {

			log.error("Error while generating PAYMENT report", ex);

			throw new RuntimeException("Unable to generate PAYMENT report.", ex);
		}
	}

	public List<LastUpdateDTO> getLastUpdateReport() {

		String sql = """
				SELECT
				    ty,
				    MAX(dt) AS max_dt
				FROM filecounter."ReturnFileCount"
				GROUP BY ty
				ORDER BY ty
				""";

		return jdbcTemplate.query(sql, (rs, rowNum) -> {

			String ty = rs.getString("ty");

			return new LastUpdateDTO(ty, getReportType(ty), rs.getDate("max_dt").toLocalDate());
		});
	}

	public List<LastUpdateDTO> getLastUpdateCrnReport() {

	    String sql = """
	            SELECT
	                'CRN' AS ty,
	                MAX(startdt::date) AS lastupdated_dt
	            FROM filecounter.crn_common
	            WHERE issuccess = 'true'
	            """;

	    return jdbcTemplate.query(
	            sql,
	            (rs, rowNum) -> {

	                String ty = rs.getString("ty");

	                return new LastUpdateDTO(
	                        ty,
	                        getReportType(ty),
	                        rs.getDate("lastupdated_dt").toLocalDate()
	                );
	            }
	    );
	}

	public List<LastUpdateDTO> getLastUpdateRegistrationReport() {

	    String sql = """
	            SELECT
	                'REGISTRATION' AS ty,
	                MAX(start_tm::date) AS lastupdated_dt
	            FROM gst_api_registration."ALERT_REGISTRATION"
	            WHERE issuccess = 'true'
	            """;

	    return jdbcTemplate.query(
	            sql,
	            (rs, rowNum) -> {

	                String ty = rs.getString("ty");

	                return new LastUpdateDTO(
	                        ty,
	                        getReportType(ty),
	                        rs.getDate("lastupdated_dt").toLocalDate()
	                );
	            }
	    );
	}

	public List<LastUpdateDTO> getLastUpdateEwayBillReport() {

	    String sql = """
	            SELECT
	                ty,
	                MAX(TO_DATE(dt, 'DD-MM-YYYY')) AS lastupdated_dt
	            FROM filecounter.ewb_count_data
	            WHERE is_success = 'True'
	            GROUP BY ty
	            ORDER BY ty
	            """;

	    return jdbcTemplate.query(
	            sql,
	            (rs, rowNum) -> {

	                String ty = rs.getString("ty");

	                return new LastUpdateDTO(
	                        ty,
	                        getReportType(ty),
	                        rs.getDate("lastupdated_dt").toLocalDate()
	                );
	            }
	    );
	}

	private String getReportType(String ty) {

		return switch (ty) {
		case "CM8" -> "Composition Form 08";
		case "payment" -> "Payment";
		case "R1" -> "GSTR1";
		case "R10" -> "GSTR10";
		case "R11" -> "GSTR11";
		case "R1A" -> "GSTR1A";
		case "R2B" -> "GSTR2B";
		case "R3B" -> "GSTR3B";
		case "R4" -> "GSTR4";
		case "R6" -> "GSTR6";
		case "R7" -> "GSTR7";
		case "R8" -> "GSTR8";
		case "R9" -> "GSTR9";
		case "R9A" -> "GSTR9A";
		case "R9C" -> "GSTR9C";
		case "CRN" -> "CRN";
		case "REGISTRATION" -> "Registration";
		case "PARTA" -> "E-Way Bill (Part A)";
		case "PARTB" -> "E-Way Bill (Part B)";
		default -> ty;
		};
	}

}