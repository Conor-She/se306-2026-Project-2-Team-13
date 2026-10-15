package com.lavs.ui;

import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DashboardControllerTest {
    @Test void addCustomerUsesConfiguredRegistrationService() throws Exception {
        JavaFxTestSupport.startToolkit();
        var service = org.mockito.Mockito.mock(com.lavs.business.CustomerRegistrationService.class);
        org.mockito.Mockito.when(service.validate(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("fxml/dashboard.fxml"));
            Parent root = loader.load();
            new Scene(root, 900, 560);
            DashboardController controller = loader.getController();
            controller.setRegistrationServiceFactory(() -> service);
            CustomersController customers = (CustomersController) loader.getNamespace().get("customersViewController");
            customers.openAddCustomer();
            return null;
        });
        org.mockito.Mockito.verify(service, org.mockito.Mockito.atLeastOnce()).validate(org.mockito.ArgumentMatchers.any());
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        return JavaFxTestSupport.run(action);
    }

    @Test void navigationAndUsernameWorkWithoutBackend() throws Exception {
        JavaFxTestSupport.startToolkit();
        boolean[] loggedOut = {false};
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("fxml/dashboard.fxml"));
        Parent root = fx(() -> {
            Parent view = loader.load();
            new Scene(view, 900, 560);
            DashboardController controller = loader.getController();
            controller.setRegistrationServiceFactory(com.lavs.business.StubCustomerRegistrationService::new);
            controller.configure("Alyza", () -> loggedOut[0] = true);
            view.applyCss();
            view.layout();
            return view;
        });
        assertTrue(fx(() -> root.lookupAll(".heading").stream().anyMatch(node -> ((Label) node).getText().equals("Welcome, Alyza"))));
        assertTrue(fx(() -> ((Label) root.lookup("#profileName")).getText().contains("Alyza")));
        fx(() -> {
            VBox searchCard = (VBox) root.lookup("#searchCard");
            VBox addCard = (VBox) root.lookup("#addCustomerCard");
            VBox home = (VBox) root.lookup("#homeView");
            StackPane content = (StackPane) root.lookup("#content");
            for (int width : new int[] {320, 480, 640, 855, 900, 1200, 1920, 2560}) {
                root.resize(width, 560);
                root.applyCss(); root.layout();
                // A second pass settles width-dependent wrapping and the scroll viewport.
                root.requestLayout(); root.layout();
                assertEquals(content.getInsets().getTop(), home.getLayoutY(), 1);
                assertTrue(searchCard.getWidth() > 0);
                assertEquals(searchCard.getWidth(), addCard.getWidth(), 1);
                for (VBox card : new VBox[] {searchCard, addCard}) {
                    assertTrue(card.getLayoutX() + card.getWidth() <= home.getWidth() + 1,
                            "Card overflows at window width " + width);
                    for (javafx.scene.Node child : card.getChildren()) {
                        assertTrue(child.getLayoutY() + child.getLayoutBounds().getHeight()
                                <= card.getHeight() - card.getInsets().getBottom() + 1,
                                "Card content overflows at window width " + width);
                    }
                }
                if (home.getWidth() >= 624) {
                    assertEquals(searchCard.getLayoutY(), addCard.getLayoutY(), 1);
                    assertEquals(home.getWidth(), searchCard.getWidth() + addCard.getWidth() + 24, 2);
                } else {
                    assertEquals(home.getWidth(), searchCard.getWidth(), 1);
                    assertTrue(addCard.getLayoutY() >= searchCard.getHeight() + 24 - 1);
                }
            }
            double smallFont = ((Label) searchCard.getChildren().get(1)).getFont().getSize();
            root.resize(900, 560);
            root.applyCss(); root.layout();
            double compactFont = ((Label) searchCard.getChildren().get(1)).getFont().getSize();
            root.resize(1920, 1080);
            for (int pass = 0; pass < 4; pass++) {
                root.applyCss(); root.requestLayout(); root.layout();
            }
            assertTrue(((Label) searchCard.getChildren().get(1)).getFont().getSize() > compactFont);
            assertTrue(smallFont >= compactFont);
            javafx.scene.layout.FlowPane cards = (javafx.scene.layout.FlowPane) root.lookup("#homeCards");
            ScrollPane scroll = (ScrollPane) root.lookup("#dashboardScroll");
            double bottom = home.getLayoutY() + cards.getLayoutY() + searchCard.getHeight();
            assertTrue(bottom <= scroll.getViewportBounds().getHeight() - content.getInsets().getBottom() + 3,
                    "Home cards should fit within the large viewport");
            root.resize(900, 560);
            root.applyCss(); root.layout();
            return null;
        });
        fx(() -> {
            ((Button) root.lookup("#loansNav")).fire();
            assertTrue(root.lookup("#loansView").isVisible());
            assertFalse(root.lookup("#homeView").isManaged());
            ((Button) root.lookup("#customersNav")).fire();
            assertTrue(root.lookup("#customersView").isVisible());
            assertTrue(root.lookup("#customersView").isManaged());
            assertFalse(root.lookup("#loansView").isVisible());
            assertTrue(root.lookup("#customersNav").getStyleClass().contains("selected"));
            root.applyCss(); root.layout();
            TextField search = (TextField) root.lookup("#searchField");
            @SuppressWarnings("unchecked")
            TableView<CustomersController.CustomerSummary> table =
                    (TableView<CustomersController.CustomerSummary>) root.lookup("#customerTable");
            assertEquals("Search customers", search.getPromptText());
            assertTrue(table.getItems().isEmpty(), "An unconnected screen must not display sample customers");
            assertEquals("Enter a customer name or ID to search", ((Label) table.getPlaceholder()).getText());
            assertNull(root.lookup(".list-view"));
            return null;
        });
        fx(() -> {
            ((VBox) root.lookup("#addCustomerCard")).getChildren().stream()
                .filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow().fire();
            return null;
        });
        fx(() -> {
            root.applyCss(); root.layout();
            assertSame(root.lookup("#title"), root.getScene().getFocusOwner());
            VBox wizard = (VBox) root.lookup(".customer-wizard");
            assertNotNull(wizard);
            ScrollPane customerScroll = (ScrollPane) wizard.lookup("#customerScroll");
            // Descendant lookups also find scrollbars inside controls such as ComboBox.
            // Only the wizard scroll pane's own bars receive its custom appearance.
            var wizardBars = customerScroll.lookupAll(".scroll-bar").stream()
                    .filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                    .filter(bar -> bar.getParent() == customerScroll).toList();
            assertEquals(2, wizardBars.size(), "Expected the wizard's horizontal and vertical scrollbars");
            for (ScrollBar bar : wizardBars) {
                var thumbs = bar.lookupAll(".thumb");
                assertFalse(thumbs.isEmpty(), "Scrollbar thumb was not created");
                assertTrue(thumbs.stream().allMatch(node -> node.getStyle().contains("#9693ca")),
                        "Wizard scrollbar thumbs should use the custom colour");
                for (String selector : new String[] {".increment-button", ".decrement-button"}) {
                    var buttons = bar.lookupAll(selector);
                    assertFalse(buttons.isEmpty(), "Scrollbar part was not created: " + selector);
                    assertTrue(buttons.stream().allMatch(node -> node.getStyle().contains("-fx-opacity: 0")),
                            "Wizard scrollbar arrows should be hidden: " + selector);
                }
            }
            assertTrue(wizard.lookupAll(".button").stream().map(Button.class::cast).noneMatch(b -> b.getText().equals("Skip")));
            wizardButton(wizard, "Next").fire();
            wizard.applyCss(); wizard.layout();
            assertTrue(wizard.lookupAll(".field-error").stream().anyMatch(javafx.scene.Node::isVisible));
            ((ComboBox<String>) wizard.lookup("#title")).setValue("Ms");
            wizardField(wizard, "Nationality *").setText("New Zealand");
            wizardField(wizard, "First name *").setText("Amelia");
            wizardField(wizard, "Last name *").setText("Adams");
            TextField dob = wizardField(wizard, "Date of birth * (dd-mm-yyyy)");
            dob.appendText("1");
            dob.appendText("4");
            assertEquals("14-", dob.getText());
            dob.appendText("0");
            dob.appendText("3");
            assertEquals("14-03-", dob.getText());
            dob.appendText("1990");
            assertEquals("14-03-1990", dob.getText());
            dob.positionCaret(6);
            dob.deletePreviousChar();
            assertEquals("14-01-990", dob.getText());
            dob.selectAll();
            dob.replaceSelection("14031990");
            assertEquals("14-03-1990", dob.getText());
            dob.appendText("1");
            assertEquals("14-03-1990", dob.getText());
            dob.setText("14-03-1990");
            assertEquals("14-03-1990", dob.getText());
            wizardButton(wizard, "Next").fire();
            assertNotNull(wizard.lookup("#contactPane"));
            for (String kind : new String[] {"address", "phone", "email"}) {
                wizardButton(wizard, "+ Add " + kind).fire();
                wizardButton(wizard, "Done").fire();
                wizard.applyCss(); wizard.layout();
                assertTrue(((Parent) wizard.lookup("#" + (kind.equals("address") ? "address" : kind) + "Rows"))
                    .lookupAll(".button").isEmpty());
            }
            // Empty editors are discarded automatically by either navigation action.
            for (String action : new String[] {"Next", "Skip"}) {
                for (String kind : new String[] {"address", "phone", "email"}) {
                    wizardButton(wizard, "+ Add " + kind).fire();
                    for (String other : new String[] {"address", "phone", "email"}) {
                        wizardButton(wizard, "+ Add " + other).fire();
                        assertEquals(1, wizard.lookupAll(".button").stream().map(Button.class::cast)
                            .filter(button -> button.getText().equals("Done")).count());
                    }
                    wizardButton(wizard, action).fire();
                    assertNotNull(wizard.lookup("#employmentPane"));
                    wizardButton(wizard, "Back").fire();
                    assertTrue(wizard.lookupAll(".text-field").isEmpty());
                }
            }
            wizardButton(wizard, "+ Add address").fire();
            wizardField(wizard, "Street address *").setText("12 Queen St");
            wizardButton(wizard, "+ Add email").fire();
            assertEquals(1, ((Parent) wizard.lookup("#addressRows")).lookupAll(".text-field").stream()
                .map(TextField.class::cast).filter(field -> field.getText().equals("12 Queen St")).count());
            assertTrue(((Parent) wizard.lookup("#emailRows")).lookupAll(".text-field").isEmpty());
            wizardButton(wizard, "Done").fire();
            assertNotNull(wizard.lookup("#contactPane"));
            assertTrue(wizard.lookupAll(".field-error").stream().anyMatch(javafx.scene.Node::isVisible));
            wizardButton(wizard, "Skip").fire();
            assertNotNull(wizard.lookup("#contactPane"));

            wizardField(wizard, "Suburb *").setText("Central");
            wizardField(wizard, "Postcode *").setText("1010");
            wizardField(wizard, "Country *").setText("New Zealand");
            wizardField(wizard, "City *").setText("Auckland");
            wizardButton(wizard, "+ Add phone").fire();
            assertTrue(((Parent) wizard.lookup("#phoneRows")).lookupAll(".text-field").isEmpty());
            assertTrue(((Label) wizard.lookup("#message")).getText().contains("Click Done"));
            wizardButton(wizard, "Done").fire();
            wizardButton(wizard, "+ Add phone").fire();
            TextField phone = wizardField(wizard, "Phone number *");
            phone.appendText("abc");
            assertEquals("", phone.getText());
            phone.appendText("+64 (21) 555-0101");
            assertEquals("+64 (21) 555-0101", phone.getText());
            phone.appendText("letters");
            assertEquals("+64 (21) 555-0101", phone.getText());
            phone.selectAll();
            phone.replaceSelection("021abc5550101");
            assertEquals("+64 (21) 555-0101", phone.getText());
            phone.selectAll();
            phone.replaceSelection("021 555 0101");
            phone.positionCaret(3);
            phone.insertText(3, "+");
            assertEquals("021 555 0101", phone.getText());
            phone.clear();
            assertEquals("", phone.getText());
            phone.setText("021 555 0101");
            wizardButton(wizard, "+ Add email").fire();
            assertTrue(((Parent) wizard.lookup("#emailRows")).lookupAll(".text-field").isEmpty());
            wizardButton(wizard, "Done").fire();
            wizardButton(wizard, "+ Add email").fire();
            wizardField(wizard, "Email address *").setText("invalid");
            wizardButton(wizard, "Next").fire();
            assertTrue(wizard.lookupAll(".field-error").stream().anyMatch(javafx.scene.Node::isVisible));
            wizardField(wizard, "Email address *").setText("amelia@example.com");
            wizardButton(wizard, "Back").fire();
            assertEquals("Amelia", wizardField(wizard, "First name *").getText());
            wizardButton(wizard, "Next").fire();
            assertEquals("amelia@example.com", wizardField(wizard, "Email address *").getText());
            assertTrue(((Parent) wizard.lookup("#addressRows")).lookupAll(".label").stream()
                .map(Label.class::cast).anyMatch(label -> label.getText().contains("12 Queen St")));
            assertTrue(((Parent) wizard.lookup("#phoneRows")).lookupAll(".label").stream()
                .map(Label.class::cast).anyMatch(label -> label.getText().contains("021 555 0101")));
            wizardButton(wizard, "Next").fire();
            assertNotNull(wizard.lookup("#employmentPane"));
            assertTrue(wizard.lookup("#guarantorNote").isVisible());
            wizardButton(wizard, "+ Add employer").fire();
            wizardButton(wizard, "+ Add employer").fire();
            assertEquals(1, wizard.lookupAll(".button").stream().map(Button.class::cast)
                .filter(button -> button.getText().equals("Done")).count());
            advanceToReview(wizard, "Next");
            returnToEmployment(wizard);
            wizardButton(wizard, "+ Add employer").fire();
            wizardButton(wizard, "Done").fire();
            assertTrue(((Parent) wizard.lookup("#employerRows")).lookupAll(".button").isEmpty());
            wizardButton(wizard, "+ Add employer").fire();
            wizardField(wizard, "Company name *").setText("Acme");
            wizard.applyCss(); wizard.layout();
            wizard.requestLayout(); wizard.layout();
            TextField employerName = wizardField(wizard, "Company name *");
            ComboBox<?> employmentType = (ComboBox<?>) wizard.lookup("#employmentType");
            assertEquals(employerName.getWidth(), employmentType.getWidth(), 1);
            assertEquals(employerName.getHeight(), employmentType.getHeight(), 1);
            assertNotNull(wizard.lookup("#address"));
            assertNotNull(wizard.lookup("#phone"));
            wizardField(wizard, "Job title *").setText("Consultant");
            wizardField(wizard, "Employer phone *").setText("09 555 0101");
            wizardField(wizard, "Annual income (NZD) *").setText("-1");
            wizardButton(wizard, "Skip").fire();
            assertNotNull(wizard.lookup("#employmentPane"));
            assertTrue(wizard.lookupAll(".field-error").stream().anyMatch(javafx.scene.Node::isVisible));
            wizardField(wizard, "Annual income (NZD) *").setText("50000.00");
            ((ComboBox<String>) wizard.lookup("#employmentType")).setValue("Part-time");
            wizardButton(wizard, "+ Add employer").fire();
            assertEquals(1, wizard.lookupAll(".button").stream().map(Button.class::cast)
                .filter(button -> button.getText().equals("Done")).count());
            assertTrue(((Label) wizard.lookup("#message")).getText().contains("Click Done"));
            wizardButton(wizard, "Done").fire();
            wizardButton(wizard, "Back").fire();
            assertEquals("amelia@example.com", wizardField(wizard, "Email address *").getText());
            wizardButton(wizard, "Next").fire();
            wizardButton(wizard, "Edit").fire();
            assertEquals("Acme", wizardField(wizard, "Company name *").getText());
            assertEquals("50000.00", wizardField(wizard, "Annual income (NZD) *").getText());
            assertEquals("Part-time", ((ComboBox<?>) wizard.lookup("#employmentType")).getValue());
            wizardButton(wizard, "Remove").fire();
            assertTrue(wizard.lookup("#guarantorNote").isVisible());
            advanceToReview(wizard, "Skip");
            assertFalse(wizardButton(wizard, "Save customer").isDisabled());
            assertTrue(((Parent) wizard.lookup("#personalReview")).lookupAll(".label").stream()
                .map(Label.class::cast).anyMatch(label -> label.getText().contains("Amelia Adams")));
            assertTrue(((Parent) wizard.lookup("#contactReview")).lookupAll(".label").stream()
                .map(Label.class::cast).anyMatch(label -> label.getText().contains("amelia@example.com")));
            Parent review = (Parent) wizard.lookup("#reviewPane");
            wizardButton((Parent) review.getChildrenUnmodifiable().get(0), "Edit").fire();
            wizardField(wizard, "First name *").setText("Amy");
            wizardButton(wizard, "Save and return to Review").fire();
            assertTrue(((Parent) wizard.lookup("#personalReview")).lookupAll(".label").stream()
                .map(Label.class::cast).anyMatch(label -> label.getText().contains("Amy Adams")));
            review = (Parent) wizard.lookup("#reviewPane");
            wizardButton((Parent) review.getChildrenUnmodifiable().get(1), "Edit").fire();
            wizardField(wizard, "Email address *").setText("invalid");
            wizardButton(wizard, "Skip").fire();
            assertNotNull(wizard.lookup("#contactPane"));
            assertNull(wizard.lookup("#reviewPane"));
            assertTrue(wizard.lookupAll(".field-error").stream().anyMatch(javafx.scene.Node::isVisible));
            wizardField(wizard, "Email address *").setText("amy@example.com");
            wizardButton(wizard, "Save and return to Review").fire();
            assertFalse(wizardButton(wizard, "Save customer").isDisabled());
            returnToEmployment(wizard);
            wizardButton(wizard, "+ Add employer").fire();
            wizardField(wizard, "Company name *").setText("Acme");
            wizardField(wizard, "Job title *").setText("Consultant");
            wizardField(wizard, "Employer phone *").setText("09 555 0101");
            ((ComboBox<String>) wizard.lookup("#employmentType")).setValue("Full-time");
            wizardField(wizard, "Annual income (NZD) *").setText("50000.00");
            advanceToReview(wizard, "Next");
            assertTrue(((Parent) wizard.lookup("#employmentReview")).lookupAll(".label").stream()
                .map(Label.class::cast).anyMatch(label -> label.getText().contains("Acme") && label.getText().contains("50000.00")));


            // End this draft without opening a modal confirmation in the test.
            ((CustomersController) loader.getNamespace().get("customersViewController")).onShown();
            return null;
        });
        fx(() -> { ((Button) root.lookup("#homeNav")).fire(); return null; });
        assertTrue(fx(() -> root.lookupAll(".heading").stream().anyMatch(node -> ((Label) node).getText().equals("Welcome, Alyza"))));
        fx(() -> {
            root.lookupAll(".nav").stream().filter(node -> ((Button) node).getText().contains("Log Out")).findFirst().map(node -> (Button) node).orElseThrow().fire();
            return null;
        });
        assertTrue(loggedOut[0]);
        fx(() -> {
            Stage stage = new Stage();
            try {
                FXMLLoader loginLoader = new FXMLLoader(Main.class.getResource("fxml/login.fxml"));
                Parent login = loginLoader.load();
                LoginController loginController = loginLoader.getController();
                Scene scene = new Scene(login, 900, 600);
                stage.setScene(scene);
                stage.show();
                String[] submittedUsername = {null};
                loginController.configure((username, password) -> {
                    assertEquals("preview", password);
                    submittedUsername[0] = username;
                    loginController.setLoginInProgress(true);
                }, () -> {
                    try {
                        FXMLLoader dashboardLoader = new FXMLLoader(Main.class.getResource("fxml/dashboard.fxml"));
                        Parent dashboard = dashboardLoader.load();
                        ((DashboardController) dashboardLoader.getController()).configure(submittedUsername[0], () -> {});
                        scene.setRoot(dashboard);
                    } catch (java.io.IOException failure) {
                        throw new AssertionError("Dashboard failed to load", failure);
                    }
                });
                ((TextField) login.lookup("#username")).setText("Jack");
                ((PasswordField) login.lookup("#password")).setText("preview");
                ((Button) login.lookup("#loginButton")).fire();
                // Submitting credentials must wait for the authentication result.
                assertSame(login, scene.getRoot());
                assertEquals("Jack", submittedUsername[0]);
                assertTrue(((Button) login.lookup("#loginButton")).isDisabled());
                loginController.loginFailed();
                assertSame(login, scene.getRoot());
                assertFalse(((Button) login.lookup("#loginButton")).isDisabled());
                assertTrue(((Label) login.lookup("#error")).getText().contains("Invalid username or password"));
                ((Button) login.lookup("#loginButton")).fire();
                loginController.loginSucceeded();
                assertEquals("", ((PasswordField) login.lookup("#password")).getText());
                Parent dashboard = stage.getScene().getRoot();
                assertNotSame(login, dashboard);
                dashboard.applyCss();
                dashboard.layout();
                assertEquals("Welcome, Jack", ((Label) dashboard.lookup("#welcomeHeading")).getText());
            } finally {
                stage.close();
            }
            return null;
        });
        fx(() -> {
            Parent login = new FXMLLoader(Main.class.getResource("fxml/login.fxml")).load();
            new Scene(login, 1200, 800);
            for (int width : new int[] {1200, 480}) {
                login.resize(width, 800);
                login.applyCss(); login.layout();
                Region form = (Region) login.lookup("#loginForm");
                Region panel = (Region) login.lookup("#colourPanel");
                Region container = (Region) form.getParent();
                assertEquals(width >= 700, panel.isManaged());
                assertEquals(container.getWidth() / 2,
                        form.getLayoutX() + form.getWidth() / 2, 1);
                assertEquals(container.getHeight() / 2,
                        form.getLayoutY() + form.getHeight() / 2, 1);
                assertTrue(form.getWidth() <= 320);
                assertTrue(form.getWidth() > 0);
            }
            return null;
        });
    }

    private static void advanceToReview(Parent wizard, String action) {
        wizardButton(wizard, action).fire();
        assertNotNull(wizard.lookup("#notesPane"));
        wizardButton(wizard, "Skip").fire();
        assertNotNull(wizard.lookup("#documentsPane"));
        wizardButton(wizard, "Skip").fire();
        assertNotNull(wizard.lookup("#reviewPane"));
    }

    private static void returnToEmployment(Parent wizard) {
        wizardButton(wizard, "Back").fire();
        assertNotNull(wizard.lookup("#documentsPane"));
        wizardButton(wizard, "Back").fire();
        assertNotNull(wizard.lookup("#notesPane"));
        wizardButton(wizard, "Back").fire();
        assertNotNull(wizard.lookup("#employmentPane"));
    }

    private static Button wizardButton(Parent wizard, String text) {
        wizard.applyCss(); wizard.layout();
        return wizard.lookupAll(".button").stream().map(Button.class::cast)
            .filter(button -> button.getText().equals(text)).findFirst().orElseThrow();
    }

    private static TextField wizardField(Parent wizard, String label) {
        wizard.applyCss(); wizard.layout();
        return wizard.lookupAll(".text-field").stream().map(TextField.class::cast)
            .filter(field -> label.equals(field.getAccessibleText())).findFirst().orElseThrow();
    }
}
