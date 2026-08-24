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





function loadReturnLastUpdate() {

    loadLastUpdate("/api/last-update");

}

function loadCrnLastUpdate() {

    loadLastUpdate("/api/last-update-crn");

}

function loadRegistrationLastUpdate() {

    loadLastUpdate("/api/last-update-registration");

}

function loadEwayBillLastUpdate() {

    loadLastUpdate("/api/last-update-eway-bill");

}

$(document).on("click", "#ledgerSubmitBtn", function() {

    let action = $("#ledgerAction").val();
    let fr_dt = $("#ledgerFromDate").val();
    let to_dt = $("#ledgerToDate").val();

    if (fr_dt === "") {
        alert("Select From Date");
        return;
    }

    if (to_dt === "") {
        alert("Select To Date");
        return;
    }

    $("#ledgerSubmitBtn").hide();

    $("#ledgerLoader").show();

    $("#ledgerSuccess").hide();

    $("#ledgerError").hide();

    $.ajax({

        url: "/common/ledger/schedule-ledger-on-automatic",

        type: "GET",

        data: {

            action: action,
            fr_dt: fr_dt,
            to_dt: to_dt

        },

        success: function(response) {

            $("#ledgerLoader").hide();

            $("#ledgerSuccess")
                .html(response)
                .show();

            $("#ledgerSubmitBtn").show();

        },

        error: function(xhr) {

            $("#ledgerLoader").hide();

            $("#ledgerError")
                .html(xhr.responseText)
                .show();

            $("#ledgerSubmitBtn").show();

        }

    });

});


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
