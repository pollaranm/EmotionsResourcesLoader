$(document).on("click", "#drop", function () {

    alertify.confirm("Are you sure you want to DELETE ALL THE TABLES?", function (e) {
        if (e) {
            // user clicked "ok"
            $.ajax({
            type: "POST",
            url: "DBManager",
            data: {action: "drop"},
            success: function (data) {
                alertify.success("Oralab DB is clear");
            },
            error: function (xhr, status, error) {
                alert(error);
            }});
        } else {
            // user clicked "cancel"
            alertify.log("Operation aborted");
        }
        
    });
});

$(document).on("click", "#create", function () {
    alertify.alert("Creating the tables...");
    $.ajax({
        type: "POST",
        url: "DBManager",
        data: {action: "create"},
        success: function (data) {
            alertify.success("Oralab DB have now sentiments's tables");
        },
        error: function (xhr, status, error) {
            alert(error);
        }
    });
});

$(document).on("click", "#load", function () {
    alertify.alert("Loading rex_res process start!");
    $.ajax({
        type: "POST",
        url: "Loader",
        success: function (data) {
            alertify.success("Operation complete");
        },
        error: function (xhr, status, error) {
            alert(error);
        }
    });
});
