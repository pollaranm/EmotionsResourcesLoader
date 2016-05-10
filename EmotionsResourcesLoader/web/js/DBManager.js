$(document).on("click", "#drop", function () {
    alert("Drop the DB");
    $.ajax({
        type: "POST",
        url: "DBManager",
        data: {action: "drop"},
        success: function (data) {
            alert("Oralab is clear");
        },
        error: function (xhr, status, error) {
            alert(error);
        }
    });
});

$(document).on("click", "#create", function () {
    alert("Create the tables");
    $.ajax({
        type: "POST",
        url: "DBManager",
        data: {action: "create"},
        success: function (data) {
            alert("Oralab is structured");
        },
        error: function (xhr, status, error) {
            alert(error);
        }
    });
});

$(document).on("click", "#load", function () {
    alert("ok!");
});
