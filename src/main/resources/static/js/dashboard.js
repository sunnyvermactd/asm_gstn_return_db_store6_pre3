let reportTable = null;

/*
==================================================
PAGE LOAD
==================================================
*/
$(document).ready(function() {

    if ($("#reportTable").length) {

        reportTable = $("#reportTable").DataTable({
            pageLength: 25,
            ordering: true,
            searching: true,
            responsive: true,
            destroy: true
        });
    }

    showSection("dashboard");

});


/*
==================================================
SIDEBAR NAVIGATION
==================================================
*/
function showSection(section) {
    // Always reset GSTIN-dependent UI when changing application sections.
    resetGstinDependentSection();

    $(".sidebar a[data-section]").removeClass("active");
    $(".sidebar a[data-section=\"" + section + "\"]").addClass("active");

    $("#dashboardSection").hide();
    $("#reportSection").hide();
    $("#lastUpdateSection").hide();
    $("#mismatchSection").hide();
    $("#statisticsSection").hide();
    $("#healthSection").hide();
    $("#returnOnFlySection").hide();
    $("#crnReportSection").hide();
    $("#registrationReportSection").hide();
    $("#ledgerSection").hide();
    $("#EwayBillNewSection").hide();   // ADD THIS
    $("#ewaySearchSection").hide();

    switch (section) {

        case "dashboard":
            $("#dashboardSection").show();
            break;

        case "report":
            $("#reportSection").show();
            break;

        case "lastUpdate":
            $("#lastUpdateSection").show();
            loadReturnLastUpdate();   // Default API
            break;

        case "mismatch":
            $("#mismatchSection").show();
            break;

        case "statistics":
            $("#statisticsSection").show();
            break;

        case "health":
            $("#healthSection").show();
            break;

        case "returnOnFlySection":
            $("#returnOnFlySection").show();
            break;

        case "ledger":

            $("#ledgerSection").show();

            break;



        case "crnReport":

            $("#dashboardSection").hide();
            $("#reportSection").hide();
            $("#lastUpdateSection").hide();
            $("#mismatchSection").hide();
            $("#statisticsSection").hide();
            $("#healthSection").hide();
            $("#returnOnFlySection").hide();
            $("#crnReportSection").show();
            break;

        case "registrationReport":
            $("#registrationReportSection").show();
            break;

        case "EwayBillNew":      // ADD THIS
            $("#EwayBillNewSection").show();
            break;

        case "ewaySearch":

            $("#ewaySearchSection").show();

            loadEwayBillSearch();

            break;


        default:
            $("#dashboardSection").show();
            break;
    }
}

/*
==================================================
REGISTRATION REPORT DATATABLE
==================================================
*/

let registrationReportTable = null;

$(document).ready(function() {

    if ($("#registrationReportTable").length) {

        registrationReportTable = $("#registrationReportTable").DataTable({

            pageLength: 25,
            ordering: true,
            searching: true,
            responsive: true,
            destroy: true

        });

    }

});

/*
==================================================
GENERATE REGISTRATION REPORT
==================================================
*/

$(document).on("click", "#generateRegistrationBtn", function() {

    let fromDate = $("#registrationFromDate").val();

    let toDate = $("#registrationToDate").val();

    if (!fromDate) {

        alert("Select From Date");

        return;

    }

    if (!toDate) {

        alert("Select To Date");

        return;

    }

    $("#registrationProcessingDiv").show();

    $("#registrationSuccessDiv").hide();

    $.ajax({

        url: "/api/return-gstr/registration-report",

        type: "POST",

        contentType: "application/json",

        data: JSON.stringify({

            fromDate: fromDate,

            toDate: toDate

        }),

        success: function(response) {

            registrationReportTable.clear();

            response.forEach(function(r) {

                registrationReportTable.row.add([

                    r.startDate,

                    r.totalFetchArn,

                    r.totalArnSuccess,

                    r.totalArnFailure,

                    r.totalFetchEntity,

                    r.totalFetchSuccessEntity,

                    r.totalFetchFailureEntity,

                    r.insertCount

                ]);

            });

            registrationReportTable.draw();

            $("#registrationProcessingDiv").hide();

            $("#registrationSuccessDiv")
                .html("Registration Report Generated Successfully")
                .show();

        },

        error: function(xhr) {

            $("#registrationProcessingDiv").hide();

            alert("Error generating Registration Report");

            console.log(xhr.responseText);

        }

    });

});


/*
==================================================
GENERATE REPORT
==================================================
*/
$(document).on("click", "#generateBtn", function() {

    let fromDate = $("#fromDate").val();
    let toDate = $("#toDate").val();
    let ty = $("#ty").val();

    if (!fromDate) {
        alert("Please Select From Date");
        return;
    }

    if (!toDate) {
        alert("Please Select To Date");
        return;
    }

    $("#processingDiv").show();
    $("#successDiv").hide();

    $("#generateBtn")
        .prop("disabled", true)
        .removeClass("btn-success")
        .addClass("btn-secondary");

    $.ajax({

        url: "/api/report",

        type: "POST",

        contentType: "application/json",

        data: JSON.stringify({
            fromDate: fromDate,
            toDate: toDate,
            ty: ty
        }),

        success: function(response) {

            reportTable.clear();

            response.forEach(function(r) {

                let status =
                    Number(r.numFilesCnt || 0) === Number(r.jsonCount || 0)
                        ? '<span class="badge bg-success">MATCHED</span>'
                        : '<span class="badge bg-danger">MISMATCH</span>';

                reportTable.row.add([
                    r.dt || '',
                    r.numFiles || 0,
                    r.fileNum || 0,
                    r.numFilesCnt || 0,
                    r.dt2 || '',
                    r.fileNumber || 0,
                    r.jsonCount || 0,
                    status
                ]);

            });

            reportTable.draw();

            $("#processingDiv").hide();

            $("#successDiv")
                .html("Report Generated Successfully")
                .show();

            $("#generateBtn")
                .prop("disabled", false)
                .removeClass("btn-secondary")
                .addClass("btn-success");
        },

        error: function(xhr) {

            $("#processingDiv").hide();

            $("#generateBtn")
                .prop("disabled", false)
                .removeClass("btn-secondary")
                .addClass("btn-success");

            alert("Error while generating report");

            console.error(xhr.responseText);
        }

    });

});


/*
==================================================
LAST UPDATE DASHBOARD
==================================================
*/
function loadLastUpdate(url) {

    $.ajax({

        url: url,

        type: "GET",

        success: function(response) {

            let cardHtml = "";
            let tableHtml = "";

            response.forEach(function(r) {

                cardHtml += `
                    <div class="col-md-2 mb-3">
                        <div class="card shadow-sm update-card"
                             onclick="triggerDownload('${r.ty}')">

                            <div class="card-body text-center">

                                <h6>${r.ty}</h6>

                                <strong>${r.maxDate}</strong>

                                <hr>

                                <small class="text-primary fw-bold">
                                    Click To Run
                                </small>

                            </div>

                        </div>
                    </div>
                `;

                tableHtml += `
                    <tr>
                        <td>${r.ty}</td>
                        <td>${r.maxDate}</td>
                    </tr>
                `;

            });

            $("#updateCards").html(cardHtml);
            $("#lastUpdateTable tbody").html(tableHtml);

        }

    });

}



/*
==================================================
DOWNLOAD PDF
==================================================
*/
function downloadPdf() {

    const request = {
        fromDate: document.getElementById("fromDate").value,
        toDate: document.getElementById("toDate").value,
        ty: document.getElementById("ty").value
    };

    fetch('/api/pdf', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    })
        .then(response => response.blob())
        .then(blob => {

            const url = window.URL.createObjectURL(blob);

            const a = document.createElement('a');

            a.href = url;

            a.download = 'Report.pdf';

            document.body.appendChild(a);

            a.click();

            a.remove();

            window.URL.revokeObjectURL(url);
        });
}


/*
==================================================
DOWNLOAD EXCEL
==================================================
*/
function downloadExcel() {

    const request = {
        fromDate: document.getElementById("fromDate").value,
        toDate: document.getElementById("toDate").value,
        ty: document.getElementById("ty").value
    };

    fetch('/api/excel', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    })
        .then(response => response.blob())
        .then(blob => {

            const url = window.URL.createObjectURL(blob);

            const a = document.createElement('a');

            a.href = url;

            a.download = 'Report.xlsx';

            document.body.appendChild(a);

            a.click();

            a.remove();

            window.URL.revokeObjectURL(url);
        });
}


/*
==================================================
DASHBOARD SUMMARY
==================================================
*/
function loadDashboardSummary() {

    $.ajax({

        url: "/api/dashboard-summary",

        type: "GET",

        success: function(response) {

            $("#totalReturns").text(response.totalReturns);
            $("#matchedCount").text(response.matchedCount);
            $("#mismatchCount").text(response.mismatchCount);
            $("#lastUpdatedDate").text(response.lastUpdatedDate);
        }

    });
}

/*
==================================================
CRN REPORT
==================================================
*/

let crnReportTable = null;

$(document).ready(function() {

    if ($("#crnReportTable").length) {

        crnReportTable = $("#crnReportTable").DataTable({
            pageLength: 25,
            ordering: true,
            searching: true,
            responsive: true,
            destroy: true
        });

    }

});


/*
==================================================
GENERATE CRN REPORT
==================================================
*/

$(document).on("click", "#generateCrnBtn", function() {

    let fromDate = $("#crnFromDate").val();
    let toDate = $("#crnToDate").val();
    let caseType = $("#caseType").val();

    if (!fromDate) {
        alert("Please Select From Date");
        return;
    }

    if (!toDate) {
        alert("Please Select To Date");
        return;
    }

    $("#crnProcessingDiv").show();

    $("#crnSuccessDiv").hide();

    $("#generateCrnBtn")
        .prop("disabled", true)
        .removeClass("btn-success")
        .addClass("btn-secondary");


    $.ajax({

        url: "/api/return-gstr/crn-report",

        type: "POST",

        contentType: "application/json",

        data: JSON.stringify({

            fromDate: fromDate,

            toDate: toDate,

            caseType: caseType

        }),

        success: function(response) {

            crnReportTable.clear();

            response.forEach(function(r) {

                let status =
                    Number(r.downloadCount || 0) === Number(r.insertCount || 0)
                        ? '<span class="badge bg-success">MATCHED</span>'
                        : '<span class="badge bg-danger">MISMATCH</span>';

                crnReportTable.row.add([

                    r.startDate || '',

                    r.caseType || '',

                    r.downloadCount || 0,

                    r.insertCount || 0,

                    status

                ]);

            });

            crnReportTable.draw();

            $("#crnProcessingDiv").hide();

            $("#crnSuccessDiv")
                .html("CRN Report Generated Successfully")
                .show();

            $("#generateCrnBtn")
                .prop("disabled", false)
                .removeClass("btn-secondary")
                .addClass("btn-success");

        },

        error: function(xhr) {

            $("#crnProcessingDiv").hide();

            $("#generateCrnBtn")
                .prop("disabled", false)
                .removeClass("btn-secondary")
                .addClass("btn-success");

            alert("Error while generating CRN Report");

            console.log(xhr.responseText);

        }

    });

});


/*
==================================================
RUN DOWNLOAD API
==================================================

function triggerDownload(ty) {

    let apiMap = {

        "CM8": "/common/gstr/CM8",
        "payment": "/common/gstr/payment",

        "R1": "/common/gstr/R1",
        "R1A": "/common/gstr/R1A",

        "R2B": "/common/gstr/R2B",
        "R3B": "/common/gstr/R3B",

        "R4": "/common/gstr/R4",
        "R5": "/common/gstr/R5",
        "R6": "/common/gstr/R6",
        "R7": "/common/gstr/R7",
        "R8": "/common/gstr/R8",

        "R9": "/common/gstr/R9",
        "R9A": "/common/gstr/R9A",
        "R9C": "/common/gstr/R9C",

        "R98A": "/common/gstr/R98A",

        "R10": "/common/gstr/R10",
        "R11": "/common/gstr/R11",
        // CRN
        "CRN": "/api/crn/process-crn-scheduler",

        // Registration
        "REGISTRATION": "/api/registration/complete-registration-automation-final-verdict",

        // E-Way Bill
          "PARTA": "http://10.79.1.225:8063/EwayBill/schedule-PARTA",
          "PARTB": "http://10.79.1.225:8063/EwayBill/schedule-PARTB"
    };

    let url = apiMap[ty];

    if (!url) {

        alert("API not configured for : " + ty);
        return;
    }

    if (!confirm("Run Download Process For " + ty + " ?")) {
        return;
    }

    $.ajax({

        url: url,

        type: "GET",

        beforeSend: function() {

            $("body").append(`
                <div id="loadingOverlay"
                     style="
                     position:fixed;
                     top:0;
                     left:0;
                     width:100%;
                     height:100%;
                     background:rgba(0,0,0,.5);
                     z-index:9999;
                     display:flex;
                     justify-content:center;
                     align-items:center;
                     color:white;
                     font-size:22px;">
                     Processing ${ty} ...
                </div>
            `);
        },

        success: function(response) {

            $("#loadingOverlay").remove();

            alert("Success\n\n" + response);

            // loadLastUpdate();
            loadReturnLastUpdate();
        },

        error: function(xhr) {

            $("#loadingOverlay").remove();

            alert("Failed : " + xhr.responseText);
        }

    });
}


*/





function setLastUpdateTab(tab) {
    $("[data-last-update-tab]").removeClass("active");
    $("[data-last-update-tab=\"" + tab + "\"]").addClass("active");
}

function resetGstinDependentSection() {
    $("#gstinDependentSection").hide();
    $("#comparisonReportResult").empty();
    $("#processDocumentsDhResult").empty();
    $("#comparisonReportLoader").hide();
    $("#processDocumentsDhLoader").hide();
}

function loadReturnLastUpdate() {
    resetGstinDependentSection();
    setLastUpdateTab("return");
    loadLastUpdate("/api/last-update");
}

function loadCrnLastUpdate() {
    resetGstinDependentSection();
    setLastUpdateTab("crn");
    loadLastUpdate("/api/last-update-crn");
}

function loadRegistrationLastUpdate() {
    resetGstinDependentSection();
    setLastUpdateTab("registration");
    loadLastUpdate("/api/last-update-registration");
}

function loadEwayBillLastUpdate() {
    resetGstinDependentSection();
    setLastUpdateTab("eway");
    loadLastUpdate("/api/last-update-eway-bill");
}

function loadGstinDependent() {
    setLastUpdateTab("gstin");
    $("#gstinDependentSection").show();
    $("#comparisonReportResult").empty();
    $("#processDocumentsDhResult").empty();
}


/*
==================================================
LEDGER SCHEDULER
YEAR WISE / MONTH WISE
==================================================
*/

$(document).ready(function() {

    initializeLedgerScheduler();

});


/*
==================================================
INITIALIZE LEDGER SCHEDULER
==================================================
*/

function initializeLedgerScheduler() {

    const dataType = $("#ledgerDataType");

    if (!dataType.length) {
        return;
    }

    populateLedgerFinancialYears();

    populateLedgerMonthYears();

    updateLedgerMonthAvailability();

}


/*
==================================================
POPULATE FINANCIAL YEARS
==================================================

Example:

2026-27
2025-26
2024-25
2023-24

Internally:

2025-26
=> 2025-04-01 to 2026-03-31

==================================================
*/

function populateLedgerFinancialYears() {

    const select =
        $("#ledgerFinancialYear");

    if (!select.length) {
        return;
    }

    select.empty();

    select.append(
        '<option value="">Select Financial Year</option>'
    );


    const currentYear =
        new Date().getFullYear();


    /*
     * Example:
     *
     * Current year = 2026
     *
     * First FY:
     * 2026-27
     *
     * Then:
     * 2025-26
     * 2024-25
     * ...
     */

    for (
        let year = currentYear;
        year >= 2020;
        year--
    ) {

        const nextYear = year + 1;

        const displayValue =
            year + "-" +
            String(nextYear).slice(-2);

        const actualValue =
            year + "-" +
            nextYear;


        select.append(
            $("<option>", {
                value: actualValue,
                text: displayValue
            })
        );

    }

}


/*
==================================================
POPULATE MONTH YEARS
==================================================

Example:

2026
2025
2024
...

==================================================
*/

/* =========================================================
   INITIALIZE MONTH WISE
   ========================================================= */

function populateLedgerMonthYears() {

    const yearDropdown = $("#ledgerMonthYear");

    if (yearDropdown.length === 0) {
        return;
    }

    yearDropdown.empty();

    yearDropdown.append(
        '<option value="">Select Year</option>'
    );

    const currentYear = new Date().getFullYear();

    /*
     * Current year + previous 5 years
     *
     * Example:
     * 2026
     * 2025
     * 2024
     * 2023
     * 2022
     * 2021
     */
    for (
        let year = currentYear;
        year >= currentYear - 5;
        year--
    ) {

        yearDropdown.append(
            '<option value="' +
            year +
            '">' +
            year +
            '</option>'
        );
    }


    /*
     * Initially month is enabled.
     *
     * We will control individual months
     * after year selection.
     */
    $("#ledgerMonth").prop("disabled", false);

}


/* =========================================================
   YEAR CHANGE
   ========================================================= */

$(document).on(
    "change",
    "#ledgerMonthYear",
    function() {

        const selectedYear =
            parseInt($(this).val());

        const monthDropdown =
            $("#ledgerMonth");


        /*
         * No year selected
         */
        if (!selectedYear) {

            monthDropdown
                .val("")
                .prop("disabled", true);

            return;
        }


        /*
         * Enable month dropdown
         */
        monthDropdown.prop("disabled", false);


        /*
         * Update which months are allowed
         */
        updateLedgerMonthAvailability();

    }
);


/* =========================================================
   MONTH AVAILABILITY
   ========================================================= */

function updateLedgerMonthAvailability() {

    const selectedYear =
        parseInt($("#ledgerMonthYear").val());

    const monthDropdown =
        $("#ledgerMonth");


    /*
     * No year selected
     */
    if (!selectedYear) {

        monthDropdown
            .val("")
            .prop("disabled", true);

        return;
    }


    /*
     * VERY IMPORTANT:
     * Enable the month dropdown.
     */
    monthDropdown.prop("disabled", false);


    const today = new Date();

    const currentYear =
        today.getFullYear();

    const currentMonth =
        today.getMonth() + 1;


    /*
     * Check every month
     */
    monthDropdown.find("option").each(function() {

        const option = $(this);

        const monthValue =
            parseInt(option.val());


        /*
         * "Select Month"
         */
        if (isNaN(monthValue)) {

            option.prop("disabled", false);

            return;
        }


        /*
         * FUTURE YEAR
         *
         * Example:
         * Current year = 2026
         * Selected year = 2027
         *
         * All months disabled.
         */
        if (selectedYear > currentYear) {

            option.prop("disabled", true);

            return;
        }


        /*
         * PREVIOUS YEAR
         *
         * All months enabled.
         */
        if (selectedYear < currentYear) {

            option.prop("disabled", false);

            return;
        }


        /*
         * CURRENT YEAR
         *
         * Only previous months are allowed.
         *
         * Current month and future months
         * are disabled.
         */
        if (monthValue >= currentMonth) {

            option.prop("disabled", true);

        } else {

            option.prop("disabled", false);
        }

    });


    /*
     * Clear selected month after changing year.
     */
    monthDropdown.val("");

}


/* =========================================================
   CALCULATE MONTH DATES
   ========================================================= */

function calculateLedgerMonthDates() {

    const selectedYear =
        parseInt($("#ledgerMonthYear").val());

    const selectedMonth =
        parseInt($("#ledgerMonth").val());


    if (!selectedYear || !selectedMonth) {

        return null;
    }


    /*
     * YYYY-MM
     */
    const month =
        String(selectedMonth).padStart(2, "0");


    /*
     * First day
     */
    const fr_dt =
        selectedYear +
        "-" +
        month +
        "-01";


    /*
     * Last day
     */
    const lastDay =
        new Date(
            selectedYear,
            selectedMonth,
            0
        ).getDate();


    const to_dt =
        selectedYear +
        "-" +
        month +
        "-" +
        String(lastDay).padStart(2, "0");


    return {

        fr_dt: fr_dt,

        to_dt: to_dt

    };

}


/*
==================================================
DATA TYPE CHANGE
==================================================
*/

$(document).on(
    "change",
    "#ledgerDataType",
    function() {

        const selectedType =
            $(this).val();


        if (selectedType === "YEAR") {

            $("#ledgerYearSection").show();

            $("#ledgerMonthSection").hide();


            /*
             * Clear month selection
             */
            $("#ledgerMonth").val("");

            $("#ledgerMonthYear").val("");

        }


        else if (selectedType === "MONTH") {

            $("#ledgerYearSection").hide();

            $("#ledgerMonthSection").show();


            /*
             * Clear financial year
             */
            $("#ledgerFinancialYear").val("");


            updateLedgerMonthAvailability();

        }


        $("#ledgerSuccess").hide();

        $("#ledgerError").hide();

    }
);


/*
==================================================
MONTH YEAR CHANGE
==================================================
*/

$(document).on(
    "change",
    "#ledgerMonthYear",
    function() {

        updateLedgerMonthAvailability();

    }
);


/*
==================================================
ENABLE / DISABLE MONTHS
==================================================

Rule:

Past month       -> ENABLED

Current month    -> DISABLED

Future month     -> DISABLED

Example:

Current date:
September 2026

Allowed:

January 2026
February 2026
March 2026
April 2026
May 2026
June 2026
July 2026
August 2026

Not allowed:

September 2026
October 2026
November 2026
December 2026

For 2025:

All months allowed.

==================================================
*/

function updateLedgerMonthAvailability() {

    const selectedYear =
        parseInt(
            $("#ledgerMonthYear").val()
        );


    const today =
        new Date();


    const currentYear =
        today.getFullYear();


    const currentMonth =
        today.getMonth() + 1;


    $("#ledgerMonth option").each(
        function() {

            const option =
                $(this);


            /*
             * Placeholder
             */
            if (!option.val()) {

                option.prop(
                    "disabled",
                    false
                );

                return;
            }


            const selectedMonth =
                parseInt(option.val());


            /*
             * No year selected
             */
            if (!selectedYear) {

                option.prop(
                    "disabled",
                    true
                );

                return;
            }


            /*
             * Future year
             */
            if (selectedYear > currentYear) {

                option.prop(
                    "disabled",
                    true
                );

            }


            /*
             * Current year
             *
             * Current month and future months
             * are disabled.
             */
            else if (
                selectedYear === currentYear &&
                selectedMonth >= currentMonth
            ) {

                option.prop(
                    "disabled",
                    true
                );

            }


            /*
             * Previous year
             *
             * All months allowed.
             */
            else {

                option.prop(
                    "disabled",
                    false
                );

            }

        }
    );


    /*
     * If selected month is now disabled,
     * clear it.
     */

    const selectedMonth =
        $("#ledgerMonth option:selected");


    if (
        selectedMonth.length &&
        selectedMonth.prop("disabled")
    ) {

        $("#ledgerMonth").val("");

    }

}


/*
==================================================
CALCULATE FINANCIAL YEAR DATES
==================================================

Input:

2025-2026

Output:

fromDate = 2025-04-01
toDate   = 2026-03-31

==================================================
*/

function calculateLedgerFinancialYearDates() {

    const financialYear =
        $("#ledgerFinancialYear").val();


    if (!financialYear) {

        throw new Error(
            "Please select Financial Year."
        );

    }


    const parts =
        financialYear.split("-");


    const startYear =
        parseInt(parts[0]);


    const endYear =
        parseInt(parts[1]);


    return {

        fromDate:
            startYear + "-04-01",

        toDate:
            endYear + "-03-31"

    };

}


/*
==================================================
CALCULATE MONTH DATES
==================================================

Example:

Month = January
Year  = 2026

Output:

fromDate = 2026-01-01
toDate   = 2026-01-31

==================================================
*/

function calculateLedgerMonthDates() {

    const selectedMonth =
        parseInt(
            $("#ledgerMonth").val()
        );


    const selectedYear =
        parseInt(
            $("#ledgerMonthYear").val()
        );


    if (!selectedYear) {

        throw new Error(
            "Please select Year."
        );

    }


    if (!selectedMonth) {

        throw new Error(
            "Please select Month."
        );

    }


    /*
     * Additional backend safety validation
     */

    const today =
        new Date();


    const currentYear =
        today.getFullYear();


    const currentMonth =
        today.getMonth() + 1;


    /*
     * Current month and future months
     * are not allowed.
     */

    if (
        selectedYear > currentYear ||
        (
            selectedYear === currentYear &&
            selectedMonth >= currentMonth
        )
    ) {

        throw new Error(
            "Current month and future months are not allowed."
        );

    }


    /*
     * JavaScript month is zero based.
     *
     * Using:
     *
     * new Date(year, month, 0)
     *
     * gives last day of selected month.
     *
     * Example:
     *
     * new Date(2026, 1, 0)
     *
     * = 31 January 2026
     */

    const lastDay =
        new Date(
            selectedYear,
            selectedMonth,
            0
        ).getDate();


    const monthString =
        String(selectedMonth)
            .padStart(2, "0");


    const lastDayString =
        String(lastDay)
            .padStart(2, "0");


    return {

        fromDate:
            selectedYear +
            "-" +
            monthString +
            "-01",

        toDate:
            selectedYear +
            "-" +
            monthString +
            "-" +
            lastDayString

    };

}


/*
==================================================
LEDGER SUBMIT
==================================================
*/

$(document).on(
    "click",
    "#ledgerSubmitBtn",
    function() {

        const button =
            $("#ledgerSubmitBtn");


        const loader =
            $("#ledgerLoader");


        const success =
            $("#ledgerSuccess");


        const error =
            $("#ledgerError");


        /*
         * Clear previous messages
         */

        success.hide();

        error.hide();


        try {

            /*
             * ================================================
             * ACTION
             * ================================================
             */

            const action =
                $("#ledgerAction").val();


            /*
             * ================================================
             * DATA TYPE
             * ================================================
             */

            const dataType =
                $("#ledgerDataType").val();


            if (!dataType) {

                throw new Error(
                    "Please select Data Type."
                );

            }


            let ledgerDates;


            /*
             * ================================================
             * YEAR WISE
             * ================================================
             */

            if (dataType === "YEAR") {

                ledgerDates =
                    calculateLedgerFinancialYearDates();

            }


            /*
             * ================================================
             * MONTH WISE
             * ================================================
             */

            else if (dataType === "MONTH") {

                ledgerDates =
                    calculateLedgerMonthDates();

            }


            else {

                throw new Error(
                    "Invalid Data Type."
                );

            }


            /*
             * ================================================
             * INTERNAL VALUES
             * ================================================
             *
             * These dates are NOT displayed to the user.
             *
             * Example:
             *
             * 2025-26
             *
             * becomes:
             *
             * fr_dt = 2025-04-01
             * to_dt = 2026-03-31
             *
             * ================================================
             */

            const fr_dt =
                ledgerDates.fromDate;


            const to_dt =
                ledgerDates.toDate;


            /*
             * Console logging for development/debugging.
             *
             * Remove these logs in production if required.
             */

            console.log(
                "Ledger Action:",
                action
            );

            console.log(
                "Ledger Data Type:",
                dataType
            );

            console.log(
                "Ledger From Date:",
                fr_dt
            );

            console.log(
                "Ledger To Date:",
                to_dt
            );


            /*
             * ================================================
             * UI
             * ================================================
             */

            button.hide();

            loader.show();

            success.hide();

            error.hide();


            /*
             * ================================================
             * EXISTING API
             * ================================================
             *
             * DO NOT CHANGE THE BACKEND API.
             *
             * Existing endpoint:
             *
             * /common/ledger/schedule-ledger-function-depedent
             *
             * Existing parameters:
             *
             * action
             * fr_dt
             * to_dt
             *
             * ================================================
             */

            $.ajax({

                url:
                    "/common/ledger/schedule-ledger-function-depedent",

                type:
                    "GET",

                data: {

                    action:
                        action,

                    fr_dt:
                        fr_dt,

                    to_dt:
                        to_dt

                },


                /*
                 * ============================================
                 * SUCCESS
                 * ============================================
                 */

                success:
                    function(response) {

                        loader.hide();

                        success
                            .html(
                                response ||
                                "Ledger processing completed successfully."
                            )
                            .show();

                        button.show();

                    },


                /*
                 * ============================================
                 * ERROR
                 * ============================================
                 */

                error:
                    function(xhr) {

                        loader.hide();

                        let message =
                            "Ledger processing failed.";


                        if (xhr.responseText) {

                            message =
                                xhr.responseText;

                        }


                        error
                            .html(message)
                            .show();


                        button.show();

                    }

            });


        } catch (e) {

            /*
             * Validation error
             */

            loader.hide();

            button.show();

            error
                .html(e.message)
                .show();

        }

    }
);



function triggerDownload(ty) {

    let apiMap = {

        "CM8": "/common/gstr/CM8",
        "payment": "/common/gstr/payment",

        "R1": "/common/gstr/R1",
        "R1A": "/common/gstr/R1A",

        "R2B": "/common/gstr/R2B",
        "R3B": "/common/gstr/R3B",

        "R4": "/common/gstr/R4",
        "R5": "/common/gstr/R5",
        "R6": "/common/gstr/R6",
        "R7": "/common/gstr/R7",
        "R8": "/common/gstr/R8",

        "R9": "/common/gstr/R9",
        "R9A": "/common/gstr/R9A",
        "R9C": "/common/gstr/R9C",

        "R98A": "/common/gstr/R98A",

        "R10": "/common/gstr/R10",
        "R11": "/common/gstr/R11",

        // CRN
        "CRN": "/api/crn/process-crn-scheduler",

        // Registration
        "REGISTRATION": "/common/registration/complete-registration-automation-final-verdict",

        // E-Way Bill
        "PARTA": "http://10.79.1.225:8063/EwayBill/schedule-PARTA",
        "PARTB": "http://10.79.1.225:8063/EwayBill/schedule-PARTB"
    };

    let url = apiMap[ty];

    if (!url) {
        alert("API not configured for : " + ty);
        return;
    }

    if (!confirm("Run Download Process For " + ty + " ?")) {
        return;
    }

    $("body").append(`
        <div id="loadingOverlay"
             style="
                position:fixed;
                top:0;
                left:0;
                width:100%;
                height:100%;
                background:rgba(0,0,0,.5);
                z-index:9999;
                display:flex;
                justify-content:center;
                align-items:center;
                color:white;
                font-size:22px;">
             Processing ${ty}...
        </div>
    `);

    // PARTA / PARTB -> Authenticate first
    if (ty === "PARTA" || ty === "PARTB") {

        $.ajax({

            url: "http://10.79.1.225:8063/EwayBill/authenticate",

            type: "GET",

            success: function() {

                $("#loadingOverlay").html("Authentication Successful.<br>Waiting 30 seconds...");

                setTimeout(function() {

                    $.ajax({

                        url: url,

                        type: "GET",

                        success: function(response) {

                            $("#loadingOverlay").remove();

                            alert("Success\n\n" + response);

                            loadReturnLastUpdate();

                        },

                        error: function(xhr) {

                            $("#loadingOverlay").remove();

                            alert("Failed : " + xhr.responseText);

                        }

                    });

                }, 30000);

            },

            error: function(xhr) {

                $("#loadingOverlay").remove();

                alert("Authentication Failed : " + xhr.responseText);

            }

        });

    } else {

        // Existing flow for all other APIs
        $.ajax({

            url: url,

            type: "GET",

            success: function(response) {

                $("#loadingOverlay").remove();

                alert("Success\n\n" + response);

                loadReturnLastUpdate();

            },

            error: function(xhr) {

                $("#loadingOverlay").remove();

                alert("Failed : " + xhr.responseText);

            }

        });

    }
}

/*
==================================================
E-WAY BILL COMPARISON
==================================================
*/

$(document).on("click", "#searchBtn", function() {
    searchEwayBill();
});

function searchEwayBill() {

    let ewbNo = $("#ewbNo").val().trim();

    if (ewbNo === "") {
        alert("Please Enter E-Way Bill Number");
        return;
    }

    $("#loader").show();
    $("#resultDiv").hide();
    $("#noDataDiv").hide();

    $.ajax({

        url: "/common/gstr/compare/" + ewbNo,

        type: "GET",

        success: function(res) {

            $("#loader").hide();
            $("#resultDiv").show();

            let html = "";
            let matchedCount = 0;

            res.comparisons.forEach(function(c) {

                if (c.matched) {
                    matchedCount++;
                }

                html += `
                    <tr class="${c.matched ? 'table-success' : 'table-danger'}">

                        <td>
                            <strong>${c.field}</strong>
                        </td>

                        <td>
                            ${c.newValue == null ? "" : c.newValue}
                        </td>

                        <td>
                            ${c.oldValue == null ? "" : c.oldValue}
                        </td>

                        <td class="text-center">

                            ${c.matched
                        ? '<span class="badge bg-success">MATCH</span>'
                        : '<span class="badge bg-danger">MISMATCH</span>'
                    }

                        </td>

                    </tr>
                `;

            });

            $("#comparisonBody").html(html);

            $("#summaryBadge").html(
                matchedCount +
                " / " +
                res.comparisons.length +
                " Matched"
            );

        },

        error: function(xhr) {

            $("#loader").hide();
            $("#resultDiv").hide();

            $("#comparisonBody").html("");
            $("#summaryBadge").html("");

            if (xhr.status === 404) {
                $("#noDataDiv").show();
            } else {
                alert("Error while fetching E-Way Bill.");
            }

        }

    });

}
function runProcessDocumentsDh() {

    if (!confirm("Do you want to run Process Documents DH?")) {
        return;
    }

    $("#processDocumentsDhBtn").prop("disabled", true);
    $("#processDocumentsDhLoader").show();
    $("#processDocumentsDhResult").html("");

    $.ajax({

        url: "/common/gstr/process-documents-dh",

        type: "GET",

        success: function(response) {

            $("#processDocumentsDhLoader").hide();
            $("#processDocumentsDhBtn").prop("disabled", false);

            $("#processDocumentsDhResult").html(`
                <div class="alert alert-success mt-2">
                    <strong>Success</strong><br>
                    ${response}
                </div>
            `);

        },

        error: function(xhr) {

            $("#processDocumentsDhLoader").hide();
            $("#processDocumentsDhBtn").prop("disabled", false);

            $("#processDocumentsDhResult").html(`
                <div class="alert alert-danger mt-2">
                    <strong>Failed</strong><br>
                    ${xhr.responseText || "Process Documents DH failed."}
                </div>
            `);

        }

    });
}
function runComparisonReport() {

    if (!confirm("Do you want to run Get Comparison Report?")) {
        return;
    }

    $("#comparisonReportBtn").prop("disabled", true);
    $("#comparisonReportLoader").show();
    $("#comparisonReportResult").html("");

    $.ajax({

        url: "/common/gstr/get-comparison-report",

        type: "GET",

        success: function(response) {

            $("#comparisonReportLoader").hide();
            $("#comparisonReportBtn").prop("disabled", false);

            $("#comparisonReportResult").html(`
                <div class="alert alert-success mt-2">
                    <strong>Success</strong><br>
                    ${response}
                </div>
            `);

        },

        error: function(xhr) {

            $("#comparisonReportLoader").hide();
            $("#comparisonReportBtn").prop("disabled", false);

            $("#comparisonReportResult").html(`
                <div class="alert alert-danger mt-2">
                    <strong>Failed</strong><br>
                    ${xhr.responseText || "Get Comparison Report failed."}
                </div>
            `);

        }

    });
}

//
function runProcessNormal() {

    if (!confirm("Do you want to run Process Normal Tax Payer?")) {
        return;
    }

    $("#processNormalTaxPayerBtn").prop("disabled", true);
    $("#processNormalTaxPayerLoader").show();
    $("#processNormalTaxPayerResult").html("");

    $.ajax({

        url: "/common/gstr/get-normal-taxpayer",

        type: "GET",

        success: function(response) {

            $("#processNormalTaxPayerLoader").hide();
            $("#processNormalTaxPayerBtn").prop("disabled", false);

            $("#processNormalTaxPayerResult").html(`
                <div class="alert alert-success mt-2">
                    <strong>Success</strong><br>
                    ${response}
                </div>
            `);

        },

        error: function(xhr) {

            $("#processNormalTaxPayerLoader").hide();
            $("#processNormalTaxPayerBtn").prop("disabled", false);

            $("#processNormalTaxPayerResult").html(`
                <div class="alert alert-danger mt-2">
                    <strong>Failed</strong><br>
                    ${xhr.responseText || "Process Normal Tax Payer failed."}
                </div>
            `);

        }

    });
}

//
function runProcessTdsTcs() {

    if (!confirm("Do you want to run Process TdsTcs Tax Payer?")) {
        return;
    }

    $("#processTdsTcsTaxPayerBtn").prop("disabled", true);
    $("#processTdsTcsTaxPayerLoader").show();
    $("#processTdsTcsTaxPayerResult").html("");

    $.ajax({

        url: "/common/gstr/get-tds-tcs-taxpayer",

        type: "GET",

        success: function(response) {

            $("#processTdsTcsTaxPayerLoader").hide();
            $("#processTdsTcsTaxPayerBtn").prop("disabled", false);

            $("#processTdsTcsTaxPayerResult").html(`
                <div class="alert alert-success mt-2">
                    <strong>Success</strong><br>
                    ${response}
                </div>
            `);

        },

        error: function(xhr) {

            $("#processTdsTcsTaxPayerLoader").hide();
            $("#processTdsTcsTaxPayerBtn").prop("disabled", false);

            $("#processTdsTcsTaxPayerResult").html(`
                <div class="alert alert-danger mt-2">
                    <strong>Failed</strong><br>
                    ${xhr.responseText || "Process TdsTcs Tax Payer failed."}
                </div>
            `);

        }

    });
}
