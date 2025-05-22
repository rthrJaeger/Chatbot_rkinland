import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import java.util.Set;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.DocumentException;

/**
 * JavaFX GUI Chatbot for travel expense form
 * Improved version with language selection, re-asking questions on invalid input, and yes/no validation
 */
public class TravelExpenseChatbotGUI extends Application {
    
    // UI components
    private ScrollPane chatScrollPane;
    private VBox chatBox;
    private TextField inputField;
    private Button sendButton;
    
    // Chatbot state and data
    private ChatbotState currentState = ChatbotState.LANGUAGE_SELECTION;
    private Map<String, String> formData = new HashMap<>();
    private Map<String, String> fieldMappings;
    
    // Additional state variables
    private boolean kfzWegstreckenartKlein = true;
    private int hotelAnzahl = 1;
    private boolean[] verkehrsmittelSelected = new boolean[10];
    private int currentVerkehrsmittelIndex = -1;
    
    // Language settings
    private Locale currentLocale = Locale.GERMAN; // Default locale
    private ResourceBundle messages;
    
    @Override
    public void start(Stage primaryStage) {
         Platform.setImplicitExit(false);
        showLanguageSelection(primaryStage);
    }
    
    /**
     * Shows language selection screen
     */
    private void showLanguageSelection(Stage primaryStage) {
        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        
        Label titleLabel = new Label("Sprachauswahl / Language Selection");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        
        Button germanButton = new Button("Deutsch");
        germanButton.setPrefWidth(200);
        germanButton.setPrefHeight(50);
        germanButton.setOnAction(e -> {
            currentLocale = Locale.GERMAN;
            initializeApp(primaryStage);
        });
        
        Button englishButton = new Button("English");
        englishButton.setPrefWidth(200);
        englishButton.setPrefHeight(50);
        englishButton.setOnAction(e -> {
            currentLocale = Locale.ENGLISH;
            initializeApp(primaryStage);
        });
        
        root.getChildren().addAll(titleLabel, germanButton, englishButton);
        
        Scene scene = new Scene(root, 400, 250);
        primaryStage.setTitle("Travel Expense Chatbot");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    /**
     * Initialize the main application after language selection
     */
    private void initializeApp(Stage primaryStage) {
        loadLanguageResources();
        
        // Initialize mappings
        fieldMappings = loadFieldMappings();
        
        // Create UI layout
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        
        // Chat display area
        chatBox = new VBox(10);
        chatBox.setPadding(new Insets(10));
        
        chatScrollPane = new ScrollPane(chatBox);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setFitToHeight(true);
        chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chatScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        // Input area
        HBox inputBox = new HBox(10);
        inputBox.setPadding(new Insets(10, 0, 0, 0));
        inputBox.setAlignment(Pos.CENTER);
        
        inputField = new TextField();
        inputField.setPrefWidth(500);
        inputField.setPromptText(getMessage("input.prompt"));
        
        // Add Enter key handler
        inputField.setOnAction(e -> handleUserInput());
        
        sendButton = new Button(getMessage("button.send"));
        sendButton.setOnAction(e -> handleUserInput());
        
        inputBox.getChildren().addAll(inputField, sendButton);
        
        // Add components to root layout
        root.setCenter(chatScrollPane);
        root.setBottom(inputBox);
        
        // Set scene and stage
        Scene scene = new Scene(root, 600, 600);
        primaryStage.setTitle(getMessage("app.title"));
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Start conversation
        startConversation();
    }
    
    /**
     * Load language resources based on selected locale
     */
    private void loadLanguageResources() {
        try {
            // Get the current class loader for resource loading
            ClassLoader loader = TravelExpenseChatbotGUI.class.getClassLoader();
            messages = ResourceBundle.getBundle("TravelExpenseChatbot", currentLocale, loader);
        } catch (MissingResourceException e) {
            System.err.println("Warning: Could not load resource bundle for locale " + currentLocale);
            messages = new ListResourceBundle()    {
                @Override
                protected Object[][] getContents() {
                    return new Object[][] {};
                }
            };
        }
    }
    
    /**
     * Get a localized message from the resource bundle
     */
    private String getMessage(String key) {
        try {
            return messages.getString(key);
        } catch (MissingResourceException e) {
            return "[" + key + "]";
        }
    }
    
    /**
     * Get a formatted message with parameters
     */
    private String getFormattedMessage(String key, Object... args) {
        String pattern = getMessage(key);
        return String.format(pattern, args);
    }
    
    /**
     * Handle user input
     */
    private void handleUserInput() {
        String userInput = inputField.getText().trim();
        if (userInput.isEmpty()) {
            return;
        }
        
        addUserMessage(userInput);
        inputField.clear();
        processUserInput(userInput);
    }
    
    /**
     * Helper method to handle yes/no questions with validation
     * This method returns true if the answer is valid (ja/yes/j/y/1) and false for valid no answers (nein/no/n/0)
     * If the answer is invalid, it asks the question again and returns null to signal that processing should stop
     * 
     * @param userInput The user's input
     * @param questionKey The resource key for the question to repeat if invalid
     * @return Boolean result if valid (true=yes, false=no), null if invalid
     */
    private Boolean validateYesNoAnswer(String userInput, String questionKey) {
        String lowered = userInput.toLowerCase().trim();
        
        // Check for valid "yes" answers
        if (lowered.equals("ja") || 
            lowered.equals("j") || 
            lowered.equals("yes") || 
            lowered.equals("y") || 
            lowered.equals("1")) {
            return true;
        }
        
        // Check for valid "no" answers
        if (lowered.equals("nein") || 
            lowered.equals("n") || 
            lowered.equals("no") || 
            lowered.equals("0")) {
            return false;
        }
        
        addBotMessage(getMessage("error.invalidYesNo"));
        addBotMessage(getMessage(questionKey));
        return null;
    }
    
    /**
     * Process user input based on current chatbot state
     */
    private void processUserInput(String userInput) {
        switch (currentState) {
            case LANGUAGE_SELECTION:
                // This should not happen anymore
                break;
                
            case WELCOME:
                formData.put("behörde", userInput);
                currentState = ChatbotState.PERSONAL_NAME;
                addBotMessage(getMessage("personal.lastName"));
                break;
                
            case PERSONAL_NAME:
                formData.put("nachname", userInput);
                currentState = ChatbotState.PERSONAL_VORNAME;
                addBotMessage(getMessage("personal.firstName"));
                break;
                
            case PERSONAL_VORNAME:
                formData.put("vorname", userInput);
                formData.put("name", formData.get("nachname") + ", " + userInput);
                currentState = ChatbotState.PERSONAL_STATUS;
                addBotMessage(getMessage("personal.status"));
                addBotMessage("1: " + getMessage("personal.status.civil"));
                addBotMessage("2: " + getMessage("personal.status.tariff"));
                addBotMessage("3: " + getMessage("personal.status.trainee"));
                addBotMessage("4: " + getMessage("personal.status.apprentice"));
                break;
                
            case PERSONAL_STATUS:
                if (!processStatusSelection(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("personal.status"));
                    addBotMessage("1: " + getMessage("personal.status.civil"));
                    addBotMessage("2: " + getMessage("personal.status.tariff"));
                    addBotMessage("3: " + getMessage("personal.status.trainee"));
                    addBotMessage("4: " + getMessage("personal.status.apprentice"));
                    return;
                }
                currentState = ChatbotState.PERSONAL_AKTENZEICHEN;
                addBotMessage(getMessage("personal.fileNumber"));
                break;
                
            case PERSONAL_AKTENZEICHEN:
                formData.put("aktenzeichen", userInput);
                currentState = ChatbotState.PERSONAL_REFERAT;
                addBotMessage(getMessage("personal.department"));
                break;
                
            case PERSONAL_REFERAT:
                formData.put("referat", userInput);
                currentState = ChatbotState.PERSONAL_KOSTENSTELLE;
                addBotMessage(getMessage("personal.costCenter"));
                break;
                
            case PERSONAL_KOSTENSTELLE:
                formData.put("kostenstelle", userInput);
                currentState = ChatbotState.PERSONAL_KOSTENTRAEGER;
                addBotMessage(getMessage("personal.costBearer"));
                break;
                
            case PERSONAL_KOSTENTRAEGER:
                formData.put("kostenträger", userInput);
                currentState = ChatbotState.PERSONAL_TELEFON;
                addBotMessage(getMessage("personal.phone"));
                break;
                
            case PERSONAL_TELEFON:
                if (validatePhoneNumber(userInput)) {
                    formData.put("telefon", userInput);
                    currentState = ChatbotState.PERSONAL_EMAIL;
                    addBotMessage(getMessage("personal.email"));
                } else {
                    addBotMessage(getMessage("error.invalidPhone"));
                }
                break;
                
            case PERSONAL_EMAIL:
                if (validateEmail(userInput)) {
                    formData.put("email", userInput);
                    currentState = ChatbotState.PERSONAL_ABORDNUNG;
                    addBotMessage(getMessage("personal.secondment"));
                } else {
                    addBotMessage(getMessage("error.invalidEmail"));
                }
                break;
                
            case PERSONAL_ABORDNUNG:
                Boolean isAbgeordnet = validateYesNoAnswer(userInput, "personal.secondment");
                if (isAbgeordnet == null) {
                    return;
                }
                
                if (isAbgeordnet) {
                    formData.put("abordnung", "Yes");
                    currentState = ChatbotState.PERSONAL_STAMMBEHOERDE;
                    addBotMessage(getMessage("personal.originalAuthority"));
                } else {
                    currentState = ChatbotState.PERSONAL_ERSTANTRAG;
                    addBotMessage(getMessage("personal.firstApplication"));
                }
                break;
                
            case PERSONAL_STAMMBEHOERDE:
                formData.put("stammBehörde", userInput);
                currentState = ChatbotState.PERSONAL_ERSTANTRAG;
                addBotMessage(getMessage("personal.firstApplication"));
                break;
                
            case PERSONAL_ERSTANTRAG:
                Boolean isErstantrag = validateYesNoAnswer(userInput, "personal.firstApplication");
                if (isErstantrag == null) {
                    return;
                }
                
                if (isErstantrag) {
                    currentState = ChatbotState.PERSONAL_ANSCHRIFT;
                    addBotMessage(getMessage("personal.address"));
                } else {
                    currentState = ChatbotState.REISE_ZWECK;
                    addBotMessage(getMessage("travel.purpose"));
                }
                break;
                
            case PERSONAL_ANSCHRIFT:
                formData.put("anschrift", userInput);
                currentState = ChatbotState.PERSONAL_FAMILIENWOHNORT;
                addBotMessage(getMessage("personal.familyAddress"));
                break;
                
            case PERSONAL_FAMILIENWOHNORT:
                if (!userInput.isEmpty()) {
                    formData.put("familienwohnort", userInput);
                }
                currentState = ChatbotState.PERSONAL_PERSONALNUMMER;
                addBotMessage(getMessage("personal.personalNumber"));
                break;
                
            case PERSONAL_PERSONALNUMMER:
                if (userInput.matches("\\d{11}")) {
                    formData.put("personalNr", userInput);
                    currentState = ChatbotState.PERSONAL_GELDINSTITUT;
                    addBotMessage(getMessage("personal.bank"));
                } else {
                    addBotMessage(getMessage("error.invalidPersonalNumber"));
                }
                break;
                
            case PERSONAL_GELDINSTITUT:
                formData.put("geldinstitut", userInput);
                currentState = ChatbotState.PERSONAL_IBAN;
                addBotMessage(getMessage("personal.iban"));
                break;
                
            case PERSONAL_IBAN:
                if (validateIBAN(userInput)) {
                    formData.put("iban", userInput.replace(" ", "").toUpperCase());
                    currentState = ChatbotState.PERSONAL_BIC;
                    addBotMessage(getMessage("personal.bic"));
                } else {
                    addBotMessage(getMessage("error.invalidIBAN"));
                }
                break;
                
            case PERSONAL_BIC:
                if (validateBIC(userInput)) {
                    formData.put("bic", userInput.trim().toUpperCase());
                    currentState = ChatbotState.REISE_ZWECK;
                    addBotMessage(getMessage("travel.purpose"));
                } else {
                    addBotMessage(getMessage("error.invalidBIC"));
                }
                break;
                
            case REISE_ZWECK:
                formData.put("zweck", userInput);
                currentState = ChatbotState.REISE_GESCHAEFTSORT;
                addBotMessage(getMessage("travel.destination"));
                break;
                
            case REISE_GESCHAEFTSORT:
                formData.put("geschäftsort", userInput);
                currentState = ChatbotState.REISE_BEGINN_DATUM;
                addBotMessage(getMessage("travel.startDate"));
                break;
                
            case REISE_BEGINN_DATUM:
                if (validateDate(userInput)) {
                    formData.put("beginnReiseDatum", userInput);
                    currentState = ChatbotState.REISE_BEGINN_ZEIT;
                    addBotMessage(getMessage("travel.startTime"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case REISE_BEGINN_ZEIT:
                if (validateTime(userInput)) {
                    formData.put("beginnReiseZeit", userInput);
                    currentState = ChatbotState.REISE_BEGINN_ORT;
                    addBotMessage(getMessage("travel.startLocation"));
                    addBotMessage("1: " + getMessage("travel.startLocation.home"));
                    addBotMessage("2: " + getMessage("travel.startLocation.office"));
                    addBotMessage("3: " + getMessage("travel.startLocation.temporary"));
                } else {
                    addBotMessage(getMessage("error.invalidTime"));
                }
                break;
                
            case REISE_BEGINN_ORT:
                if (!processReiseBeginnOrt(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("travel.startLocation"));
                    addBotMessage("1: " + getMessage("travel.startLocation.home"));
                    addBotMessage("2: " + getMessage("travel.startLocation.office"));
                    addBotMessage("3: " + getMessage("travel.startLocation.temporary"));
                    return;
                }
                currentState = ChatbotState.REISE_ANKUNFT_DATUM;
                addBotMessage(getMessage("travel.arrivalDate"));
                break;
                
            case REISE_ANKUNFT_DATUM:
                if (validateDate(userInput)) {
                    formData.put("ankunftDatum", userInput);
                    currentState = ChatbotState.REISE_ANKUNFT_ZEIT;
                    addBotMessage(getMessage("travel.arrivalTime"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case REISE_ANKUNFT_ZEIT:
                if (validateTime(userInput)) {
                    formData.put("ankunftUhrzeit", userInput);
                    currentState = ChatbotState.REISE_BEGINN_DIENST_DATUM;
                    addBotMessage(getMessage("travel.businessStartDate"));
                } else {
                    addBotMessage(getMessage("error.invalidTime"));
                }
                break;
                
            case REISE_BEGINN_DIENST_DATUM:
                if (validateDate(userInput)) {
                    formData.put("beginnDienstDatum", userInput);
                    currentState = ChatbotState.REISE_BEGINN_DIENST_ZEIT;
                    addBotMessage(getMessage("travel.businessStartTime"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case REISE_BEGINN_DIENST_ZEIT:
                if (validateTime(userInput)) {
                    formData.put("beginnDienstUhrzeit", userInput);
                    currentState = ChatbotState.REISE_ENDE_DIENST_DATUM;
                    addBotMessage(getMessage("travel.businessEndDate"));
                } else {
                    addBotMessage(getMessage("error.invalidTime"));
                }
                break;
                
            case REISE_ENDE_DIENST_DATUM:
                if (validateDate(userInput)) {
                    formData.put("endeDienstDatum", userInput);
                    currentState = ChatbotState.REISE_ENDE_DIENST_ZEIT;
                    addBotMessage(getMessage("travel.businessEndTime"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case REISE_ENDE_DIENST_ZEIT:
                if (validateTime(userInput)) {
                    formData.put("endeDienstUhrzeit", userInput);
                    currentState = ChatbotState.REISE_ABFAHRT_DATUM;
                    addBotMessage(getMessage("travel.departureDate"));
                } else {
                    addBotMessage(getMessage("error.invalidTime"));
                }
                break;
                
            case REISE_ABFAHRT_DATUM:
                if (validateDate(userInput)) {
                    formData.put("abfahrtDatum", userInput);
                    currentState = ChatbotState.REISE_ABFAHRT_ZEIT;
                    addBotMessage(getMessage("travel.departureTime"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case REISE_ABFAHRT_ZEIT:
                if (validateTime(userInput)) {
                    formData.put("abfahrtUhrzeit", userInput);
                    currentState = ChatbotState.REISE_ENDE_DATUM;
                    addBotMessage(getMessage("travel.endDate"));
                } else {
                    addBotMessage(getMessage("error.invalidTime"));
                }
                break;
                
            case REISE_ENDE_DATUM:
                if (validateDate(userInput)) {
                    formData.put("endeReiseDatum", userInput);
                    currentState = ChatbotState.REISE_ENDE_ZEIT;
                    addBotMessage(getMessage("travel.endTime"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case REISE_ENDE_ZEIT:
                if (validateTime(userInput)) {
                    formData.put("endeReiseZeit", userInput);
                    currentState = ChatbotState.REISE_ENDE_ORT;
                    addBotMessage(getMessage("travel.endLocation"));
                    addBotMessage("1: " + getMessage("travel.endLocation.home"));
                    addBotMessage("2: " + getMessage("travel.endLocation.office"));
                    addBotMessage("3: " + getMessage("travel.endLocation.temporary"));
                } else {
                    addBotMessage(getMessage("error.invalidTime"));
                }
                break;
                
            case REISE_ENDE_ORT:
                if (!processReiseEndeOrt(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("travel.endLocation"));
                    addBotMessage("1: " + getMessage("travel.endLocation.home"));
                    addBotMessage("2: " + getMessage("travel.endLocation.office"));
                    addBotMessage("3: " + getMessage("travel.endLocation.temporary"));
                    return;
                }
                currentState = ChatbotState.REISE_PRIVATREISE;
                addBotMessage(getMessage("travel.privateTrip"));
                break;
                
            case REISE_PRIVATREISE:
                Boolean privatreise = validateYesNoAnswer(userInput, "travel.privateTrip");
                if (privatreise == null) {
                    return;
                }
                
                if (privatreise) {
                    formData.put("privatreise", "Yes");
                    currentState = ChatbotState.REISE_PRIVATREISE_ERLAEUTERUNG;
                    addBotMessage(getMessage("travel.privateTripDetails"));
                } else {
                    currentState = ChatbotState.REISE_TELEARBEIT;
                    addBotMessage(getMessage("travel.telecommuting"));
                }
                break;
                
            case REISE_PRIVATREISE_ERLAEUTERUNG:
                formData.put("privatreiseErläuterung", userInput);
                currentState = ChatbotState.REISE_TELEARBEIT;
                addBotMessage(getMessage("travel.telecommuting"));
                break;
                
            case REISE_TELEARBEIT:
                Boolean telearbeit = validateYesNoAnswer(userInput, "travel.telecommuting");
                if (telearbeit == null) {
                    return;
                }
                
                if (telearbeit) {
                    formData.put("telearbeit", "Yes");
                    currentState = ChatbotState.REISE_TELEARBEIT_ERLAEUTERUNG;
                    addBotMessage(getMessage("travel.telecommutingDetails"));
                } else {
                    currentState = ChatbotState.REISE_ABRECHNUNGSSTELLE;
                    addBotMessage(getMessage("travel.accountingOffice"));
                    addBotMessage("1: " + getMessage("travel.accountingOffice.berlin"));
                    addBotMessage("2: " + getMessage("travel.accountingOffice.hamm"));
                    addBotMessage("3: " + getMessage("travel.accountingOffice.osnabrueck"));
                }
                break;
                
            case REISE_TELEARBEIT_ERLAEUTERUNG:
                formData.put("telearbeitErläuterung", userInput);
                currentState = ChatbotState.REISE_ABRECHNUNGSSTELLE;
                addBotMessage(getMessage("travel.accountingOffice"));
                addBotMessage("1: " + getMessage("travel.accountingOffice.berlin"));
                addBotMessage("2: " + getMessage("travel.accountingOffice.hamm"));
                addBotMessage("3: " + getMessage("travel.accountingOffice.osnabrueck"));
                break;
                
            case REISE_ABRECHNUNGSSTELLE:
                if (!processAbrechnungsstelle(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("travel.accountingOffice"));
                    addBotMessage("1: " + getMessage("travel.accountingOffice.berlin"));
                    addBotMessage("2: " + getMessage("travel.accountingOffice.hamm"));
                    addBotMessage("3: " + getMessage("travel.accountingOffice.osnabrueck"));
                    return;
                }
                currentState = ChatbotState.VERKEHRSMITTEL_AUSWAHL;
                addBotMessage(getMessage("transport.select"));
                addBotMessage("1: " + getMessage("transport.official"));
                addBotMessage("2: " + getMessage("transport.private"));
                addBotMessage("3: " + getMessage("transport.passenger"));
                addBotMessage("4: " + getMessage("transport.rental"));
                addBotMessage("5: " + getMessage("transport.train"));
                addBotMessage("6: " + getMessage("transport.flight"));
                addBotMessage("7: " + getMessage("transport.public"));
                addBotMessage("8: " + getMessage("transport.taxi"));
                addBotMessage("9: " + getMessage("transport.bicycle"));
                addBotMessage("10: " + getMessage("transport.other"));
                break;
                
            case VERKEHRSMITTEL_AUSWAHL:
                processVerkehrsmittelAuswahl(userInput);
                if (processNextVerkehrsmittel()) {
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG;
                    addBotMessage(getMessage("accommodation"));
                }
                break;
                
            case VERKEHR_PRIVATKFZ_WEGSTRECKENART:
                if (!processPrivatKfzWegstreckenart(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("transport.private.compensation"));
                    addBotMessage("1: " + getMessage("transport.private.compensation.small"));
                    addBotMessage("2: " + getMessage("transport.private.compensation.large"));
                    return;
                }
                currentState = ChatbotState.VERKEHR_PRIVATKFZ_KILOMETER;
                addBotMessage(getMessage("transport.private.kilometers"));
                break;
                
            case VERKEHR_PRIVATKFZ_KILOMETER:
                if (kfzWegstreckenartKlein) {
                    formData.put("KfzKleineWEAnzahlKm", userInput);
                } else {
                    formData.put("KfzGrosseWEAnzahlKm", userInput);
                }
                currentState = ChatbotState.VERKEHR_PRIVATKFZ_STRECKE;
                addBotMessage(getMessage("transport.private.route"));
                break;
                
            case VERKEHR_PRIVATKFZ_STRECKE:
                if (kfzWegstreckenartKlein) {
                    formData.put("KfzKleineWEOrt", userInput);
                } else {
                    formData.put("KfzGrosseWEOrt", userInput);
                }
                if (processNextVerkehrsmittel()) {
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG;
                    addBotMessage(getMessage("accommodation"));
                }
                break;
                
            case VERKEHR_MITFAHRER_NAME:
                formData.put("mitfahrerName", userInput);
                if (processNextVerkehrsmittel()) {
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG;
                    addBotMessage(getMessage("accommodation"));
                }
                break;
                
            case VERKEHR_MIETWAGEN_BUCHUNG:
                if (!processMietwagenBuchung(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("transport.rental.bookedBy"));
                    addBotMessage("1: " + getMessage("transport.rental.bookedBy.travelPrep"));
                    addBotMessage("2: " + getMessage("transport.rental.bookedBy.self"));
                    return;
                }
                currentState = ChatbotState.VERKEHR_MIETWAGEN_KOSTEN;
                addBotMessage(getMessage("transport.rental.costs"));
                break;
                
            case VERKEHR_MIETWAGEN_KOSTEN:
                formData.put("Mietkosten", userInput);
                currentState = ChatbotState.VERKEHR_MIETWAGEN_BENZIN;
                addBotMessage(getMessage("transport.rental.fuelCosts"));
                break;
                
            case VERKEHR_MIETWAGEN_BENZIN:
                formData.put("Benzinkosten", userInput);
                if (formData.containsKey("mietwagenSelbst") && formData.get("mietwagenSelbst").equals("Yes")) {
                    currentState = ChatbotState.VERKEHR_MIETWAGEN_BEGRUENDUNG;
                    addBotMessage(getMessage("transport.rental.reason"));
                } else {
                    if (processNextVerkehrsmittel()) {
                    } else {
                        currentState = ChatbotState.UEBERNACHTUNG;
                        addBotMessage(getMessage("accommodation"));
                    }
                }
                break;
                
            case VERKEHR_MIETWAGEN_BEGRUENDUNG:
                formData.put("MietwagenBegründung", userInput);
                if (processNextVerkehrsmittel()) {
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG;
                    addBotMessage(getMessage("accommodation"));
                }
                break;
                
            case VERKEHR_BAHN_BUCHUNG:
                if (!processBahnBuchung(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("transport.train.bookedBy"));
                    addBotMessage("1: " + getMessage("transport.train.bookedBy.travelPrep"));
                    addBotMessage("2: " + getMessage("transport.train.bookedBy.self"));
                    return;
                }
                currentState = ChatbotState.VERKEHR_BAHN_BAHNCARD;
                addBotMessage(getMessage("transport.train.bahncard"));
                break;
                
            case VERKEHR_BAHN_BAHNCARD:
                Boolean bahncard = validateYesNoAnswer(userInput, "transport.train.bahncard");
                if (bahncard == null) {
                    return;
                }
                
                if (bahncard) {
                    formData.put("bahncardVorhanden", "Yes");
                    currentState = ChatbotState.VERKEHR_BAHN_BAHNCARD_TYP;
                    addBotMessage(getMessage("transport.train.bahncard.type"));
                    addBotMessage("1: " + getMessage("transport.train.bahncard.type.private"));
                    addBotMessage("2: " + getMessage("transport.train.bahncard.type.business"));
                } else {
                    currentState = ChatbotState.VERKEHR_BAHN_BONUS;
                    addBotMessage(getMessage("transport.train.bonus"));
                }
                break;
                
            case VERKEHR_BAHN_BAHNCARD_TYP:
                if (!processBahncardTyp(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("transport.train.bahncard.type"));
                    addBotMessage("1: " + getMessage("transport.train.bahncard.type.private"));
                    addBotMessage("2: " + getMessage("transport.train.bahncard.type.business"));
                    return;
                }
                currentState = ChatbotState.VERKEHR_BAHN_BAHNCARD_WERT;
                addBotMessage(getMessage("transport.train.bahncard.value"));
                addBotMessage("1: " + getMessage("transport.train.bahncard.value.25"));
                addBotMessage("2: " + getMessage("transport.train.bahncard.value.50"));
                addBotMessage("3: " + getMessage("transport.train.bahncard.value.100"));
                break;
                
            case VERKEHR_BAHN_BAHNCARD_WERT:
                if (!processBahncardWert(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("transport.train.bahncard.value"));
                    addBotMessage("1: " + getMessage("transport.train.bahncard.value.25"));
                    addBotMessage("2: " + getMessage("transport.train.bahncard.value.50"));
                    addBotMessage("3: " + getMessage("transport.train.bahncard.value.100"));
                    return;
                }
                currentState = ChatbotState.VERKEHR_BAHN_BAHNCARD_KLASSE;
                addBotMessage(getMessage("transport.train.bahncard.class"));
                addBotMessage("1: " + getMessage("transport.train.bahncard.class.first"));
                addBotMessage("2: " + getMessage("transport.train.bahncard.class.second"));
                break;
                
            case VERKEHR_BAHN_BAHNCARD_KLASSE:
                if (!processBahncardKlasse(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("transport.train.bahncard.class"));
                    addBotMessage("1: " + getMessage("transport.train.bahncard.class.first"));
                    addBotMessage("2: " + getMessage("transport.train.bahncard.class.second"));
                    return;
                }
                currentState = ChatbotState.VERKEHR_BAHN_BONUS;
                addBotMessage(getMessage("transport.train.bonus"));
                break;
                
            case VERKEHR_BAHN_BONUS:
                Boolean bahnBonus = validateYesNoAnswer(userInput, "transport.train.bonus");
                if (bahnBonus == null) {
                    return;
                }
                
                if (bahnBonus) {
                    formData.put("bahnBonus", "Yes");
                    currentState = ChatbotState.VERKEHR_BAHN_BONUS_NAME;
                    addBotMessage(getMessage("transport.train.bonus.name"));
                } else {
                    currentState = ChatbotState.VERKEHR_BAHN_HINFAHRT;
                    addBotMessage(getMessage("transport.train.outward"));
                }
                break;
                
            case VERKEHR_BAHN_BONUS_NAME:
                formData.put("bahnBonusName", userInput);
                currentState = ChatbotState.VERKEHR_BAHN_HINFAHRT;
                addBotMessage(getMessage("transport.train.outward"));
                break;
                
            case VERKEHR_BAHN_HINFAHRT:
                formData.put("BahnHinfahrt", userInput);
                currentState = ChatbotState.VERKEHR_BAHN_RUECKFAHRT;
                addBotMessage(getMessage("transport.train.return"));
                break;
                
            case VERKEHR_BAHN_RUECKFAHRT:
                formData.put("BahnRückfahrt", userInput);
                currentState = ChatbotState.VERKEHR_BAHN_VORGABEN;
                addBotMessage(getMessage("transport.train.guidelines"));
                break;
                
            case VERKEHR_BAHN_VORGABEN:
                Boolean bahnVorgaben = validateYesNoAnswer(userInput, "transport.train.guidelines");
                if (bahnVorgaben == null) {
                    return;
                }
                
                if (bahnVorgaben) {
                    formData.put("BahnReisekostenVorgaben", "Yes");
                }
                
                if (processNextVerkehrsmittel()) {
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG;
                    addBotMessage(getMessage("accommodation"));
                }
                break;
                
            case VERKEHR_FLUG_BUCHUNG:
                if (!processFlugBuchung(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("transport.flight.bookedBy"));
                    addBotMessage("1: " + getMessage("transport.flight.bookedBy.travelPrep"));
                    addBotMessage("2: " + getMessage("transport.flight.bookedBy.self"));
                    return;
                }
                currentState = ChatbotState.VERKEHR_FLUG_KOSTEN;
                addBotMessage(getMessage("transport.flight.costs"));
                break;
                
            case VERKEHR_FLUG_KOSTEN:
                formData.put("FlugKosten", userInput);
                currentState = ChatbotState.VERKEHR_FLUG_BEGRUENDUNG;
                addBotMessage(getMessage("transport.flight.reason"));
                break;
                
            case VERKEHR_FLUG_BEGRUENDUNG:
                formData.put("FlugBegründung", userInput);
                currentState = ChatbotState.VERKEHR_FLUG_BONUS;
                addBotMessage(getMessage("transport.flight.bonus"));
                break;
                
            case VERKEHR_FLUG_BONUS:
                Boolean flugBonus = validateYesNoAnswer(userInput, "transport.flight.bonus");
                if (flugBonus == null) {
                    return;
                }
                
                if (flugBonus) {
                    formData.put("flugBonusProgramm", "Yes");
                    currentState = ChatbotState.VERKEHR_FLUG_BONUS_NAME;
                    addBotMessage(getMessage("transport.flight.bonus.name"));
                } else {
                    if (processNextVerkehrsmittel()) {
                    } else {
                        currentState = ChatbotState.UEBERNACHTUNG;
                        addBotMessage(getMessage("accommodation"));
                    }
                }
                break;
                
            case VERKEHR_FLUG_BONUS_NAME:
                formData.put("flugBonusName", userInput);
                if (processNextVerkehrsmittel()) {
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG;
                    addBotMessage(getMessage("accommodation"));
                }
                break;
                
            case VERKEHR_OEPNV_ANZAHL:
                formData.put("öpnvAnzahl", userInput);
                currentState = ChatbotState.VERKEHR_OEPNV_KOSTEN;
                addBotMessage(getMessage("transport.public.costs"));
                break;
                
            case VERKEHR_OEPNV_KOSTEN:
                formData.put("öpnvKosten", userInput);
                currentState = ChatbotState.VERKEHR_OEPNV_GRUND;
                addBotMessage(getMessage("transport.public.reason"));
                break;
                
            case VERKEHR_OEPNV_GRUND:
                formData.put("öpnvGrund", userInput);
                if (processNextVerkehrsmittel()) {
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG;
                    addBotMessage(getMessage("accommodation"));
                }
                break;
                
            case VERKEHR_TAXI_ANTRAG:
                Boolean taxiAntrag = validateYesNoAnswer(userInput, "transport.taxi.request");
                if (taxiAntrag == null) {
                    return;
                }
                
                if (taxiAntrag) {
                    formData.put("taxiAntrag", "Yes");
                    currentState = ChatbotState.VERKEHR_TAXI_ANZAHL;
                    addBotMessage(getMessage("transport.taxi.trips"));
                } else {
                    if (processNextVerkehrsmittel()) {
                    } else {
                        currentState = ChatbotState.UEBERNACHTUNG;
                        addBotMessage(getMessage("accommodation"));
                    }
                }
                break;
                
            case VERKEHR_TAXI_ANZAHL:
                formData.put("taxiAnzahl", userInput);
                currentState = ChatbotState.VERKEHR_TAXI_KOSTEN;
                addBotMessage(getMessage("transport.taxi.costs"));
                break;
                
            case VERKEHR_TAXI_KOSTEN:
                formData.put("taxiKosten", userInput);
                currentState = ChatbotState.VERKEHR_TAXI_GRUND;
                addBotMessage(getMessage("transport.taxi.reason"));
                break;
                
            case VERKEHR_TAXI_GRUND:
                formData.put("taxiGrund", userInput);
                if (processNextVerkehrsmittel()) {
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG;
                    addBotMessage(getMessage("accommodation"));
                }
                break;
                
            case VERKEHR_FAHRRAD_ANZAHL:
                formData.put("fahrradAnzahl", userInput);
                currentState = ChatbotState.VERKEHR_FAHRRAD_PAUSCHALE;
                addBotMessage(getMessage("transport.bicycle.request"));
                break;
                
            case VERKEHR_FAHRRAD_PAUSCHALE:
                Boolean fahrradPauschale = validateYesNoAnswer(userInput, "transport.bicycle.request");
                if (fahrradPauschale == null) {
                    return;
                }
                
                if (fahrradPauschale) {
                    formData.put("fahrradPauschale", "Yes");
                }
                
                if (processNextVerkehrsmittel()) {
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG;
                    addBotMessage(getMessage("accommodation"));
                }
                break;
                
            case VERKEHR_SONSTIGES_ART:
                formData.put("AndereVerkehrsmittelText", userInput);
                currentState = ChatbotState.VERKEHR_SONSTIGES_ANZAHL;
                addBotMessage(getMessage("transport.other.items"));
                break;
                
            case VERKEHR_SONSTIGES_ANZAHL:
                formData.put("SonstigeKostenAnzahl", userInput);
                currentState = ChatbotState.VERKEHR_SONSTIGES_KOSTEN;
                addBotMessage(getMessage("transport.other.costs"));
                break;
                
            case VERKEHR_SONSTIGES_KOSTEN:
                formData.put("SonstigeKostenKosten", userInput);
                currentState = ChatbotState.VERKEHR_SONSTIGES_GRUND;
                addBotMessage(getMessage("transport.other.reason"));
                break;
                
            case VERKEHR_SONSTIGES_GRUND:
                formData.put("SonstigeKostenGrund", userInput);
                if (processNextVerkehrsmittel()) {
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG;
                    addBotMessage(getMessage("accommodation"));
                }
                break;
                
            case UEBERNACHTUNG:
                Boolean hatUebernachtet = validateYesNoAnswer(userInput, "accommodation");
                if (hatUebernachtet == null) {
                    return;
                }
                
                if (hatUebernachtet) {
                    currentState = ChatbotState.UEBERNACHTUNG_UNENTGELTLICH;
                    addBotMessage(getMessage("accommodation.free"));
                } else {
                    currentState = ChatbotState.ZUSATZ_LEISTUNG_DRITTE;
                    addBotMessage(getMessage("additional.thirdParty"));
                }
                break;
                
            case UEBERNACHTUNG_UNENTGELTLICH:
                Boolean unentgeltlich = validateYesNoAnswer(userInput, "accommodation.free");
                if (unentgeltlich == null) {
                    return;
                }
                
                if (unentgeltlich) {
                    formData.put("unterkunftUnentgeltlichJa", "Yes");
                    currentState = ChatbotState.UEBERNACHTUNG_UNTERKUNFT;
                    addBotMessage(getMessage("accommodation.received"));
                } else {
                    formData.put("unterkunftUnentgeltlichNein", "Yes");
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL;
                    addBotMessage(getMessage("accommodation.hotel"));
                }
                break;
                
            case UEBERNACHTUNG_UNTERKUNFT:
                Boolean unterkunftErhalten = validateYesNoAnswer(userInput, "accommodation.received");
                if (unterkunftErhalten == null) {
                    return;
                }
                
                if (unterkunftErhalten) {
                    currentState = ChatbotState.UEBERNACHTUNG_UNTERKUNFT_VON;
                    addBotMessage(getMessage("accommodation.from"));
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG_FRUEHSTUECK;
                    addBotMessage(getMessage("accommodation.breakfast"));
                }
                break;
                
            case UEBERNACHTUNG_UNTERKUNFT_VON:
                if (validateDate(userInput)) {
                    formData.put("UnterkunftVon", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_UNTERKUNFT_BIS;
                    addBotMessage(getMessage("accommodation.to"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_UNTERKUNFT_BIS:
                if (validateDate(userInput)) {
                    formData.put("UnterkunftBis", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_FRUEHSTUECK;
                    addBotMessage(getMessage("accommodation.breakfast"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_FRUEHSTUECK:
                Boolean fruehstueckErhalten = validateYesNoAnswer(userInput, "accommodation.breakfast");
                if (fruehstueckErhalten == null) {
                    return;
                }
                
                if (fruehstueckErhalten) {
                    currentState = ChatbotState.UEBERNACHTUNG_FRUEHSTUECK_VON;
                    addBotMessage(getMessage("accommodation.breakfast.from"));
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG_MITTAGESSEN;
                    addBotMessage(getMessage("accommodation.lunch"));
                }
                break;
                
            case UEBERNACHTUNG_FRUEHSTUECK_VON:
                if (validateDate(userInput)) {
                    formData.put("FrühstückVon", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_FRUEHSTUECK_BIS;
                    addBotMessage(getMessage("accommodation.breakfast.to"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_FRUEHSTUECK_BIS:
                if (validateDate(userInput)) {
                    formData.put("FrühstückBis", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_MITTAGESSEN;
                    addBotMessage(getMessage("accommodation.lunch"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_MITTAGESSEN:
                Boolean mittagessenErhalten = validateYesNoAnswer(userInput, "accommodation.lunch");
                if (mittagessenErhalten == null) {
                    return;
                }
                
                if (mittagessenErhalten) {
                    currentState = ChatbotState.UEBERNACHTUNG_MITTAGESSEN_VON;
                    addBotMessage(getMessage("accommodation.lunch.from"));
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG_ABENDESSEN;
                    addBotMessage(getMessage("accommodation.dinner"));
                }
                break;
                
            case UEBERNACHTUNG_MITTAGESSEN_VON:
                if (validateDate(userInput)) {
                    formData.put("MittagessenVon", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_MITTAGESSEN_BIS;
                    addBotMessage(getMessage("accommodation.lunch.to"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_MITTAGESSEN_BIS:
                if (validateDate(userInput)) {
                    formData.put("MittagessenBis", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_ABENDESSEN;
                    addBotMessage(getMessage("accommodation.dinner"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_ABENDESSEN:
                Boolean abendessenErhalten = validateYesNoAnswer(userInput, "accommodation.dinner");
                if (abendessenErhalten == null) {
                    return;
                }
                
                if (abendessenErhalten) {
                    currentState = ChatbotState.UEBERNACHTUNG_ABENDESSEN_VON;
                    addBotMessage(getMessage("accommodation.dinner.from"));
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL;
                    addBotMessage(getMessage("accommodation.hotel"));
                }
                break;
                
            case UEBERNACHTUNG_ABENDESSEN_VON:
                if (validateDate(userInput)) {
                    formData.put("AbendessenVon", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_ABENDESSEN_BIS;
                    addBotMessage(getMessage("accommodation.dinner.to"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_ABENDESSEN_BIS:
                if (validateDate(userInput)) {
                    formData.put("AbendessenBis", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL;
                    addBotMessage(getMessage("accommodation.hotel"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_HOTEL:
                Boolean hatHotel = validateYesNoAnswer(userInput, "accommodation.hotel");
                if (hatHotel == null) {
                    return;
                }
                
                if (hatHotel) {
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL_ANZAHL;
                    addBotMessage(getMessage("accommodation.hotel.count"));
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG_WOHNUNG;
                    addBotMessage(getMessage("accommodation.residence.outside"));
                }
                break;
                
            case UEBERNACHTUNG_HOTEL_ANZAHL:
                try {
                    hotelAnzahl = Integer.parseInt(userInput.trim());
                    if (hotelAnzahl != 1 && hotelAnzahl != 2) {
                        throw new NumberFormatException("Only 1 or 2 allowed");
                    }
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL1_NAME;
                    addBotMessage(getMessage("accommodation.hotel1.name"));
                } catch (NumberFormatException e) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("accommodation.hotel.count"));
                }
                break;
                
            case UEBERNACHTUNG_HOTEL1_NAME:
                formData.put("HotelName1", userInput);
                currentState = ChatbotState.UEBERNACHTUNG_HOTEL1_ORT;
                addBotMessage(getMessage("accommodation.hotel1.location"));
                break;
                
            case UEBERNACHTUNG_HOTEL1_ORT:
                formData.put("ÜbernachtungOrt1", userInput);
                currentState = ChatbotState.UEBERNACHTUNG_HOTEL1_VON;
                addBotMessage(getMessage("accommodation.hotel1.from"));
                break;
                
            case UEBERNACHTUNG_HOTEL1_VON:
                if (validateDate(userInput)) {
                    formData.put("ÜbernachtungVon1", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL1_BIS;
                    addBotMessage(getMessage("accommodation.hotel1.to"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_HOTEL1_BIS:
                if (validateDate(userInput)) {
                    formData.put("ÜbernachtungBis1", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL1_KOSTEN;
                    addBotMessage(getMessage("accommodation.hotel1.costs"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_HOTEL1_KOSTEN:
                formData.put("HotelKosten1", userInput);
                currentState = ChatbotState.UEBERNACHTUNG_HOTEL1_FRUEHSTUECK;
                addBotMessage(getMessage("accommodation.hotel1.breakfast"));
                break;
                
            case UEBERNACHTUNG_HOTEL1_FRUEHSTUECK:
                Boolean hotel1Fruehstueck = validateYesNoAnswer(userInput, "accommodation.hotel1.breakfast");
                if (hotel1Fruehstueck == null) {
                    return;
                }
                
                if (hotel1Fruehstueck) {
                    formData.put("MitFrühstück1", "Yes");
                } else {
                    formData.put("OhneFrühstück1", "Yes");
                }
                
                if (hotelAnzahl == 2) {
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL2_NAME;
                    addBotMessage(getMessage("accommodation.hotel2.name"));
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL_RECHNUNG;
                    addBotMessage(getMessage("accommodation.hotel.billing"));
                }
                break;
                
            case UEBERNACHTUNG_HOTEL2_NAME:
                formData.put("HotelName2", userInput);
                currentState = ChatbotState.UEBERNACHTUNG_HOTEL2_ORT;
                addBotMessage(getMessage("accommodation.hotel2.location"));
                break;
                
            case UEBERNACHTUNG_HOTEL2_ORT:
                formData.put("ÜbernachtungOrt2", userInput);
                currentState = ChatbotState.UEBERNACHTUNG_HOTEL2_VON;
                addBotMessage(getMessage("accommodation.hotel2.from"));
                break;
                
            case UEBERNACHTUNG_HOTEL2_VON:
                if (validateDate(userInput)) {
                    formData.put("ÜbernachtungVon2", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL2_BIS;
                    addBotMessage(getMessage("accommodation.hotel2.to"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_HOTEL2_BIS:
                if (validateDate(userInput)) {
                    formData.put("ÜbernachtungBis2", userInput);
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL2_KOSTEN;
                    addBotMessage(getMessage("accommodation.hotel2.costs"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case UEBERNACHTUNG_HOTEL2_KOSTEN:
                formData.put("HotelKosten2", userInput);
                currentState = ChatbotState.UEBERNACHTUNG_HOTEL2_FRUEHSTUECK;
                addBotMessage(getMessage("accommodation.hotel2.breakfast"));
                break;
                
            case UEBERNACHTUNG_HOTEL2_FRUEHSTUECK:
                Boolean hotel2Fruehstueck = validateYesNoAnswer(userInput, "accommodation.hotel2.breakfast");
                if (hotel2Fruehstueck == null) {
                    return;
                }
                
                if (hotel2Fruehstueck) {
                    formData.put("MitFrühstück2", "Yes");
                } else {
                    formData.put("OhneFrühstück2", "Yes");
                }
                currentState = ChatbotState.UEBERNACHTUNG_HOTEL_RECHNUNG;
                addBotMessage(getMessage("accommodation.hotel.billing"));
                break;
                
            case UEBERNACHTUNG_HOTEL_RECHNUNG:
                Boolean hotelRechnung = validateYesNoAnswer(userInput, "accommodation.hotel.billing");
                if (hotelRechnung == null) {
                    return;
                }
                
                if (hotelRechnung) {
                    formData.put("BuchungRechnung", "Yes");
                }
                currentState = ChatbotState.UEBERNACHTUNG_HOTEL_BUCHUNG;
                addBotMessage(getMessage("accommodation.hotel.bookedBy"));
                addBotMessage("1: " + getMessage("accommodation.hotel.bookedBy.travelPrep"));
                addBotMessage("2: " + getMessage("accommodation.hotel.bookedBy.traveler"));
                addBotMessage("3: " + getMessage("accommodation.hotel.bookedBy.other"));
                break;
                
            case UEBERNACHTUNG_HOTEL_BUCHUNG:
                if (!processHotelBuchung(userInput)) {
                    addBotMessage(getMessage("error.invalidInput") + " " + getMessage("accommodation.hotel.bookedBy"));
                    addBotMessage("1: " + getMessage("accommodation.hotel.bookedBy.travelPrep"));
                    addBotMessage("2: " + getMessage("accommodation.hotel.bookedBy.traveler"));
                    addBotMessage("3: " + getMessage("accommodation.hotel.bookedBy.other"));
                    return;
                }
                
                if (formData.containsKey("BuchungReisenden") && formData.get("BuchungReisenden").equals("Yes")) {
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL_TMS;
                    addBotMessage(getMessage("accommodation.hotel.tms"));
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL_DOPPELZIMMER;
                    addBotMessage(getMessage("accommodation.hotel.doubleRoom"));
                }
                break;
                
            case UEBERNACHTUNG_HOTEL_TMS:
                Boolean hotelTMS = validateYesNoAnswer(userInput, "accommodation.hotel.tms");
                if (hotelTMS == null) {
                    return;
                }
                
                if (hotelTMS) {
                    formData.put("BuchungTMS", "Yes");
                }
                currentState = ChatbotState.UEBERNACHTUNG_HOTEL_PREISGRENZE;
                addBotMessage(getMessage("accommodation.hotel.priceLimit"));
                break;
                
            case UEBERNACHTUNG_HOTEL_PREISGRENZE:
                Boolean hotelPreisgrenze = validateYesNoAnswer(userInput, "accommodation.hotel.priceLimit");
                if (hotelPreisgrenze == null) {
                    return;
                }
                
                if (hotelPreisgrenze) {
                    formData.put("BuchungPreisgrenze", "Yes");
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL_PREISGRENZE_GRUND;
                    addBotMessage(getMessage("accommodation.hotel.priceLimit.reason"));
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG_HOTEL_DOPPELZIMMER;
                    addBotMessage(getMessage("accommodation.hotel.doubleRoom"));
                }
                break;
                
            case UEBERNACHTUNG_HOTEL_PREISGRENZE_GRUND:
                formData.put("BuchungPreisgrenzeGrund", userInput);
                currentState = ChatbotState.UEBERNACHTUNG_HOTEL_DOPPELZIMMER;
                addBotMessage(getMessage("accommodation.hotel.doubleRoom"));
                break;
                
            case UEBERNACHTUNG_HOTEL_DOPPELZIMMER:
                Boolean hotelDoppelzimmer = validateYesNoAnswer(userInput, "accommodation.hotel.doubleRoom");
                if (hotelDoppelzimmer == null) {
                    return;
                }
                
                if (hotelDoppelzimmer) {
                    formData.put("DoppelzimmerMitAnderen", "Yes");
                }
                currentState = ChatbotState.UEBERNACHTUNG_WOHNUNG;
                addBotMessage(getMessage("accommodation.residence.outside"));
                break;
                
            case UEBERNACHTUNG_WOHNUNG:
                Boolean wohnungAussen = validateYesNoAnswer(userInput, "accommodation.residence.outside");
                if (wohnungAussen == null) {
                    return;
                }
                
                if (wohnungAussen) {
                    formData.put("ÜbernachtungWohnungAus", "Yes");
                    currentState = ChatbotState.UEBERNACHTUNG_WOHNUNG_BETRETEN;
                    addBotMessage(getMessage("accommodation.residence.outside.enter"));
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG_WOHNUNG_GESCHAEFTSORT;
                    addBotMessage(getMessage("accommodation.residence.destination"));
                }
                break;
                
            case UEBERNACHTUNG_WOHNUNG_BETRETEN:
                formData.put("ÜbernachtungWohnungAusBetreten", userInput);
                currentState = ChatbotState.UEBERNACHTUNG_WOHNUNG_VERLASSEN;
                addBotMessage(getMessage("accommodation.residence.outside.leave"));
                break;
                
            case UEBERNACHTUNG_WOHNUNG_VERLASSEN:
                formData.put("ÜbernachtungWohnungAusVerlassen", userInput);
                currentState = ChatbotState.UEBERNACHTUNG_WOHNUNG_GESCHAEFTSORT;
                addBotMessage(getMessage("accommodation.residence.destination"));
                break;
                
            case UEBERNACHTUNG_WOHNUNG_GESCHAEFTSORT:
                Boolean wohnungGeschaeftsort = validateYesNoAnswer(userInput, "accommodation.residence.destination");
                if (wohnungGeschaeftsort == null) {
                    return;
                }
                
                if (wohnungGeschaeftsort) {
                    formData.put("ÜbernachtungWohnungAm", "Yes");
                    currentState = ChatbotState.UEBERNACHTUNG_WOHNUNG_GESCHAEFTSORT_BETRETEN;
                    addBotMessage(getMessage("accommodation.residence.destination.enter"));
                } else {
                    currentState = ChatbotState.UEBERNACHTUNG_PRIVAT;
                    addBotMessage(getMessage("accommodation.private"));
                }
                break;
                
            case UEBERNACHTUNG_WOHNUNG_GESCHAEFTSORT_BETRETEN:
                formData.put("ÜbernachtungWohnungAmBetreten", userInput);
                currentState = ChatbotState.UEBERNACHTUNG_WOHNUNG_GESCHAEFTSORT_VERLASSEN;
                addBotMessage(getMessage("accommodation.residence.destination.leave"));
                break;
                
            case UEBERNACHTUNG_WOHNUNG_GESCHAEFTSORT_VERLASSEN:
                formData.put("ÜbernachtungWohnungAmVerlassen", userInput);
                currentState = ChatbotState.UEBERNACHTUNG_PRIVAT;
                addBotMessage(getMessage("accommodation.private"));
                break;
                
            case UEBERNACHTUNG_PRIVAT:
                Boolean privatUebernachtung = validateYesNoAnswer(userInput, "accommodation.private");
                if (privatUebernachtung == null) {
                    return;
                }
                
                if (privatUebernachtung) {
                    formData.put("PrivateÜbernachtung", "Yes");
                }
                currentState = ChatbotState.UEBERNACHTUNG_BEFOERDERUNG;
                addBotMessage(getMessage("accommodation.transport"));
                break;
                
            case UEBERNACHTUNG_BEFOERDERUNG:
                Boolean befoerderungUebernachtung = validateYesNoAnswer(userInput, "accommodation.transport");
                if (befoerderungUebernachtung == null) {
                    return;
                }
                
                if (befoerderungUebernachtung) {
                    formData.put("ÜbernachtungInBeförderung", "Yes");
                }
                currentState = ChatbotState.UEBERNACHTUNG_KOSTEN_ENTHALTEN;
                addBotMessage(getMessage("accommodation.costs.included"));
                break;
                
            case UEBERNACHTUNG_KOSTEN_ENTHALTEN:
                Boolean kostenEnthalten = validateYesNoAnswer(userInput, "accommodation.costs.included");
                if (kostenEnthalten == null) {
                    return;
                }
                
                if (kostenEnthalten) {
                    formData.put("ÜbernachtungsKostenEnthalten", "Yes");
                }
                currentState = ChatbotState.ZUSATZ_LEISTUNG_DRITTE;
                addBotMessage(getMessage("additional.thirdParty"));
                break;
                
            case ZUSATZ_LEISTUNG_DRITTE:
                Boolean leistungDritte = validateYesNoAnswer(userInput, "additional.thirdParty");
                if (leistungDritte == null) {
                    return;
                }
                
                if (leistungDritte) {
                    formData.put("LeistungVonDritten", "Yes");
                    currentState = ChatbotState.ZUSATZ_LEISTUNG_DRITTE_HOEHE;
                    addBotMessage(getMessage("additional.thirdParty.amount"));
                } else {
                    currentState = ChatbotState.ZUSATZ_NEBENTAETIGKEIT;
                    addBotMessage(getMessage("additional.sideActivity"));
                }
                break;
                
            case ZUSATZ_LEISTUNG_DRITTE_HOEHE:
                formData.put("LeistungVonDrittenHöhe", userInput);
                currentState = ChatbotState.ZUSATZ_NEBENTAETIGKEIT;
                addBotMessage(getMessage("additional.sideActivity"));
                break;
                
            case ZUSATZ_NEBENTAETIGKEIT:
                Boolean nebentaetigkeit = validateYesNoAnswer(userInput, "additional.sideActivity");
                if (nebentaetigkeit == null) {
                    return;
                }
                
                if (nebentaetigkeit) {
                    formData.put("InVerbindungmitNeben", "Yes");
                }
                currentState = ChatbotState.ZUSATZ_ABSCHLAG;
                addBotMessage(getMessage("additional.advance"));
                break;
                
            case ZUSATZ_ABSCHLAG:
                Boolean abschlag = validateYesNoAnswer(userInput, "additional.advance");
                if (abschlag == null) {
                    return;
                }
                
                if (abschlag) {
                    formData.put("Abschlag", "Yes");
                    currentState = ChatbotState.ZUSATZ_ABSCHLAG_HOEHE;
                    addBotMessage(getMessage("additional.advance.amount"));
                } else {
                    currentState = ChatbotState.ZUSATZ_ERGAENZENDE_AUSFUEHRUNGEN;
                    addBotMessage(getMessage("additional.comments"));
                }
                break;
                
            case ZUSATZ_ABSCHLAG_HOEHE:
                formData.put("AbschlagHöhe", userInput);
                currentState = ChatbotState.ZUSATZ_ERGAENZENDE_AUSFUEHRUNGEN;
                addBotMessage(getMessage("additional.comments"));
                break;
                
            case ZUSATZ_ERGAENZENDE_AUSFUEHRUNGEN:
                formData.put("ErgänzendeAusführungen", userInput);
                currentState = ChatbotState.ZUSATZ_BELEGE;
                addBotMessage(getMessage("additional.receipts"));
                break;
                
            case ZUSATZ_BELEGE:
                Boolean belege = validateYesNoAnswer(userInput, "additional.receipts");
                if (belege == null) {
                    return;
                }
                
                if (belege) {
                    formData.put("Belege", "Yes");
                }
                currentState = ChatbotState.ZUSATZ_MUENDLICH_GENEHMIGT;
                addBotMessage(getMessage("additional.verbalApproval"));
                break;
                
            case ZUSATZ_MUENDLICH_GENEHMIGT:
                Boolean muendlichGenehmigt = validateYesNoAnswer(userInput, "additional.verbalApproval");
                if (muendlichGenehmigt == null) {
                    return;
                }
                
                if (muendlichGenehmigt) {
                    formData.put("MündlichGenehmigtJa", "Yes");
                } else {
                    formData.put("MündlichGenehmigtNein", "Yes");
                }
                currentState = ChatbotState.ZUSATZ_UNTERSCHRIFT_ORT;
                addBotMessage(getMessage("additional.signature.location"));
                break;
                
            case ZUSATZ_UNTERSCHRIFT_ORT:
                formData.put("UnterschriftOrt", userInput);
                currentState = ChatbotState.ZUSATZ_UNTERSCHRIFT_DATUM;
                addBotMessage(getMessage("additional.signature.date"));
                break;
                
            case ZUSATZ_UNTERSCHRIFT_DATUM:
                if (validateDate(userInput)) {
                    formData.put("UnterschriftDatum", userInput);
                    currentState = ChatbotState.ABSCHLUSS_PDF;
                    addBotMessage(getMessage("pdf.create"));
                } else {
                    addBotMessage(getMessage("error.invalidDate"));
                }
                break;
                
            case ABSCHLUSS_PDF:
                Boolean createPdf = validateYesNoAnswer(userInput, "pdf.create");
                if (createPdf == null) {
                    return;
                }
                
                if (createPdf) {
                    currentState = ChatbotState.DONE;
                    generatePDF();
                    addBotMessage(getMessage("pdf.success"));
                    addBotMessage(getMessage("app.thankyou"));
                } else {
                    currentState = ChatbotState.DONE;
                    addBotMessage(getMessage("app.thankyou.noExport"));
                }
                break;
                
            default:
                // Handle unprocessed states
                addBotMessage(getMessage("error.notImplemented"));
                currentState = ChatbotState.DONE;
                break;
        }
    }
    
    /**
     * Process the status selection
     * @return true if input was valid
     */
    private boolean processStatusSelection(String userInput) {
        switch (userInput.trim()) {
            case "1":
                formData.put("beamter", "Yes");
                addBotMessage(getMessage("status.selected") + ": " + getMessage("personal.status.civil"));
                return true;
            case "2":
                formData.put("tarifB", "Yes");
                addBotMessage(getMessage("status.selected") + ": " + getMessage("personal.status.tariff"));
                return true;
            case "3":
                formData.put("anwärter", "Yes");
                addBotMessage(getMessage("status.selected") + ": " + getMessage("personal.status.trainee"));
                return true;
            case "4":
                formData.put("azubi", "Yes");
                addBotMessage(getMessage("status.selected") + ": " + getMessage("personal.status.apprentice"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Process the start location of the journey
     * @return true if input was valid
     */
    private boolean processReiseBeginnOrt(String userInput) {
        switch (userInput.trim()) {
            case "1":
                formData.put("beginnWohnung", "Yes");
                addBotMessage(getMessage("travel.startLocation") + ": " + getMessage("travel.startLocation.home"));
                return true;
            case "2":
                formData.put("beginnDienststelle", "Yes");
                addBotMessage(getMessage("travel.startLocation") + ": " + getMessage("travel.startLocation.office"));
                return true;
            case "3":
                formData.put("beginnVorübergehend", "Yes");
                addBotMessage(getMessage("travel.startLocation") + ": " + getMessage("travel.startLocation.temporary"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Process the end location of the journey
     * @return true if input was valid
     */
    private boolean processReiseEndeOrt(String userInput) {
        switch (userInput.trim()) {
            case "1":
                formData.put("endeWohnung", "Yes");
                addBotMessage(getMessage("travel.endLocation") + ": " + getMessage("travel.endLocation.home"));
                return true;
            case "2":
                formData.put("endeDienststelle", "Yes");
                addBotMessage(getMessage("travel.endLocation") + ": " + getMessage("travel.endLocation.office"));
                return true;
            case "3":
                formData.put("endeVorübergehend", "Yes");
                addBotMessage(getMessage("travel.endLocation") + ": " + getMessage("travel.endLocation.temporary"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Process the billing office selection
     * @return true if input was valid
     */
    private boolean processAbrechnungsstelle(String userInput) {
        switch (userInput.trim()) {
            case "1":
                formData.put("AsBerlin", "Yes");
                addBotMessage(getMessage("travel.accountingOffice") + ": " + getMessage("travel.accountingOffice.berlin"));
                return true;
            case "2":
                formData.put("AsHamm", "Yes");
                addBotMessage(getMessage("travel.accountingOffice") + ": " + getMessage("travel.accountingOffice.hamm"));
                return true;
            case "3":
                formData.put("AsOsnabrück", "Yes");
                addBotMessage(getMessage("travel.accountingOffice") + ": " + getMessage("travel.accountingOffice.osnabrueck"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Process the transportation selection
     */
    private void processVerkehrsmittelAuswahl(String userInput) {
        verkehrsmittelSelected = new boolean[10];
        
        // Parse comma-separated numbers
        String[] selections = userInput.split(",");
        for (String selection : selections) {
            try {
                int index = Integer.parseInt(selection.trim());
                if (index >= 1 && index <= 10) {
                    verkehrsmittelSelected[index - 1] = true;
                }
            } catch (NumberFormatException e) {
            }
        }
        
        // Set initial flags
        if (verkehrsmittelSelected[0]) formData.put("dienstKfz", "Yes");
        if (verkehrsmittelSelected[1]) formData.put("privatKfz", "Yes");
        if (verkehrsmittelSelected[2]) formData.put("mitfahrer", "Yes");
        if (verkehrsmittelSelected[3]) formData.put("mietwagen", "Yes");
        if (verkehrsmittelSelected[4]) formData.put("bahn", "Yes");
        if (verkehrsmittelSelected[5]) formData.put("flug", "Yes");
        if (verkehrsmittelSelected[6]) formData.put("öpnv", "Yes");
        if (verkehrsmittelSelected[7]) formData.put("taxi", "Yes");
        if (verkehrsmittelSelected[8]) formData.put("fahrrad", "Yes");
        if (verkehrsmittelSelected[9]) formData.put("andereVerkehrsmittel", "Yes");
        
        // Reset current index to prepare for processing
        currentVerkehrsmittelIndex = -1;
    }
    
    /**
     * Process the next selected transportation method
     * @return true if a transportation method was processed, false if no more transportation methods need to be processed
     */
    private boolean processNextVerkehrsmittel() {
        currentVerkehrsmittelIndex++;
        
        while (currentVerkehrsmittelIndex < verkehrsmittelSelected.length) {
            if (verkehrsmittelSelected[currentVerkehrsmittelIndex]) {
                switch (currentVerkehrsmittelIndex) {
                    case 0: // Dienst-Kfz
                        addBotMessage(getMessage("transport.official") + " " + getMessage("status.selected"));
                        currentVerkehrsmittelIndex++;
                        return processNextVerkehrsmittel();
                        
                    case 1: // Privates Kfz
                        currentState = ChatbotState.VERKEHR_PRIVATKFZ_WEGSTRECKENART;
                        addBotMessage(getMessage("transport.private.compensation"));
                        addBotMessage("1: " + getMessage("transport.private.compensation.small"));
                        addBotMessage("2: " + getMessage("transport.private.compensation.large"));
                        return true;
                        
                    case 2: // Mitfahrer
                        currentState = ChatbotState.VERKEHR_MITFAHRER_NAME;
                        addBotMessage(getMessage("transport.passenger.name"));
                        return true;
                        
                    case 3: // Mietwagen
                        currentState = ChatbotState.VERKEHR_MIETWAGEN_BUCHUNG;
                        addBotMessage(getMessage("transport.rental.bookedBy"));
                        addBotMessage("1: " + getMessage("transport.rental.bookedBy.travelPrep"));
                        addBotMessage("2: " + getMessage("transport.rental.bookedBy.self"));
                        return true;
                        
                    case 4: // Bahn
                        currentState = ChatbotState.VERKEHR_BAHN_BUCHUNG;
                        addBotMessage(getMessage("transport.train.bookedBy"));
                        addBotMessage("1: " + getMessage("transport.train.bookedBy.travelPrep"));
                        addBotMessage("2: " + getMessage("transport.train.bookedBy.self"));
                        return true;
                        
                    case 5: // Flug
                        currentState = ChatbotState.VERKEHR_FLUG_BUCHUNG;
                        addBotMessage(getMessage("transport.flight.bookedBy"));
                        addBotMessage("1: " + getMessage("transport.flight.bookedBy.travelPrep"));
                        addBotMessage("2: " + getMessage("transport.flight.bookedBy.self"));
                        return true;
                        
                    case 6: // ÖPNV
                        currentState = ChatbotState.VERKEHR_OEPNV_ANZAHL;
                        addBotMessage(getMessage("transport.public.trips"));
                        return true;
                        
                    case 7: // Taxi
                        currentState = ChatbotState.VERKEHR_TAXI_ANTRAG;
                        addBotMessage(getMessage("transport.taxi.request"));
                        return true;
                        
                    case 8: // Fahrrad
                        currentState = ChatbotState.VERKEHR_FAHRRAD_ANZAHL;
                        addBotMessage(getMessage("transport.bicycle.trips"));
                        return true;
                        
                    case 9: // Sonstiges
                        currentState = ChatbotState.VERKEHR_SONSTIGES_ART;
                        addBotMessage(getMessage("transport.other.type"));
                        return true;
                        
                    default:
                        currentVerkehrsmittelIndex++;
                        continue;
                }
            }
            
            currentVerkehrsmittelIndex++;
        }
        
        return false;
    }
    
    /**
     * Process the private car compensation type
     * @return true if input was valid
     */
    private boolean processPrivatKfzWegstreckenart(String userInput) {
        switch (userInput.trim()) {
            case "1":
                kfzWegstreckenartKlein = true;
                formData.put("KfzKleineWECheck", "Yes");
                addBotMessage(getMessage("transport.private.compensation") + ": " + getMessage("transport.private.compensation.small"));
                return true;
            case "2":
                kfzWegstreckenartKlein = false;
                formData.put("KfzGrosseWECheck", "Yes");
                addBotMessage(getMessage("transport.private.compensation") + ": " + getMessage("transport.private.compensation.large"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Process rental car booking type
     * @return true if input was valid
     */
    private boolean processMietwagenBuchung(String userInput) {
        switch (userInput.trim()) {
            case "1":
                formData.put("mietwagenRV", "Yes");
                addBotMessage(getMessage("transport.rental.bookedBy") + ": " + getMessage("transport.rental.bookedBy.travelPrep"));
                return true;
            case "2":
                formData.put("mietwagenSelbst", "Yes");
                addBotMessage(getMessage("transport.rental.bookedBy") + ": " + getMessage("transport.rental.bookedBy.self"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Process train booking type
     * @return true if input was valid
     */
    private boolean processBahnBuchung(String userInput) {
        switch (userInput.trim()) {
            case "1":
                formData.put("bahnRV", "Yes");
                addBotMessage(getMessage("transport.train.bookedBy") + ": " + getMessage("transport.train.bookedBy.travelPrep"));
                return true;
            case "2":
                formData.put("bahnSelbst", "Yes");
                addBotMessage(getMessage("transport.train.bookedBy") + ": " + getMessage("transport.train.bookedBy.self"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Process train card type
     * @return true if input was valid
     */
    private boolean processBahncardTyp(String userInput) {
        switch (userInput.trim()) {
            case "1":
                formData.put("bahncardPrivat", "Yes");
                addBotMessage(getMessage("transport.train.bahncard.type") + ": " + getMessage("transport.train.bahncard.type.private"));
                return true;
            case "2":
                formData.put("bahncardBusiness", "Yes");
                addBotMessage(getMessage("transport.train.bahncard.type") + ": " + getMessage("transport.train.bahncard.type.business"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Process train card value
     * @return true if input was valid
     */
    private boolean processBahncardWert(String userInput) {
        switch (userInput.trim()) {
            case "1":
                formData.put("bahncard25", "Yes");
                addBotMessage(getMessage("transport.train.bahncard.value") + ": " + getMessage("transport.train.bahncard.value.25"));
                return true;
            case "2":
                formData.put("bahncard50", "Yes");
                addBotMessage(getMessage("transport.train.bahncard.value") + ": " + getMessage("transport.train.bahncard.value.50"));
                return true;
            case "3":
                formData.put("bahncard100", "Yes");
                addBotMessage(getMessage("transport.train.bahncard.value") + ": " + getMessage("transport.train.bahncard.value.100"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Process train card class
     * @return true if input was valid
     */
    private boolean processBahncardKlasse(String userInput) {
        switch (userInput.trim()) {
            case "1":
                formData.put("klasse1", "Yes");
                addBotMessage(getMessage("transport.train.bahncard.class") + ": " + getMessage("transport.train.bahncard.class.first"));
                return true;
            case "2":
                formData.put("klasse2", "Yes");
                addBotMessage(getMessage("transport.train.bahncard.class") + ": " + getMessage("transport.train.bahncard.class.second"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Process flight booking type
     * @return true if input was valid
     */
    private boolean processFlugBuchung(String userInput) {
        switch (userInput.trim()) {
            case "1":
                formData.put("flugRV", "Yes");
                addBotMessage(getMessage("transport.flight.bookedBy") + ": " + getMessage("transport.flight.bookedBy.travelPrep"));
                return true;
            case "2":
                formData.put("flugSelbst", "Yes");
                addBotMessage(getMessage("transport.flight.bookedBy") + ": " + getMessage("transport.flight.bookedBy.self"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Process hotel booking type
     * @return true if input was valid
     */
    private boolean processHotelBuchung(String userInput) {
        switch (userInput.trim()) {
            case "1":
                formData.put("BuchungRv", "Yes");
                addBotMessage(getMessage("accommodation.hotel.bookedBy") + ": " + getMessage("accommodation.hotel.bookedBy.travelPrep"));
                return true;
            case "2":
                formData.put("BuchungReisenden", "Yes");
                addBotMessage(getMessage("accommodation.hotel.bookedBy") + ": " + getMessage("accommodation.hotel.bookedBy.traveler"));
                return true;
            case "3":
                formData.put("BuchungAndereStelle", "Yes");
                addBotMessage(getMessage("accommodation.hotel.bookedBy") + ": " + getMessage("accommodation.hotel.bookedBy.other"));
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Start the conversation
     */
    private void startConversation() {
        addBotMessage(getMessage("welcome.message"));
        addBotMessage(getMessage("welcome.help"));
        
        addBotMessage(getMessage("authority.request"));
        currentState = ChatbotState.WELCOME;
    }
    
    /**
     * Generate the filled PDF
     */
    private void generatePDF() {
        try {
            // File paths
            File pdfTemplate = new File("rkinland.pdf");
            String outputPath = "ausgefuelltes_formular.pdf";
            
            // Create reader and stamper
            PdfReader reader = new PdfReader(pdfTemplate.getAbsolutePath());
            PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(outputPath));
            
            try {
                // Get the form fields and direct content for custom text
                AcroFields form = stamper.getAcroFields();
                
                // Process regular form fields (non-date/time fields)
                processRegularFields(form);
                
                // Process date and time fields with custom text overlays
                addCustomTextOverlays(stamper);
                
                // Don't flatten the form so fields remain editable
                stamper.setFormFlattening(false);
                
            } finally {
                // Close stamper and reader
                try {
                    stamper.close();
                    reader.close();
                } catch (Exception e) {
                    String errorMsg = "Warning: Error closing PDF: " + e.getMessage();
                    addBotMessage(errorMsg);
                }
            }
            
        } catch (Exception e) {
            String errorMsg = "Error creating PDF: " + e.getMessage();
            addBotMessage(errorMsg);
            e.printStackTrace();
        }
    }
    
    /**
     * Process all regular form fields (non-date/time fields)
     */
    private void processRegularFields(AcroFields form) throws IOException, DocumentException {
        // Process all fields except those that need direct text overlay
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue();
            
            // Skip fields that will be handled by custom text overlays
            if (isCustomOverlayField(fieldName)) {
                continue;
            }
            
            // Map the field name to PDF field name
            String pdfFieldName = fieldMappings.getOrDefault(fieldName, fieldName);
            
            try {
                // Check if field exists to avoid errors
                if (form.getField(pdfFieldName) != null) {
                    int fieldType = form.getFieldType(pdfFieldName);
                    
                    if (fieldValue.equals("Yes") && 
                        (fieldType == AcroFields.FIELD_TYPE_CHECKBOX ||
                         fieldType == AcroFields.FIELD_TYPE_RADIOBUTTON)) {
                        // For checkboxes and radio buttons
                        String[] states = form.getAppearanceStates(pdfFieldName);
                        if (states != null && states.length > 0) {
                            for (String state : states) {
                                if (!state.equalsIgnoreCase("Off")) {
                                    form.setField(pdfFieldName, state);
                                    break;
                                }
                            }
                        }
                    } else {
                        // For text fields and other types
                        form.setField(pdfFieldName, fieldValue);
                    }
                }
            } catch (Exception e) {
                String warningMsg = "Warning: Could not process field " + pdfFieldName + ": " + e.getMessage();
                addBotMessage(warningMsg);
            }
        }
    }
    
    /**
     * Determines if a field should be handled by custom text overlay instead of standard form filling
     */
    private boolean isCustomOverlayField(String fieldName) {
        return fieldName.equals("beginnReiseDatum") || 
               fieldName.equals("beginnReiseZeit") ||
               fieldName.equals("ankunftDatum") ||
               fieldName.equals("ankunftUhrzeit") ||
               fieldName.equals("beginnDienstDatum") ||
               fieldName.equals("beginnDienstUhrzeit") ||
               fieldName.equals("endeDienstDatum") ||
               fieldName.equals("endeDienstUhrzeit") ||
               fieldName.equals("abfahrtDatum") ||
               fieldName.equals("abfahrtUhrzeit") ||
               fieldName.equals("endeReiseDatum") ||
               fieldName.equals("endeReiseZeit") ||
               fieldName.equals("dienstKfz") ||
               fieldName.equals("privatKfz") ||
               fieldName.equals("mitfahrerName") ||
               fieldName.equals("flugBonusProgramm") ||
               fieldName.equals("KfzKleineWEAnzahlKm") ||
               fieldName.equals("KfzKleineWEOrt") ||
               fieldName.equals("KfzGrosseWEAnzahlKm") ||
               fieldName.equals("KfzGrosseWEOrt") ||
               fieldName.equals("Mietkosten") ||
               fieldName.equals("Benzinkosten") ||
               fieldName.equals("BahnHinfahrt") ||
               fieldName.equals("BahnRückfahrt") ||
               fieldName.equals("FlugKosten") ||
               fieldName.equals("öpnvAnzahl") ||
               fieldName.equals("öpnvKosten") ||
               fieldName.equals("taxiAnzahl") ||
               fieldName.equals("taxiKosten") ||
               fieldName.equals("parkgebuehrenAnzahl") ||
               fieldName.equals("parkgebuehrenKosten") ||
               fieldName.equals("fahrradAnzahl") ||
               fieldName.equals("SonstigeKostenAnzahl") ||
               fieldName.equals("SonstigeKostenKosten") ||
               fieldName.equals("UnterkunftVon") ||
               fieldName.equals("UnterkunftBis") ||
               fieldName.equals("FrühstückVon") ||
               fieldName.equals("FrühstückBis") ||
               fieldName.equals("MittagessenVon") ||
               fieldName.equals("MittagessenBis") ||
               fieldName.equals("AbendessenVon") ||
               fieldName.equals("AbendessenBis") ||
               fieldName.equals("HotelName1") ||
               fieldName.equals("ÜbernachtungOrt1") ||
               fieldName.equals("ÜbernachtungVon1") ||
               fieldName.equals("ÜbernachtungBis1") ||
               fieldName.equals("HotelKosten1") ||
               fieldName.equals("HotelName2") ||
               fieldName.equals("ÜbernachtungOrt2") ||
               fieldName.equals("ÜbernachtungVon2") ||
               fieldName.equals("ÜbernachtungBis2") ||
               fieldName.equals("HotelKosten2") ||
               fieldName.equals("LeistungVonDrittenHöhe") ||
               fieldName.equals("AbschlagHöhe") ||
               fieldName.equals("AsHamm") ||
               fieldName.equals("AsOsnabrück") ||
               fieldName.equals("AsBerlin");
    }
    
    /**
     * Add custom text overlays for all fields requiring direct placement
     */
    private void addCustomTextOverlays(PdfStamper stamper) throws IOException, DocumentException {
        // Create font for the text
        BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        
        // Add page overlays
        addPage1Overlays(stamper.getOverContent(1), bf);
        addPage2Overlays(stamper.getOverContent(2), bf);
        addPage3Overlays(stamper.getOverContent(3), bf);
    }
    
    /**
     * Add custom text overlays for page 1 (travel dates and times)
     */
    private void addPage1Overlays(PdfContentByte canvas, BaseFont bf) throws DocumentException {
        Map<String, float[]> coordinates = new HashMap<>();
        
        // Handle accounting office address
        if (formData.containsKey("AsHamm") && formData.get("AsHamm").equals("Yes")) {
            addTextWithPosition(canvas, bf, "Bundesverwaltungsamt", 60, 690, 10);
            addTextWithPosition(canvas, bf, "-Außenstelle Hamm-", 60, 675, 10);
            addTextWithPosition(canvas, bf, "Alter Uentroper Weg 2", 60, 660, 10);
            addTextWithPosition(canvas, bf, "59071 Hamm", 60, 645, 10);
        } 
        else if (formData.containsKey("AsOsnabrück") && formData.get("AsOsnabrück").equals("Yes")) {
            addTextWithPosition(canvas, bf, "Bundesverwaltungsamt", 60, 690, 10);
            addTextWithPosition(canvas, bf, "-Außenstelle Osnabrück-", 60, 675, 10);
            addTextWithPosition(canvas, bf, "Hannoversche Straße 6-8", 60, 660, 10);
            addTextWithPosition(canvas, bf, "49084 Osnabrück", 60, 645, 10);
        }
        else if (formData.containsKey("AsBerlin") && formData.get("AsBerlin").equals("Yes")) {
            addTextWithPosition(canvas, bf, "Bundesverwaltungsamt", 60, 690, 10);
            addTextWithPosition(canvas, bf, "-Außenstelle Berlin-", 60, 675, 10);
            addTextWithPosition(canvas, bf, "DGZ-Ring 12", 60, 660, 10);
            addTextWithPosition(canvas, bf, "13086 Berlin", 60, 645, 10);
        }
    
        coordinates.put("beginnReiseDatum", new float[]{200, 207.5f});
        coordinates.put("beginnReiseZeit", new float[]{275, 207.5f});
        coordinates.put("ankunftDatum", new float[]{200, 186.5f});
        coordinates.put("ankunftUhrzeit", new float[]{275, 186.5f});
        coordinates.put("beginnDienstDatum", new float[]{200, 172.5f});
        coordinates.put("beginnDienstUhrzeit", new float[]{275, 172.5f});
        coordinates.put("endeDienstDatum", new float[]{200, 158.5f});
        coordinates.put("endeDienstUhrzeit", new float[]{275, 158.5f});
        coordinates.put("abfahrtDatum", new float[]{200, 144.5f});
        coordinates.put("abfahrtUhrzeit", new float[]{275, 144.5f});
        coordinates.put("endeReiseDatum", new float[]{200, 123.5f});
        coordinates.put("endeReiseZeit", new float[]{275, 123.5f});
        
        renderTextOverlays(canvas, bf, coordinates);
    }
    
    /**
     * Add custom text overlays for page 2 (travel expenses and transport)
     */
    private void addPage2Overlays(PdfContentByte canvas, BaseFont bf) throws DocumentException {
        Map<String, float[]> coordinates = new HashMap<>();

        if (formData.containsKey("dienstKfz") && formData.get("dienstKfz").equals("Yes")) {
            coordinates.put("dienstKfz", new float[]{70, 765});
        }
        
        if (formData.containsKey("privatKfz") && formData.get("privatKfz").equals("Yes")) {
            coordinates.put("privatKfz", new float[]{70, 748});
        }
        
        coordinates.put("mitfahrerName", new float[]{190, 718});
        
        if (formData.containsKey("flugBonusProgramm") && formData.get("flugBonusProgramm").equals("Yes")) {
            coordinates.put("flugBonusProgramm", new float[]{70, 490});
        }
        
        coordinates.put("KfzKleineWEAnzahlKm", new float[]{320, 358});
        coordinates.put("KfzKleineWEOrt", new float[]{380, 358});
        coordinates.put("KfzGrosseWEAnzahlKm", new float[]{320, 338});
        coordinates.put("KfzGrosseWEOrt", new float[]{380, 338});
        coordinates.put("Mietkosten", new float[]{220, 300});
        coordinates.put("Benzinkosten", new float[]{220, 290});
        coordinates.put("BahnHinfahrt", new float[]{220, 260});
        coordinates.put("BahnRückfahrt", new float[]{220, 250});
        coordinates.put("FlugKosten", new float[]{220, 230});
        coordinates.put("öpnvAnzahl", new float[]{170, 150});
        coordinates.put("öpnvKosten", new float[]{230, 150});
        coordinates.put("taxiAnzahl", new float[]{170, 120});
        coordinates.put("taxiKosten", new float[]{230, 120});
        coordinates.put("parkgebuehrenAnzahl", new float[]{170, 95});
        coordinates.put("parkgebuehrenKosten", new float[]{230, 95});
        coordinates.put("fahrradAnzahl", new float[]{170, 65});
        coordinates.put("SonstigeKostenAnzahl", new float[]{170, 40});
        coordinates.put("SonstigeKostenKosten", new float[]{230, 40});
        
        renderCheckboxFields(canvas, bf, coordinates);
        renderTextOverlays(canvas, bf, coordinates);
    }
    
    /**
     * Add custom text overlays for page 3 (accommodation and other expenses)
     */
    private void addPage3Overlays(PdfContentByte canvas, BaseFont bf) throws DocumentException {
        Map<String, float[]> coordinates = new HashMap<>();
        coordinates.put("UnterkunftVon", new float[]{50, 745});
        coordinates.put("UnterkunftBis", new float[]{50, 730});
        coordinates.put("FrühstückVon", new float[]{210, 745}); 
        coordinates.put("FrühstückBis", new float[]{210, 730});
        coordinates.put("MittagessenVon", new float[]{330, 745});
        coordinates.put("MittagessenBis", new float[]{330, 730});
        coordinates.put("AbendessenVon", new float[]{450, 745});
        coordinates.put("AbendessenBis", new float[]{450, 730});
        coordinates.put("HotelName1", new float[]{120, 410});
        coordinates.put("ÜbernachtungOrt1", new float[]{140, 390});
        coordinates.put("ÜbernachtungVon1", new float[]{75, 368});
        coordinates.put("ÜbernachtungBis1", new float[]{155, 368});
        coordinates.put("HotelKosten1", new float[]{120, 350});
        
        coordinates.put("HotelName2", new float[]{360, 410});
        coordinates.put("ÜbernachtungOrt2", new float[]{380, 390});
        coordinates.put("ÜbernachtungVon2", new float[]{320, 368});
        coordinates.put("ÜbernachtungBis2", new float[]{400, 368});
        coordinates.put("HotelKosten2", new float[]{370, 350});
        coordinates.put("LeistungVonDrittenHöhe", new float[]{380, 180});
        coordinates.put("AbschlagHöhe", new float[]{230, 120});
        
        renderTextOverlays(canvas, bf, coordinates);
    }
    
    /**
     * Helper method to add text at specific coordinates
     */
    private void addTextWithPosition(PdfContentByte canvas, BaseFont bf, String text, float x, float y, float fontSize) {
        canvas.beginText();
        canvas.setFontAndSize(bf, fontSize);
        canvas.setTextMatrix(x, y);
        canvas.showText(text);
        canvas.endText();
    }
    
    /**
     * Render checkbox-style text overlays with an "X"
     */
    private void renderCheckboxFields(PdfContentByte canvas, BaseFont bf, Map<String, float[]> coordinates) {
        try {
            // Set up canvas for drawing text
            canvas.beginText();
            canvas.setFontAndSize(bf, 10);
            
            // List of fields that should render as "X" when present
            Set<String> checkboxFields = Set.of("dienstKfz", "privatKfz", "flugBonusProgramm");
            
            // Add each checkbox field as an "X"
            for (String fieldName : checkboxFields) {
                if (coordinates.containsKey(fieldName)) {
                    float[] position = coordinates.get(fieldName);
                    
                    canvas.setTextMatrix(position[0], position[1]);
                    canvas.showText("X");
                    
                    // Remove from coordinates map so it doesn't get processed again in renderTextFields
                    coordinates.remove(fieldName);
                }
            }
            
            canvas.endText();
        } catch (Exception e) {
            addBotMessage("Fehler beim Erstellen der Checkbox-Textüberlagerungen: " + e.getMessage());
        }
    }
    
    /**
     * Render text overlays from a coordinate map
     */
    private void renderTextOverlays(PdfContentByte canvas, BaseFont bf, Map<String, float[]> coordinates) {
        try {
            // Set up canvas for drawing text
            canvas.beginText();
            canvas.setFontAndSize(bf, 10);
            
            for (Map.Entry<String, float[]> entry : coordinates.entrySet()) {
                String fieldName = entry.getKey();
                float[] position = entry.getValue();
                
                // Get the field value
                String fieldValue = formData.get(fieldName);
                
                if (fieldValue != null && !fieldValue.isEmpty()) {
                    // Position text at the specified coordinates
                    canvas.setTextMatrix(position[0], position[1]);
                    canvas.showText(fieldValue);
                }
            }
            
            canvas.endText();
        } catch (Exception e) {
            addBotMessage("Fehler beim Erstellen der benutzerdefinierten Textüberlagerungen: " + e.getMessage());
        }
    }
    
    /**
     * Add a bot message to the chat
     */
    private void addBotMessage(String message) {
        Platform.runLater(() -> {
            TextFlow messageFlow = new TextFlow();
            messageFlow.setPrefWidth(chatBox.getWidth() - 20);
            messageFlow.setPadding(new Insets(8));
            messageFlow.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 8px;");
            
            Text botText = new Text(getMessage("bot.prefix") + ": ");
            botText.setFont(Font.font("System", FontWeight.BOLD, 12));
            
            Text messageText = new Text(message);
            messageText.setFont(Font.font("System", 12));
            
            messageFlow.getChildren().addAll(botText, messageText);
            chatBox.getChildren().add(messageFlow);
            
            // Scroll to bottom
            chatScrollPane.setVvalue(1.0);
        });
    }
    
    /**
     * Add a user message to the chat
     */
    private void addUserMessage(String message) {
        Platform.runLater(() -> {
            TextFlow messageFlow = new TextFlow();
            messageFlow.setPrefWidth(chatBox.getWidth() - 20);
            messageFlow.setPadding(new Insets(8));
            messageFlow.setStyle("-fx-background-color: #d1e7ff; -fx-background-radius: 8px;");
            messageFlow.setTextAlignment(javafx.scene.text.TextAlignment.RIGHT);
            
            Text userText = new Text(getMessage("user.prefix") + ": ");
            userText.setFont(Font.font("System", FontWeight.BOLD, 12));
            
            Text messageText = new Text(message);
            messageText.setFont(Font.font("System", 12));
            
            messageFlow.getChildren().addAll(userText, messageText);
            
            HBox alignRight = new HBox(messageFlow);
            alignRight.setAlignment(Pos.CENTER_RIGHT);
            
            chatBox.getChildren().add(alignRight);
            
            // Scroll to bottom
            chatScrollPane.setVvalue(1.0);
        });
    }
    
    /**
     * Validate email address
     */
    private boolean validateEmail(String email) {
        Pattern emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
        
        if (!emailPattern.matcher(email).matches() || !email.contains(".") || !email.contains("@")) {
            return false;
        }
        
        if (email.contains(" ")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate phone number
     */
    private boolean validatePhoneNumber(String phone) {
        Pattern validChars = Pattern.compile("[\\d\\+\\-\\(\\)\\s]*");
        
        if (!validChars.matcher(phone).matches()) {
            return false;
        }
        
        long digitCount = phone.chars().filter(Character::isDigit).count();
        if (digitCount < 6) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate IBAN
     */
    private boolean validateIBAN(String iban) {
        String cleanIban = iban.replace(" ", "").toUpperCase();
        
        if (cleanIban.length() < 15 || cleanIban.length() > 34) {
            return false;
        }
        
        if (!cleanIban.substring(0, 2).matches("[A-Z]{2}") || 
            !cleanIban.substring(2, 4).matches("\\d{2}")) {
            return false;
        }
        
        if (!cleanIban.matches("[A-Z0-9]+")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate BIC
     */
    private boolean validateBIC(String bic) {
        String cleanBic = bic.trim().toUpperCase();
        
        if (cleanBic.length() != 8 && cleanBic.length() != 11) {
            return false;
        }
        
        if (!cleanBic.matches("[A-Z0-9]+")) {
            return false;
        }
        
        if (!cleanBic.substring(0, 4).matches("[A-Z]{4}")) {
            return false;
        }
        
        if (!cleanBic.substring(4, 6).matches("[A-Z]{2}")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate date format (DD.MM.YYYY)
     */
    private boolean validateDate(String date) {
        try {
            LocalDate.parse(date, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
    
    /**
     * Validate time format (HH:MM)
     */
    private boolean validateTime(String time) {
        try {
            LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
    
    /**
     * Load field mappings from configuration
     */
    private Map<String, String> loadFieldMappings() {
        Map<String, String> mappings = new HashMap<>();
        
        mappings.put("behörde", "Behörde ausfüllen");
        mappings.put("name", "Text1");
        mappings.put("beamter", "Check Box60");
        mappings.put("tarifB", "Check Box61");
        mappings.put("anwärter", "Check Box62");
        mappings.put("azubi", "Check Box63");
        mappings.put("aktenzeichen", "Text7");
        mappings.put("email", "eMail");
        mappings.put("dienstort", "Dienstort");
        mappings.put("referat", "Text4");
        mappings.put("kostenstelle", "Text5");
        mappings.put("kostenträger", "Text6");
        mappings.put("telefon", "Text8");
        mappings.put("stammBehörde", "Text22");
        mappings.put("anschrift", "Text9");
        mappings.put("familienwohnort", "Text10");
        mappings.put("personalNr", "Text14");
        mappings.put("geldinstitut", "Text11");
        mappings.put("iban", "Text12");
        mappings.put("bic", "Text13");
        mappings.put("geschäftsort", "Geschaeftsort");
        mappings.put("zweck", "Text15");
        mappings.put("privatreiseErläuterung", "Text19");
        mappings.put("telearbeitErläuterung", "Text20");
        
        // Date time fields
        mappings.put("beginnReiseDatum", "Text16.0");
        mappings.put("beginnReiseZeit", "Text17.0");
        mappings.put("ankunftDatum", "Text16.1");
        mappings.put("ankunftUhrzeit", "Text17.1");
        mappings.put("beginnDienstDatum", "Text16.2");
        mappings.put("beginnDienstUhrzeit", "Text17.2");
        mappings.put("endeDienstDatum", "Text16.3");
        mappings.put("endeDienstUhrzeit", "Text17.3");
        mappings.put("abfahrtDatum", "Text16.4");
        mappings.put("abfahrtUhrzeit", "Text17.4");
        mappings.put("endeReiseDatum", "Text16.5");
        mappings.put("endeReiseZeit", "Text17.5");
        
        // Transportation mappings
        mappings.put("dienstKfz", "Check Box9");
        mappings.put("privatKfz", "Check Box10");
        mappings.put("mitfahrer", "Check Box12");
        mappings.put("mitfahrerName", "Text23");
        mappings.put("mietwagen", "Check Box13");
        mappings.put("mietwagenRV", "Check Box14");
        mappings.put("mietwagenSelbst", "Check Box16");
        mappings.put("bahn", "Check Box19");
        mappings.put("bahnRV", "Check Box15");
        mappings.put("bahnSelbst", "Check Box17");
        mappings.put("bahncardVorhanden", "Check Box18");
        mappings.put("bahncardPrivat", "Check Box30");
        mappings.put("bahncardBusiness", "Check Box31");
        mappings.put("bahncard25", "Check Box20");
        mappings.put("bahncard50", "Check Box22");
        mappings.put("bahncard100", "Check Box23");
        mappings.put("klasse1", "Check Box21");
        mappings.put("klasse2", "Check Box24");
        mappings.put("bahnBonus", "Check Box25");
        mappings.put("bahnBonusName", "Text24");
        mappings.put("flug", "Check Box26");
        mappings.put("flugRV", "Check Box27");
        mappings.put("flugSelbst", "Check Box28");
        mappings.put("flugBonusProgramm", "Check Box29");
        mappings.put("flugBonusName", "Text26");
        mappings.put("andereVerkehrsmittel", "Check Box29");
        mappings.put("AndereVerkehrsmittelText", "Text21");
        
        // Expense mappings
        mappings.put("öpnv", "0[0]");
        mappings.put("taxi", "1[0]");
        mappings.put("parkgebuehren", "2[0]");
        mappings.put("fahrrad", "3[0]");
        mappings.put("SonstigeKosten", "4[0]");
        
        // Expense details 
        mappings.put("KfzKleineWECheck", "Check Box64");
        mappings.put("KfzGrosseWECheck", "Check Box65");
        mappings.put("KfzKleineWEAnzahlKm", "Text33.0");
        mappings.put("KfzKleineWEOrt", "Text34.0");
        mappings.put("KfzGrosseWEAnzahlKm", "Text33.1");
        mappings.put("KfzGrosseWEOrt", "Text34.1");
        mappings.put("Mietkosten", "Text28.0");
        mappings.put("Benzinkosten", "Text28.1.0");
        mappings.put("MietwagenBegründung", "Text30");
        mappings.put("BahnHinfahrt", "Text28.1.1");
        mappings.put("BahnRückfahrt", "Text28.1.2.0");
        mappings.put("BahnReisekostenVorgaben", "Check Box32");
        mappings.put("FlugKosten", "Text28.1.2.1");
        mappings.put("FlugBegründung", "Text36");
        mappings.put("öpnvAnzahl", "Text28.011.0");
        mappings.put("öpnvKosten", "Text28.0112.0");
        mappings.put("öpnvGrund", "Text37");
        mappings.put("taxiAnzahl", "Text28.011.1");
        mappings.put("taxiKosten", "Text28.0112.1");
        mappings.put("taxiGrund", "Text38");
        mappings.put("parkgebuehrenAnzahl", "Text28.011.2");
        mappings.put("parkgebuehrenKosten", "Text28.0112.2");
        mappings.put("parkgebuehrenGrund", "Text39");
        mappings.put("fahrradAnzahl", "Text28.011.3");
        mappings.put("fahrradGrund", "Text40");
        mappings.put("SonstigeKostenAnzahl", "Text28.011.4");
        mappings.put("SonstigeKostenKosten", "Text28.0112.4");
        mappings.put("SonstigeKostenGrund", "Text41");
        
        // Specific field mappings
        mappings.put("telearbeit", "Check Box8"); 
        mappings.put("privatreise", "Check Box7");
        mappings.put("taxiAntrag", "Check Box35");
        mappings.put("fahrradPauschale", "Check Box36");
        mappings.put("beginnWohnung", "Check Box1");
        mappings.put("beginnDienststelle", "Check Box2");
        mappings.put("beginnVorübergehend", "Check Box3");
        mappings.put("endeWohnung", "Check Box4");
        mappings.put("endeDienststelle", "Check Box5");
        mappings.put("endeVorübergehend", "Check Box6");
        
        // Accommodation mappings
        mappings.put("unterkunftUnentgeltlichJa", "Check Box37");
        mappings.put("unterkunftUnentgeltlichNein", "Check Box38");
        mappings.put("UnterkunftVon", "Text29.0");
        mappings.put("UnterkunftBis", "Text29.1");
        mappings.put("FrühstückVon", "Text32.0");
        mappings.put("FrühstückBis", "Text32.1");
        mappings.put("MittagessenVon", "Text35.0");
        mappings.put("MittagessenBis", "Text35.1");
        mappings.put("AbendessenVon", "Text42.0");
        mappings.put("AbendessenBis", "Text42.1");
        
        // Hotel
        mappings.put("ÜbernachtungWohnungAus", "Check Box33");
        mappings.put("ÜbernachtungWohnungAusBetreten", "Text54");
        mappings.put("ÜbernachtungWohnungAusVerlassen", "Text53");
        mappings.put("ÜbernachtungWohnungAm", "Check Box39");
        mappings.put("ÜbernachtungWohnungAmBetreten", "Text56");
        mappings.put("ÜbernachtungWohnungAmVerlassen", "Text55");
        mappings.put("PrivateÜbernachtung", "Check Box40");
        mappings.put("ÜbernachtungInBeförderung", "Check Box41");
        mappings.put("ÜbernachtungsKostenEnthalten", "Check Box42");
        
        mappings.put("HotelName1", "Text52.0.0");
        mappings.put("ÜbernachtungOrt1", "Text52.0.1");
        mappings.put("ÜbernachtungVon1", "Text18.0");
        mappings.put("ÜbernachtungBis1", "Text18.1");
        mappings.put("HotelKosten1", "Text28.0");
        mappings.put("MitFrühstück1", "Check Box47");
        mappings.put("OhneFrühstück1", "Check Box48");
        
        mappings.put("HotelName2", "Text52.1.0");
        mappings.put("ÜbernachtungOrt2", "Text52.1.1");
        mappings.put("ÜbernachtungVon2", "Text48.0");
        mappings.put("ÜbernachtungBis2", "Text48.1");
        mappings.put("HotelKosten2", "Text28.1.0");
        mappings.put("MitFrühstück2", "Check Box49");
        mappings.put("OhneFrühstück2", "Check Box50");
        
        mappings.put("BuchungRechnung", "Text43");
        mappings.put("BuchungRv", "Check Box44");
        mappings.put("BuchungReisenden", "Check Box45");
        mappings.put("BuchungAndereStelle", "Check Box46");
        
        mappings.put("BuchungTMS", "Check Box51");
        mappings.put("BuchungPreisgrenze", "Check Box52");
        mappings.put("BuchungPreisgrenzeGrund", "Text51");
        mappings.put("DoppelzimmerMitAnderen", "Check Box53");
        
        // Other fields
        mappings.put("LeistungVonDritten", "Check Box54");
        mappings.put("LeistungVonDrittenHöhe", "Text54");
        mappings.put("InVerbindungmitNeben", "Check Box55");
        mappings.put("Abschlag", "Check Box56");
        mappings.put("AbschlagHöhe", "Text55");
        mappings.put("ErgänzendeAusführungen", "Text45");
        mappings.put("Belege", "Check Box57");
        mappings.put("MündlichGenehmigtJa", "Check Box59");
        mappings.put("MündlichGenehmigtNein", "Check Box58");
        mappings.put("UnterschriftOrt", "Text43");
        mappings.put("UnterschriftDatum", "Text44");
        mappings.put("AsHamm", "Hamm");
        mappings.put("AsOsnabrück", "Osnabrück");
        mappings.put("AsBerlin", "Berlin");
        
        return mappings;
    }
    
    /**
     * Enum for chatbot states
     */
    private enum ChatbotState {
        LANGUAGE_SELECTION,
        WELCOME,
        // Personal Data States
        PERSONAL_NAME,
        PERSONAL_VORNAME,
        PERSONAL_STATUS,
        PERSONAL_AKTENZEICHEN,
        PERSONAL_REFERAT,
        PERSONAL_KOSTENSTELLE,
        PERSONAL_KOSTENTRAEGER,
        PERSONAL_TELEFON,
        PERSONAL_EMAIL,
        PERSONAL_ABORDNUNG,
        PERSONAL_STAMMBEHOERDE,
        PERSONAL_ERSTANTRAG,
        PERSONAL_ANSCHRIFT,
        PERSONAL_FAMILIENWOHNORT,
        PERSONAL_PERSONALNUMMER,
        PERSONAL_GELDINSTITUT,
        PERSONAL_IBAN,
        PERSONAL_BIC,
        
        // Travel Data States
        REISE_ZWECK,
        REISE_GESCHAEFTSORT,
        REISE_BEGINN_DATUM,
        REISE_BEGINN_ZEIT,
        REISE_BEGINN_ORT,
        REISE_ANKUNFT_DATUM,
        REISE_ANKUNFT_ZEIT,
        REISE_BEGINN_DIENST_DATUM,
        REISE_BEGINN_DIENST_ZEIT,
        REISE_ENDE_DIENST_DATUM,
        REISE_ENDE_DIENST_ZEIT,
        REISE_ABFAHRT_DATUM,
        REISE_ABFAHRT_ZEIT,
        REISE_ENDE_DATUM,
        REISE_ENDE_ZEIT,
        REISE_ENDE_ORT,
        REISE_PRIVATREISE,
        REISE_PRIVATREISE_ERLAEUTERUNG,
        REISE_TELEARBEIT,
        REISE_TELEARBEIT_ERLAEUTERUNG,
        REISE_ABRECHNUNGSSTELLE,
        
        // Transport States
        VERKEHRSMITTEL_AUSWAHL,
        
        // Transport Detail States
        VERKEHR_PRIVATKFZ_WEGSTRECKENART,
        VERKEHR_PRIVATKFZ_KILOMETER,
        VERKEHR_PRIVATKFZ_STRECKE,
        
        VERKEHR_MITFAHRER_NAME,
        
        VERKEHR_MIETWAGEN_BUCHUNG,
        VERKEHR_MIETWAGEN_KOSTEN,
        VERKEHR_MIETWAGEN_BENZIN,
        VERKEHR_MIETWAGEN_BEGRUENDUNG,
        
        VERKEHR_BAHN_BUCHUNG,
        VERKEHR_BAHN_BAHNCARD,
        VERKEHR_BAHN_BAHNCARD_TYP,
        VERKEHR_BAHN_BAHNCARD_WERT,
        VERKEHR_BAHN_BAHNCARD_KLASSE,
        VERKEHR_BAHN_BONUS,
        VERKEHR_BAHN_BONUS_NAME,
        VERKEHR_BAHN_HINFAHRT,
        VERKEHR_BAHN_RUECKFAHRT,
        VERKEHR_BAHN_VORGABEN,
        
        VERKEHR_FLUG_BUCHUNG,
        VERKEHR_FLUG_KOSTEN,
        VERKEHR_FLUG_BEGRUENDUNG,
        VERKEHR_FLUG_BONUS,
        VERKEHR_FLUG_BONUS_NAME,
        
        VERKEHR_OEPNV_ANZAHL,
        VERKEHR_OEPNV_KOSTEN,
        VERKEHR_OEPNV_GRUND,
        
        VERKEHR_TAXI_ANTRAG,
        VERKEHR_TAXI_ANZAHL,
        VERKEHR_TAXI_KOSTEN,
        VERKEHR_TAXI_GRUND,
        
        VERKEHR_FAHRRAD_ANZAHL,
        VERKEHR_FAHRRAD_PAUSCHALE,
        
        VERKEHR_SONSTIGES_ART,
        VERKEHR_SONSTIGES_ANZAHL,
        VERKEHR_SONSTIGES_KOSTEN,
        VERKEHR_SONSTIGES_GRUND,
        
        // Accommodation States
        UEBERNACHTUNG,
        UEBERNACHTUNG_UNENTGELTLICH,
        UEBERNACHTUNG_UNTERKUNFT,
        UEBERNACHTUNG_UNTERKUNFT_VON,
        UEBERNACHTUNG_UNTERKUNFT_BIS,
        UEBERNACHTUNG_FRUEHSTUECK,
        UEBERNACHTUNG_FRUEHSTUECK_VON,
        UEBERNACHTUNG_FRUEHSTUECK_BIS,
        UEBERNACHTUNG_MITTAGESSEN,
        UEBERNACHTUNG_MITTAGESSEN_VON,
        UEBERNACHTUNG_MITTAGESSEN_BIS,
        UEBERNACHTUNG_ABENDESSEN,
        UEBERNACHTUNG_ABENDESSEN_VON,
        UEBERNACHTUNG_ABENDESSEN_BIS,
        UEBERNACHTUNG_HOTEL,
        UEBERNACHTUNG_HOTEL_ANZAHL,
        UEBERNACHTUNG_HOTEL1_NAME,
        UEBERNACHTUNG_HOTEL1_ORT,
        UEBERNACHTUNG_HOTEL1_VON,
        UEBERNACHTUNG_HOTEL1_BIS,
        UEBERNACHTUNG_HOTEL1_KOSTEN,
        UEBERNACHTUNG_HOTEL1_FRUEHSTUECK,
        UEBERNACHTUNG_HOTEL2_NAME,
        UEBERNACHTUNG_HOTEL2_ORT,
        UEBERNACHTUNG_HOTEL2_VON,
        UEBERNACHTUNG_HOTEL2_BIS,
        UEBERNACHTUNG_HOTEL2_KOSTEN,
        UEBERNACHTUNG_HOTEL2_FRUEHSTUECK,
        UEBERNACHTUNG_HOTEL_RECHNUNG,
        UEBERNACHTUNG_HOTEL_BUCHUNG,
        UEBERNACHTUNG_HOTEL_TMS,
        UEBERNACHTUNG_HOTEL_PREISGRENZE,
        UEBERNACHTUNG_HOTEL_PREISGRENZE_GRUND,
        UEBERNACHTUNG_HOTEL_DOPPELZIMMER,
        UEBERNACHTUNG_WOHNUNG,
        UEBERNACHTUNG_WOHNUNG_BETRETEN,
        UEBERNACHTUNG_WOHNUNG_VERLASSEN,
        UEBERNACHTUNG_WOHNUNG_GESCHAEFTSORT,
        UEBERNACHTUNG_WOHNUNG_GESCHAEFTSORT_BETRETEN,
        UEBERNACHTUNG_WOHNUNG_GESCHAEFTSORT_VERLASSEN,
        UEBERNACHTUNG_PRIVAT,
        UEBERNACHTUNG_BEFOERDERUNG,
        UEBERNACHTUNG_KOSTEN_ENTHALTEN,
        
        // Additional Information States
        ZUSATZ_LEISTUNG_DRITTE,
        ZUSATZ_LEISTUNG_DRITTE_HOEHE,
        ZUSATZ_NEBENTAETIGKEIT,
        ZUSATZ_ABSCHLAG,
        ZUSATZ_ABSCHLAG_HOEHE,
        ZUSATZ_ERGAENZENDE_AUSFUEHRUNGEN,
        ZUSATZ_BELEGE,
        ZUSATZ_MUENDLICH_GENEHMIGT,
        ZUSATZ_UNTERSCHRIFT_ORT,
        ZUSATZ_UNTERSCHRIFT_DATUM,
        
        // Final States
        ABSCHLUSS_PDF,
        DONE
    }
    
    /**
     * Main method to start the application
     */
    public static void main(String[] args) {
        try {
            launch(args);
        } catch (Exception e) {
            Platform.runLater(() -> {
                try {
                    new TravelExpenseChatbotGUI().start(new Stage());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }
}
}

