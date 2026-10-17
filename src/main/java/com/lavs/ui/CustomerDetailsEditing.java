package com.lavs.ui;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

import com.lavs.ui.CustomerRecordEditor.Kind;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import static com.lavs.ui.CustomerRecordEditor.Kind;

/** Renders retrieved details and keeps local editor state for standalone previews. */
final class CustomerDetailsEditing {
    private static final String CELL = "-fx-font-size: 12px; -fx-text-fill: #55558d;";
    private static final String ACTION = "-fx-background-color: transparent; -fx-text-fill: #747ba6; "
            + "-fx-font-size: 19px; -fx-padding: 4 10; -fx-min-width: 36; -fx-min-height: 36; -fx-cursor: hand;";
    private static final String BADGE = "-fx-background-color: #d9f3ec; -fx-background-radius: 12; "
            + "-fx-text-fill: #329e8a; -fx-font-size: 11px; -fx-padding: 2 16;";
    private final Node owner;
    private final Map<String, Label> labels;
    private final Map<Kind, GridPane> tables;
    private final VBox notes, additionalContacts, additionalEmployers;
    private final Map<String, Button> contactLinks;
    private final HBox primaryEmployerRow;
    private final Consumer<CustomersController.CustomerSummary> onChange;
    private final Map<String, State> sessions = new HashMap<>();
    private State state;
    private boolean readOnly;
    private com.lavs.business.CustomerService customerService;

    private static final class State {
        CustomersController.CustomerSummary summary;
        final Map<Kind, Map<String, String>> details = new EnumMap<>(Kind.class);
        final Map<Kind, List<Map<String, String>>> records = new EnumMap<>(Kind.class);
        boolean persisted;
        State(CustomersController.CustomerSummary customer) {
            summary = customer;
            details.put(Kind.PERSONAL, values("title", "", "firstName", firstName(customer.name()),
                    "lastName", lastName(customer.name()), "birthDate", "", "nationality", ""));
            details.put(Kind.CONTACT, values("email", customer.email(), "phone", customer.phone(), "address", ""));
            for (Kind kind : List.of(Kind.CONTACT_RECORD, Kind.EMPLOYMENT, Kind.LOAN, Kind.NOTE,
                    Kind.DOCUMENT, Kind.GUARANTOR)) records.put(kind, new ArrayList<>());

        }
        State(State source) {
            summary = source.summary;
            persisted = source.persisted;
            source.details.forEach((kind, values) -> details.put(kind, new LinkedHashMap<>(values)));
            source.records.forEach((kind, values) -> records.put(kind, values.stream()
                    .map(value -> (Map<String, String>) new LinkedHashMap<>(value))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new))));
        }
    }

    CustomerDetailsEditing(Node owner, Map<String, Label> labels, Map<Kind, GridPane> tables,
            VBox notes, VBox additionalContacts, VBox additionalEmployers, HBox primaryEmployerRow, Map<String, Button> contactLinks, Consumer<CustomersController.CustomerSummary> onChange) {
        this.owner = owner;
        this.labels = labels;
        this.tables = tables;
        this.notes = notes;
        this.additionalContacts = additionalContacts;
        this.additionalEmployers = additionalEmployers;
        this.contactLinks = contactLinks;
        this.primaryEmployerRow = primaryEmployerRow;
        this.onChange = onChange;
    }

    void setCustomerService(com.lavs.business.CustomerService customerService) {
        this.customerService = Objects.requireNonNull(customerService);
    }

    CustomersController.CustomerSummary select(CustomersController.CustomerSummary customer) {
        readOnly = false;
        state = sessions.computeIfAbsent(customer.customerId(), id -> new State(customer));
        render();
        return state.summary;
    }

    CustomersController.CustomerSummary select(com.lavs.model.CustomerDetails details) {
        var customer = details.customer();
        State loaded = new State(new CustomersController.CustomerSummary(customer.getCustomerId(),
                (text(customer.getFirstName()) + " " + text(customer.getLastName())).strip(), "", ""));
        loaded.details.put(Kind.PERSONAL, values("title", text(customer.getTitle()),
                "firstName", text(customer.getFirstName()), "lastName", text(customer.getLastName()),
                "birthDate", customer.getDateOfBirth() == null ? "" : CustomerRecordEditor.BIRTH_DATE.format(customer.getDateOfBirth()),
                "nationality", text(customer.getNationality())));
        var contacts = loaded.records.get(Kind.CONTACT_RECORD);
        details.emails().forEach(email -> contacts.add(values("id", id(email.getEmailId()), "contactType", "Email",
                "value", text(email.getEmailAddress()), "primary", Boolean.toString(email.isPrimary()))));
        details.phones().forEach(phone -> contacts.add(values("id", id(phone.getPhoneId()), "contactType", "Phone",
                "value", text(phone.getNumber()), "primary", Boolean.toString(phone.isPrimary()),
                "texts", Boolean.toString(phone.isSendTXT()))));
        details.addresses().forEach(address -> contacts.add(values("contactType", "Address", "value",
                java.util.stream.Stream.of(address.getStreet(), address.getSuburb(), address.getCity(), address.getPostCode(), address.getCountry())
                        .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(", ")),
                "id", id(address.getAddressId()), "street", text(address.getStreet()), "suburb", text(address.getSuburb()),
                "city", text(address.getCity()), "postCode", text(address.getPostCode()), "country", text(address.getCountry()),
                "primary", Boolean.toString(address.isMailing()))));
        for (String type : List.of("Email", "Phone", "Address")) {
            var entries = contacts.stream().filter(record -> type.equals(record.get("contactType"))).toList();
            var primary = entries.stream().filter(record -> Boolean.parseBoolean(record.get("primary")))
                    .findFirst().orElse(entries.isEmpty() ? null : entries.get(0));
            if (primary != null) {
                writePrimaryContact(loaded, type, primary);
                contacts.remove(primary);
            }
        }
        details.employers().forEach(employer -> {
            var record = values("id", id(employer.getEmployerId()), "employer", text(employer.getCompanyName()), "role", text(employer.getJobTitle()),
                    "employmentType", text(employer.getEmploymentType()), "income", Double.toString(employer.getIncome()));
            if (!loaded.details.containsKey(Kind.EMPLOYMENT)) loaded.details.put(Kind.EMPLOYMENT, record);
            else loaded.records.get(Kind.EMPLOYMENT).add(record);
        });
        var contact = loaded.details.get(Kind.CONTACT);
        loaded.summary = new CustomersController.CustomerSummary(customer.getCustomerId(), loaded.summary.name(),
                contact.get("phone"), contact.get("email"));
        // The mainframe's loans come first. Loans added this session are kept only if the
        // mainframe's list doesn't already have them, so a loan saved just now isn't shown twice.
        var loanRecords = loaded.records.get(Kind.LOAN);
        details.loans().forEach(loan -> loanRecords.add(loanRecord(loan)));
        details.documents().forEach(document -> loaded.records.get(Kind.DOCUMENT).add(values(
                "id", document.getDocumentId(), "fileName", document.getFileName(),
                "type", document.getDocumentType(), "date", document.getUploadDate().toString())));
        details.guarantors().forEach(guarantor -> loaded.records.get(Kind.GUARANTOR).add(values(
                "id", guarantor.getGuarantorId(),
                "firstName", "Customer " + guarantor.getGuarantorCustomerId(), "lastName", "",
                "relationship", guarantor.getRelationshipType(),
                "phone", "Not provided", "email", "Not provided", "status", "Not provided")));
        State previous = sessions.get(customer.getCustomerId());
        if (previous != null) {
            Set<String> knownIds = new HashSet<>();
            loanRecords.forEach(record -> knownIds.add(record.get("id")));
            previous.records.get(Kind.LOAN).stream()
                    .filter(record -> !knownIds.contains(record.get("id")))
                    .forEach(loanRecords::add);
        }
        state = loaded;
        sessions.put(customer.getCustomerId(), loaded);
        loaded.persisted = true;
        readOnly = false;
        render();
        return loaded.summary;
    }

    private static String text(String value) { return value == null ? "" : value; }
    private static String id(Long value) { return value == null ? "" : value.toString(); }

    // A loan read from the mainframe, in the form the Loans tab shows. The mainframe doesn't
    // keep whether a loan is a table or a reducing balance loan, so those show as Fixed or Floating.
    private static Map<String, String> loanRecord(com.lavs.model.Loan loan) {
        String type;
        if (loan.getType() == null) {
            type = loan.isFloating() ? "Floating" : "Fixed";
        } else {
            type = switch (loan.getType()) {
                case TABLE_LOAN -> "Table Loan";
                case REDUCING_BALANCE -> "Reducing Balance";
                case INTEREST_ONLY -> "Interest Only";
            };
        }
        String name = loan.getStatus().name();
        return values("id", loan.getLoanId(), "type", type,
                "amount", java.math.BigDecimal.valueOf(loan.getPrincipal()).toPlainString(),
                "rate", java.math.BigDecimal.valueOf(loan.getInterestRate())
                        .stripTrailingZeros().toPlainString(),
                "status", name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT));
    }

    void addSessionLoan(CustomersController.CustomerSummary customer, com.lavs.business.LoanSubmissionService.SavedLoan loan) {
        State target = sessions.computeIfAbsent(customer.customerId(), id -> new State(customer));
        var draft = loan.draft();
        target.records.get(Kind.LOAN).add(values("id", loan.loanId(), "type", draft.details.get("loanType"),
                "amount", draft.details.get("principal"), "rate", draft.details.get("interestRate"), "status", draft.details.get("status")));
        if (state == target) render();
    }

    void editDetails(Kind kind) {
        State target = state;
        CustomerRecordEditor.create(window(), kind, target.details.get(kind), false, saved -> {
            save(target, draft -> draft.details.put(kind, saved), () -> {});
        }).show();
    }

    private static String contactKey(String type) {
        return switch (type) { case "Email" -> "email"; case "Phone" -> "phone"; case "Address" -> "address"; default -> throw new IllegalArgumentException(type); };
    }

    void editContact(String type) { editContact(type, null, () -> {}); }

    private void editContact(String type, Map<String, String> record, Runnable afterSave) {
        State target = state;
        Map<String, String> original = record == null
                ? primaryContact(target, type)
                : new LinkedHashMap<>(record);
        if (record != null) original.put("primary", "false");
        Dialog<Map<String, String>> dialog = CustomerRecordEditor.create(window(), Kind.CONTACT_RECORD, original, false, saved -> {
            normaliseAddress(saved, original);
            int recordIndex = record == null ? -1 : target.records.get(Kind.CONTACT_RECORD).indexOf(record);
            save(target, draft -> {
                if (recordIndex < 0) writePrimaryContact(draft, type, saved);
                else {
                    Map<String, String> changed = draft.records.get(Kind.CONTACT_RECORD).get(recordIndex);
                    changed.clear();
                    changed.putAll(saved);
                    if (Boolean.parseBoolean(saved.get("primary"))) promoteContact(draft, type, changed);
                }
            }, afterSave);
        });
        VBox form = (VBox) ((ScrollPane) dialog.getDialogPane().getContent()).getContent();
        Button delete = deleteButton("Delete contact", () -> confirmDelete("contact", () -> {
            int recordIndex = record == null ? -1 : target.records.get(Kind.CONTACT_RECORD).indexOf(record);
            save(target, draft -> deleteContact(draft, type,
                    recordIndex < 0 ? null : draft.records.get(Kind.CONTACT_RECORD).get(recordIndex)), afterSave);
            dialog.close();
        }));
        form.getChildren().add(delete);
        dialog.setHeaderText("Edit " + type.toLowerCase(Locale.ROOT));
        dialog.show();
    }

    private void updateSummary(State target, boolean notify) {
        Map<String, String> contact = target.details.get(Kind.CONTACT);
        target.summary = new CustomersController.CustomerSummary(target.summary.customerId(),
                fullName(target.details.get(Kind.PERSONAL)), contact.get("phone"), contact.get("email"));
        if (notify && state == target) onChange.accept(target.summary);
    }

    private void promoteContact(State target, String type, Map<String, String> record) {
        Map<String, String> previous = primaryContact(target, type);
        writePrimaryContact(target, type, record);
        if (previous.getOrDefault("value", "").isBlank()) {
            target.records.get(Kind.CONTACT_RECORD).removeIf(entry -> entry == record);
        }
        else {
            record.clear();
            record.putAll(previous);
            record.put("primary", "false");
        }
    }

    private void deleteContact(State target, String type, Map<String, String> record) {
        if (record != null) target.records.get(Kind.CONTACT_RECORD).removeIf(entry -> entry == record);
        else {
            writePrimaryContact(target, type, values("contactType", type, "value", "",
                    "primary", "true", "texts", "false"));
            target.records.get(Kind.CONTACT_RECORD).stream().filter(entry -> type.equals(entry.get("contactType")))
                    .findFirst().ifPresent(entry -> promoteContact(target, type, entry));
        }
        updateSummary(target, false);
    }

    private Button deleteButton(String accessibleText, Runnable action) {
        Button button = new Button(accessibleText.equals("Delete contact") ? "Delete contact" : "");
        button.setGraphic(icon("M 1 4 H 15 M 5 4 V 1 H 11 V 4 M 3 4 L 4 18 H 12 L 13 4 M 6 7 V 15 M 10 7 V 15", "#b42318"));
        button.setMinSize(28, 28);
        button.setTooltip(new Tooltip(accessibleText));
        button.setAccessibleText(accessibleText);
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: #b42318; -fx-padding: 5; -fx-cursor: hand;");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void confirmDelete(String kind, Runnable action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete this " + kind + "?", ButtonType.CANCEL, ButtonType.OK);
        if (window() != null) alert.initOwner(window());
        alert.setHeaderText("Delete " + kind);
        alert.setOnHidden(event -> { if (alert.getResult() == ButtonType.OK) action.run(); });
        alert.show();
    }

    void deleteEmployer() {
        State target = state;
        confirmDelete("employer", () -> {
            save(target, draft -> {
                if (draft.records.get(Kind.EMPLOYMENT).isEmpty()) draft.details.remove(Kind.EMPLOYMENT);
                else draft.details.put(Kind.EMPLOYMENT, draft.records.get(Kind.EMPLOYMENT).remove(0));
            }, () -> {});
        });
    }

    void viewContacts(String type) {
        Dialog<Void> dialog = new Dialog<>();
        if (window() != null) dialog.initOwner(window());
        dialog.setTitle("All " + type.toLowerCase(Locale.ROOT) + " entries");
        dialog.setHeaderText("Customer " + type.toLowerCase(Locale.ROOT) + " entries");
        dialog.getDialogPane().setId("customerContactList");
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-background-color: white;");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox list = new VBox(8);
        list.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true); scroll.setPrefViewportHeight(280);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: white; -fx-background-color: white;");
        dialog.getDialogPane().setContent(scroll);
        populateContacts(type, list);
        dialog.show();
    }

    private void populateContacts(String type, VBox list) {
        list.getChildren().clear();
        String primary = state.details.get(Kind.CONTACT).getOrDefault(contactKey(type), "");
        if (!primary.isBlank()) list.getChildren().add(contactRow(type, primary, null, list));
        state.records.get(Kind.CONTACT_RECORD).stream().filter(record -> type.equals(record.get("contactType")))
                .forEach(record -> list.getChildren().add(contactRow(type, record.get("value"), record, list)));
        if (list.getChildren().isEmpty()) list.getChildren().add(label("No entries added."));
    }

    private HBox contactRow(String type, String value, Map<String, String> record, VBox list) {
        VBox text = new VBox(5, label(value));
        if (record == null) text.getChildren().add(label(type.equals("Address") ? "Mailing address" : "Primary"));
        text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
        Button edit = new Button(); edit.setStyle(ACTION);
        edit.setAccessibleText("Edit " + value);
        edit.setGraphic(icon("M 1 15 L 4 8 L 12 0 L 16 4 L 8 12 Z M 4 8 L 8 12 M 10 2 L 14 6", "#55558d"));
        edit.setOnAction(event -> editContact(type, record, () -> populateContacts(type, list)));
        VBox actions = new VBox(5, edit);
        actions.setVisible(!readOnly);
        actions.setManaged(!readOnly);
        if (record != null) {
            State target = state;
            int recordIndex = target.records.get(Kind.CONTACT_RECORD).indexOf(record);
            Button primary = new Button("Make primary");
            primary.setAccessibleText("Make primary " + value);
            primary.setOnAction(event -> {
                save(target, draft -> promoteContact(draft, type,
                        draft.records.get(Kind.CONTACT_RECORD).get(recordIndex)),
                        () -> populateContacts(type, list));
            });
            actions.getChildren().add(primary);
        }
        if (type.equals("Phone")) {
            CheckBox texts = new CheckBox("Send TXT messages");
            texts.setAccessibleText("Send TXT messages to " + value);
            texts.setSelected(Boolean.parseBoolean(record == null ? state.details.get(Kind.CONTACT).getOrDefault("texts", "false") : record.getOrDefault("texts", "false")));
            texts.setOnAction(event -> {
                State target = state;
                int recordIndex = record == null ? -1 : target.records.get(Kind.CONTACT_RECORD).indexOf(record);
                save(target, draft -> {
                    if (recordIndex < 0) draft.details.get(Kind.CONTACT).put("texts", Boolean.toString(texts.isSelected()));
                    else draft.records.get(Kind.CONTACT_RECORD).get(recordIndex)
                            .put("texts", Boolean.toString(texts.isSelected()));
                }, () -> populateContacts(type, list));
            });
            texts.setDisable(readOnly);
            text.getChildren().add(texts);
        }
        State target = state;
        int recordIndex = record == null ? -1 : target.records.get(Kind.CONTACT_RECORD).indexOf(record);
        actions.getChildren().add(deleteButton("Delete " + value, () -> confirmDelete("contact", () -> {
            save(target, draft -> deleteContact(draft, type,
                    recordIndex < 0 ? null : draft.records.get(Kind.CONTACT_RECORD).get(recordIndex)),
                    () -> populateContacts(type, list));
        })));
        HBox row = new HBox(12, text, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 12 14; -fx-background-color: #f5f6ff; -fx-background-radius: 8; -fx-border-color: #dce2ff; -fx-border-radius: 8;");
        return row;
    }

    void add(Kind kind) { editRecord(kind, null); }

    private void editRecord(Kind kind, Map<String, String> record) {
        State target = state;
        Map<String, String> editorDraft = record == null ? new LinkedHashMap<>() : record;
        if (record == null && (kind == Kind.NOTE || kind == Kind.DOCUMENT)) editorDraft.put("date", LocalDate.now().toString());
        int recordIndex = record == null ? -1 : target.records.get(kind).indexOf(record);
        CustomerRecordEditor.create(window(), kind, editorDraft, record == null, saved -> {
            save(target, draft -> {
                if (recordIndex < 0 && kind == Kind.EMPLOYMENT && !draft.details.containsKey(kind)) draft.details.put(kind, saved);
                else if (recordIndex < 0) draft.records.get(kind).add(saved);
                else {
                    Map<String, String> changed = draft.records.get(kind).get(recordIndex);
                    changed.clear();
                    changed.putAll(saved);
                }
                if (kind == Kind.CONTACT_RECORD) {
                    String type = saved.get("contactType");
                    Map<String, String> changed = recordIndex < 0 ? saved : draft.records.get(kind).get(recordIndex);
                    normaliseAddress(changed, editorDraft);
                    if (Boolean.parseBoolean(saved.get("primary")) || draft.details.get(Kind.CONTACT).getOrDefault(contactKey(type), "").isBlank())
                        promoteContact(draft, type, changed);
                }
            }, () -> {});
        }, saved -> kind == Kind.LOAN && target.records.get(kind).stream()
                .anyMatch(other -> other != record && other.get("id").equalsIgnoreCase(saved.get("id")))
                ? Map.of("id", "This loan ID already exists for the customer.") : Map.of()).show();
    }

    private void save(State target, Consumer<State> change, Runnable afterSave) {
        if (state != target) return;
        State draft = new State(target);
        change.accept(draft);
        updateSummary(draft, false);
        if (!target.persisted) {
            state = draft;
            sessions.put(draft.summary.customerId(), draft);
            render();
            onChange.accept(draft.summary);
            afterSave.run();
            return;
        }
        if (customerService == null) {
            showSaveError(new IllegalStateException("Customer editing is not connected"));
            return;
        }
        try {
            com.lavs.model.CustomerDetails requested = toCustomerDetails(draft);
            customerService.update(requested.customer(), requested.addresses(), requested.phones(),
                    requested.emails(), requested.employers());
            CustomersController.CustomerSummary updated = select(customerService.getById(
                    new com.lavs.model.CustomerId(draft.summary.customerId())));
            onChange.accept(updated);
            afterSave.run();
        } catch (RuntimeException failure) {
            showSaveError(failure);
        }
    }

    private void showSaveError(RuntimeException failure) {
        Alert alert = new Alert(Alert.AlertType.ERROR,
                "Your changes were not saved. Check the details and connection, then try again.", ButtonType.OK);
        if (window() != null) alert.initOwner(window());
        alert.setHeaderText("Customer changes could not be saved");
        alert.setTitle("Save failed");
        alert.getDialogPane().setId("customerSaveError");
        alert.show();
    }

    private com.lavs.model.CustomerDetails toCustomerDetails(State source) {
        String customerId = source.summary.customerId();
        Map<String, String> personal = source.details.get(Kind.PERSONAL);
        com.lavs.model.Customer customer = new com.lavs.model.Customer(customerId,
                personal.get("title"), personal.get("firstName"), personal.get("lastName"),
                LocalDate.parse(personal.get("birthDate"), CustomerRecordEditor.BIRTH_DATE),
                personal.get("nationality"));
        List<Map<String, String>> contacts = new ArrayList<>();
        for (String type : List.of("Email", "Phone", "Address")) {
            Map<String, String> primary = primaryContact(source, type);
            if (!primary.getOrDefault("value", "").isBlank()) contacts.add(primary);
        }
        contacts.addAll(source.records.get(Kind.CONTACT_RECORD));
        List<com.lavs.model.Address> addresses = contacts.stream()
                .filter(record -> "Address".equals(record.get("contactType")))
                .map(record -> address(record, customerId)).toList();
        List<com.lavs.model.Phone> phones = contacts.stream()
                .filter(record -> "Phone".equals(record.get("contactType")))
                .map(record -> new com.lavs.model.Phone(longId(record.get("id")), customerId,
                        record.get("value"), Boolean.parseBoolean(record.get("primary")),
                        Boolean.parseBoolean(record.getOrDefault("texts", "false")))).toList();
        List<com.lavs.model.Email> emails = contacts.stream()
                .filter(record -> "Email".equals(record.get("contactType")))
                .map(record -> new com.lavs.model.Email(longId(record.get("id")), customerId,
                        record.get("value"), Boolean.parseBoolean(record.get("primary")))).toList();
        List<Map<String, String>> employment = new ArrayList<>();
        if (source.details.containsKey(Kind.EMPLOYMENT)) employment.add(source.details.get(Kind.EMPLOYMENT));
        employment.addAll(source.records.get(Kind.EMPLOYMENT));
        List<com.lavs.model.Employer> employers = employment.stream()
                .map(record -> new com.lavs.model.Employer(longId(record.get("id")), customerId,
                        record.get("employer"), record.get("role"), Double.parseDouble(record.get("income")),
                        record.get("employmentType"))).toList();
        return new com.lavs.model.CustomerDetails(customer, addresses, phones, emails, employers);
    }

    private static com.lavs.model.Address address(Map<String, String> record, String customerId) {
        String structured = addressText(record);
        boolean unchanged = structured.equals(record.getOrDefault("value", ""));
        return new com.lavs.model.Address(longId(record.get("id")), customerId,
                unchanged ? record.getOrDefault("street", "") : record.get("value"),
                unchanged ? record.getOrDefault("suburb", "") : "",
                unchanged ? record.getOrDefault("city", "") : "",
                unchanged ? record.getOrDefault("postCode", "") : "",
                unchanged ? record.getOrDefault("country", "") : "",
                Boolean.parseBoolean(record.get("primary")));
    }

    private static Long longId(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    private static String addressText(Map<String, String> record) {
        return java.util.stream.Stream.of("street", "suburb", "city", "postCode", "country")
                .map(key -> record.getOrDefault(key, "")).filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static void normaliseAddress(Map<String, String> saved, Map<String, String> original) {
        if (!"Address".equals(saved.get("contactType"))) return;
        if (!Objects.equals(saved.get("value"), original.get("value"))) {
            saved.put("street", saved.get("value"));
            for (String key : List.of("suburb", "city", "postCode", "country")) saved.put(key, "");
        }
    }

    private static Map<String, String> primaryContact(State source, String type) {
        Map<String, String> contact = source.details.get(Kind.CONTACT);
        Map<String, String> result = values("contactType", type,
                "value", contact.getOrDefault(contactKey(type), ""), "primary", "true",
                "texts", type.equals("Phone") ? contact.getOrDefault("texts", "false") : "false");
        for (String key : List.of("id", "street", "suburb", "city", "postCode", "country"))
            result.put(key, contact.getOrDefault(primaryKey(type, key), ""));
        return result;
    }

    private static void writePrimaryContact(State target, String type, Map<String, String> record) {
        Map<String, String> contact = target.details.get(Kind.CONTACT);
        contact.put(contactKey(type), record.getOrDefault("value", ""));
        if (type.equals("Phone")) contact.put("texts", record.getOrDefault("texts", "false"));
        for (String key : List.of("id", "street", "suburb", "city", "postCode", "country"))
            contact.put(primaryKey(type, key), record.getOrDefault(key, ""));
    }

    private static String primaryKey(String type, String key) {
        return type.toLowerCase(Locale.ROOT) + "." + key;
    }

    private javafx.stage.Window window() {
        return owner.getScene() == null ? null : owner.getScene().getWindow();
    }

    private void render() {
        Map<String, String> personal = state.details.get(Kind.PERSONAL);
        Map<String, String> contact = state.details.get(Kind.CONTACT);
        Map<String, String> employment = state.details.get(Kind.EMPLOYMENT);
        primaryEmployerRow.setVisible(employment != null);
        primaryEmployerRow.setManaged(employment != null);
        set("customerHeading", fullName(personal));
        set("firstNameValue", personal.get("firstName"));
        set("lastNameValue", personal.get("lastName"));
        set("titleValue", personal.get("title"));
        String birthDate = personal.get("birthDate");
        set("birthDateValue", birthDate == null || birthDate.isBlank() ? "Not provided"
                : CustomerRecordEditor.DISPLAY_DATE.format(LocalDate.parse(birthDate, CustomerRecordEditor.BIRTH_DATE)));
        set("nationalityValue", personal.get("nationality"));
        set("emailValue", contactDisplay(contact.get("email"), "No email address"));
        set("phoneValue", contactDisplay(contact.get("phone"), "No phone number"));
        set("addressValue", contactDisplay(contact.get("address"), "No mailing address"));
        set("employerNameValue", employment == null ? "" : employment.get("employer"));
        set("employmentSummaryValue", employment == null ? "" : employment.get("role") + "  \u2022  " + employment.get("employmentType") + "  \u2022  " + money(employment.get("income")) + " / year");
        tables.forEach(this::renderTable);
        renderNotes();
        additionalContacts.getChildren().clear();
        additionalContacts.setVisible(false);
        additionalContacts.setManaged(false);
        contactLinks.forEach((type, button) -> {
            long count = state.records.get(Kind.CONTACT_RECORD).stream().filter(record -> type.equals(record.get("contactType"))).count();
            if (!state.details.get(Kind.CONTACT).getOrDefault(contactKey(type), "").isBlank()) count++;
            button.setVisible(count > 1);
            button.setManaged(count > 1);
        });
        renderAdditional(Kind.EMPLOYMENT, additionalEmployers);
    }

    private static String contactDisplay(String value, String emptyMessage) {
        return value == null || value.isBlank() ? emptyMessage : value;
    }

    private static String firstName(String name) {
        String[] parts = name.strip().split("\\s+", 2);
        return parts[0];
    }
    private static String lastName(String name) {
        String[] parts = name.strip().split("\\s+", 2);
        return parts.length == 2 ? parts[1] : "";
    }
    private static String fullName(Map<String, String> record) {
        return (record.getOrDefault("firstName", "") + " " + record.getOrDefault("lastName", "")).strip();
    }
    private void renderAdditional(Kind kind, VBox container) {
        container.getChildren().clear();
        for (Map<String, String> record : state.records.get(kind)) {
            String title = kind == Kind.CONTACT_RECORD ? record.get("contactType") : record.get("employer");
            String description = kind == Kind.CONTACT_RECORD ? record.get("value")
                    : record.get("role") + "  \u2022  " + record.get("employmentType") + "  \u2022  " + money(record.get("income")) + " / year";
            Label heading = label(title);
            Label summary = label(description);
            if (kind == Kind.EMPLOYMENT) {
                heading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #20205e;");
                summary.setStyle("-fx-font-size: 12px; -fx-text-fill: #626499;");
            }
            VBox text = new VBox(6, heading, summary);
            text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
            Button edit = new Button();
            edit.setAccessibleText("Edit " + title);
            edit.setStyle(((Button) primaryEmployerRow.getChildren().get(1)).getStyle());
            edit.setGraphic(icon("M 1 15 L 4 8 L 12 0 L 16 4 L 8 12 Z M 4 8 L 8 12 M 10 2 L 14 6", "#55558d"));
            ((SVGPath) edit.getGraphic()).setStrokeWidth(1.3);
            edit.setOnAction(event -> editRecord(kind, record));
            State target = state;
            int recordIndex = target.records.get(kind).indexOf(record);
            Button delete = deleteButton("Delete " + title, () -> confirmDelete("employer", () -> {
                save(target, draft -> draft.records.get(kind).remove(recordIndex), () -> {});
            }));
            delete.setStyle(((Button) primaryEmployerRow.getChildren().get(2)).getStyle());
            ((SVGPath) delete.getGraphic()).setStrokeWidth(1.5);
            edit.setVisible(!readOnly); edit.setManaged(!readOnly);
            delete.setVisible(!readOnly); delete.setManaged(!readOnly);
            HBox row = new HBox(primaryEmployerRow.getSpacing(), text, edit, delete);
            row.setAlignment(primaryEmployerRow.getAlignment());
            row.setStyle(primaryEmployerRow.getStyle());
            container.getChildren().add(row);
        }
        if (kind == Kind.EMPLOYMENT && state.details.get(kind) == null && container.getChildren().isEmpty())
            container.getChildren().add(label("No employers added."));
        container.setManaged(!container.getChildren().isEmpty());
        container.setVisible(!container.getChildren().isEmpty());
    }

    private void set(String id, String value) { labels.get(id).setText(value); }
    private static Map<String, String> values(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }
    private static String money(String value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ENGLISH);
        format.setMaximumFractionDigits(2);
        return "$" + format.format(new java.math.BigDecimal(value));
    }
    private static Label label(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setStyle(CELL);
        return label;
    }
    private static SVGPath icon(String path, String colour) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setFill(javafx.scene.paint.Color.TRANSPARENT);
        icon.setStroke(javafx.scene.paint.Color.web(colour));
        icon.setStrokeWidth(1.7);
        return icon;
    }
    private static Pane avatar() {
        Pane avatar = new Pane();
        avatar.setMinSize(36, 36); avatar.setPrefSize(36, 36); avatar.setMaxSize(36, 36);
        Circle background = new Circle(18, 18, 18, javafx.scene.paint.Color.web("#efedff"));
        Circle head = new Circle(18, 13, 6, javafx.scene.paint.Color.web("#393a7b"));
        SVGPath body = new SVGPath();
        body.setContent("M 8 28 C 8 16 28 16 28 28 Q 18 33 8 28 Z");
        body.setFill(javafx.scene.paint.Color.web("#393a7b"));
        avatar.getChildren().addAll(background, head, body);
        return avatar;
    }
    private void renderTable(Kind kind, GridPane table) {
        table.getChildren().removeIf(node -> GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) > 0);
        if (table.getRowConstraints().size() > 1) table.getRowConstraints().subList(1, table.getRowConstraints().size()).clear();
        List<Map<String, String>> records = state.records.get(kind);
        int columns = table.getColumnConstraints().size();
        for (int index = 0; index < records.size(); index++) {
            Map<String, String> record = records.get(index);
            int row = index + 1;
            table.getRowConstraints().add(new RowConstraints(kind == Kind.GUARANTOR ? 60 : 52));
            Region divider = new Region();
            divider.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            divider.setStyle("-fx-border-color: transparent transparent #edf0ff transparent; -fx-border-width: 0 0 1 0;");
            GridPane.setMargin(divider, new Insets(0, 16, 0, 16));
            table.add(divider, 0, row, columns, 1);
            if (kind == Kind.LOAN) {
                Label id = label(record.get("id")); id.setPadding(new Insets(0, 0, 0, 16));
                table.add(id, 0, row); table.add(label(record.get("type")), 1, row);
                table.add(label(money(record.get("amount"))), 2, row);
                table.add(label(record.get("rate") + "%"), 3, row);
                table.add(status(record.get("status")), 4, row);
                table.add(actions(kind, record, "Actions for loan " + record.get("id")), 5, row);
            } else if (kind == Kind.DOCUMENT) {
                String colour = switch (record.get("type")) { case "Identity" -> "#7666db"; case "Address" -> "#42bda6"; default -> "#ed4d94"; };
                HBox name = new HBox(16, icon("M 1 0 H 10 L 15 5 V 20 H 1 Z M 10 0 V 5 H 15 M 5 9 H 11 M 5 12 H 11 M 5 15 H 9", colour), label(record.get("fileName")));
                name.setAlignment(Pos.CENTER_LEFT); name.setPadding(new Insets(0, 24, 0, 24)); name.setMinWidth(0);
                table.add(name, 0, row); table.add(label(record.get("type")), 1, row);
                table.add(label(CustomerRecordEditor.DISPLAY_DATE.format(LocalDate.parse(record.get("date")))), 2, row);
                table.add(actions(kind, record, "Actions for " + record.get("fileName")), 3, row);
            } else {
                HBox name = new HBox(12, avatar(), label(fullName(record)));
                name.setAlignment(Pos.CENTER_LEFT); name.setPadding(new Insets(0, 20, 0, 20)); name.setMinWidth(0);
                table.add(name, 0, row); table.add(label(record.get("relationship")), 1, row);
                table.add(label(record.get("phone")), 2, row); table.add(label(record.get("email")), 3, row);
                table.add(status(record.get("status")), 4, row);
                table.add(actions(kind, record, "Actions for guarantor " + fullName(record)), 5, row);
            }
        }
        if (records.isEmpty()) {
            Label empty = label("No " + (kind == Kind.LOAN ? "loans" : kind == Kind.DOCUMENT ? "documents" : "guarantors") + (readOnly ? " loaded." : " added."));
            empty.setPadding(new Insets(20)); table.add(empty, 0, 1, columns, 1);
        }
    }
    private static Label status(String text) {
        Label label = label(text);
        label.setStyle(text.equals("Active") ? BADGE : BADGE.replace("#d9f3ec", "#efedff").replace("#329e8a", "#6255cc"));
        return label;
    }
    private Button actions(Kind kind, Map<String, String> record, String accessibleText) {
        Button button = new Button("\u22EE"); button.setStyle(ACTION); button.setAccessibleText(accessibleText);
        if (state.persisted && (kind == Kind.LOAN || kind == Kind.DOCUMENT || kind == Kind.GUARANTOR)) {
            button.setDisable(true);
            button.setTooltip(new Tooltip("This record type is not editable here yet."));
            return button;
        }
        ContextMenu menu = new ContextMenu();
        MenuItem edit = new MenuItem("Edit " + kind.title);
        edit.setOnAction(action -> editRecord(kind, record));
        MenuItem remove = new MenuItem("Remove " + kind.title);
        remove.setOnAction(action -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Remove this " + kind.title + " from the customer?", ButtonType.CANCEL, ButtonType.OK);
            if (window() != null) confirmation.initOwner(window());
            confirmation.setHeaderText("Remove " + kind.title);
            confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> { state.records.get(kind).remove(record); render(); });
        });
        menu.getItems().addAll(edit, remove);
        button.setContextMenu(menu);
        button.setOnAction(event -> menu.show(button, javafx.geometry.Side.BOTTOM, 0, 0));
        return button;
    }
    private void renderNotes() {
        if (notes.getChildren().size() > 1) notes.getChildren().subList(1, notes.getChildren().size()).clear();
        for (Map<String, String> record : state.records.get(Kind.NOTE)) {
            StackPane icon = new StackPane(icon("M 1 0 H 9 L 13 4 V 16 H 1 Z M 9 0 V 4 H 13 M 4 7 H 10 M 4 10 H 10 M 4 13 H 8", "#6255cc"));
            icon.setMinSize(36, 36); icon.setPrefSize(36, 36); icon.setMaxSize(36, 36);
            icon.setStyle("-fx-background-color: #efedff; -fx-background-radius: 18;");
            Label title = label(record.get("title")); title.setStyle(CELL + "-fx-font-weight: bold;");
            VBox text = new VBox(5, title, label(record.get("text"))); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
            VBox meta = new VBox(5, label(CustomerRecordEditor.DISPLAY_DATE.format(LocalDate.parse(record.get("date")))), label("By " + record.get("author")));
            meta.setMinWidth(145); meta.setPrefWidth(145);
            HBox row = new HBox(16, icon, text, meta, actions(Kind.NOTE, record, "Actions for " + record.get("title") + " note"));
            row.setAlignment(Pos.CENTER_LEFT); row.setMinHeight(74);
            row.setStyle("-fx-background-color: white; -fx-border-color: #e2e9fc; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14 18;");
            notes.getChildren().add(row);
        }
        if (state.records.get(Kind.NOTE).isEmpty()) notes.getChildren().add(label("No notes added."));
    }
}
