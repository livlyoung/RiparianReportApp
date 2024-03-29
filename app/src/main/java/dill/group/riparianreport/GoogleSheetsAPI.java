package dill.group.riparianreport;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import android.util.Log;


import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.Value;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;



public class GoogleSheetsAPI {
    private static final String APPLICATION_NAME = "Google Sheets API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static NetHttpTransport httpTransport;

    static {
         httpTransport = new NetHttpTransport();

    }

    /**
     * Returns a Sheets service object initialized with the given Google credentials.
     *
     * @param credentials the Google credentials to use for authentication
     * @return a Sheets service object
     * @throws IOException if an error occurs while initializing the Sheets service
     */
    public static Sheets getSheetsService(GoogleCredentials credentials) throws IOException {
        // Source: Chat GPT
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
        return new Sheets.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                .setApplicationName(APPLICATION_NAME).build();
    }

    /**
     * Appends a list of ReportModel objects to a specified Google Sheets spreadsheet.
     * The first row of the sheet should contain the questions as column headers.
     * Each ReportModel object contains answers to these questions that should be
     * written in the next available row.
     *
     * @param spreadsheetId the ID of the Google Sheets spreadsheet to write to
     * @param reports       the list of ReportModel objects to write
     * @param credentials   the GoogleCredentials object used to authenticate the Sheets API
     * @throws IOException if an error occurs while writing to the sheet
     * @throws GeneralSecurityException If there is a security-related error.
     */
    public static void appendReports(String spreadsheetId, List<ReportModel> reports, GoogleCredentials credentials, String subSheet) throws IOException, GeneralSecurityException {
        Sheets sheetsService = getSheetsService(credentials);
        if (sheetsService == null) {
            Log.d("SheetsService", "SheetsService is null!");
            return;
        }

        //Source: Chat GPT
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        boolean sheetExists = false;
        for (Sheet sheet : spreadsheet.getSheets()) {
            if (sheet.getProperties().getTitle().equals(subSheet)) {
                sheetExists = true;
                break;
            }
        }
        if (!sheetExists) {
            // If the subsheet does not exist, add it to the spreadsheet
            addSheetToSpreadsheet(spreadsheetId, subSheet, credentials);
            appendReports(spreadsheetId, reports, credentials, subSheet);
            return;
        }

        // Check if the first row already has questions
        String range = subSheet;
        ValueRange firstRow = sheetsService.spreadsheets().values().get(spreadsheetId, range + "!1:1").execute();
        List<List<Object>> values = firstRow.getValues();
        boolean hasQuestions = values != null && values.size() > 0;

        // Create a new value range to hold the reports to append
        List<List<Object>> data = new ArrayList<>();
        if (!hasQuestions) {
            // If the first row doesn't have questions yet, add them
            List<Object> firstRowData = new ArrayList<>();
            for (ReportModel report : reports) {
                firstRowData.add(report.getQuestion());
            }
            data.add(firstRowData);
        }

        // Add the report data to the value range
        List<Object> rowData = new ArrayList<>();

        for (ReportModel report : reports) {
            rowData.add(report.getAnswer());
        }
        data.add(rowData);

        // Set the range for the report data
        //Source: Chat GPT
        range += "!A" + (hasQuestions ? 2 : 1) + ":XFD" + (hasQuestions ? values.get(0).size() + 1 : reports.size() + 1); //source : Chat GPT
        Log.d("the range", range.toString());

        // Create the value range object and append the data to the sheet
        ValueRange body = new ValueRange().setValues(data);
        AppendValuesResponse result = sheetsService.spreadsheets().values().append(spreadsheetId, range, body)
                .setValueInputOption("RAW").execute();
        Log.d("%d cells appended.", result.getUpdates().getUpdatedCells().toString());
    }


    /**
     * Adds a new sheet to the specified spreadsheet.
     *
     * @param spreadsheetId The ID of the spreadsheet to add the sheet to.
     * @param sheetTitle The title of the new sheet to be added.
     * @param credentials The Google credentials to use for authentication.
     * @throws IOException If an error occurs while communicating with the Sheets API.
     * @throws GeneralSecurityException If there is a security-related error.
     */
    public static void addSheetToSpreadsheet(String spreadsheetId, String sheetTitle, GoogleCredentials credentials) throws IOException, GeneralSecurityException {
        Sheets sheetsService = new Sheets.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Create the new sheet with the specified title
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                //Source: Chat GPT
                SheetProperties properties = new SheetProperties();
                properties.setTitle(sheetTitle);
                AddSheetRequest addSheetRequest = new AddSheetRequest();
                addSheetRequest.setProperties(properties);
                List<Request> requests = new ArrayList<>();
                requests.add(new Request().setAddSheet(addSheetRequest));
                BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest().setRequests(requests);
                try {
                    sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                Log.d("Spreadsheet added", "Success!");

            }
        });

    }

    /**
     * Checks if a subsheet with the given name exists in the given spreadsheet.
     *
     * @param spreadsheetId The ID of the spreadsheet to check for the subsheet.
     * @param subsheetName The name of the subsheet to check for in the spreadsheet.
     * @return True if the subsheet exists in the spreadsheet, false otherwise.
     * @throws IOException If an error occurs while communicating with the Google Sheets API.
     */
    public static boolean checkIfSubsheetExists(String spreadsheetId, String subsheetName, GoogleCredentials credentials) throws IOException {
        // Get the Sheets service instance
        Sheets service = getSheetsService(credentials);
        // Get the spreadsheet object
        Spreadsheet spreadsheet = service.spreadsheets().get(spreadsheetId).execute();
        // Get the list of sheets in the spreadsheet
        List<Sheet> sheets = spreadsheet.getSheets();
        // Loop through each sheet and check if the sheet's title matches the given sub-sheet name
        for (Sheet sheet : sheets) {
            if (sheet.getProperties().getTitle().equals(subsheetName)) {
                return true; // Sub-sheet exists, return true
            }
        }
        // Sub-sheet does not exist, return false
        return false;
    }


    /**
     * writeData was just for testing purposes
     **/
    public static void writeData(String spreadsheetId, String value, GoogleCredentials credentials) throws IOException {
        Sheets sheetsService = getSheetsService(credentials);
        if (sheetsService == null) {
            Log.d("SheetsService", "SheetsService is null!");
            return;
        }
        List<List<Object>> values = Arrays.asList(Arrays.asList(value));
        String range = "Sheet1!A1:XFD1048576";
        ValueRange body = new ValueRange().setValues(values);
        UpdateValuesResponse result = sheetsService.spreadsheets().values().update(spreadsheetId, range, body)
                .setValueInputOption("RAW").execute();
        Log.d("%d cells updated.", result.getUpdatedCells().toString());
    }
}
