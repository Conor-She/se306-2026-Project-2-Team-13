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
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(5, TimeUnit.SECONDS);
    }

    @Test void navigationAndUsernameWorkWithoutBackend() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
        boolean[] loggedOut = {false};
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("dashboard.fxml"));
        Parent root = fx(() -> {
            Parent view = loader.load();
            new Scene(view, 900, 560);
            DashboardController controller = loader.getController();
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
            assertEquals(scroll.getViewportBounds().getHeight() - content.getInsets().getBottom(), bottom, 3);
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
            assertNull(root.lookup(".text-field"));
            assertNull(root.lookup(".list-view"));
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
                new Main().start(stage);
                Parent login = stage.getScene().getRoot();
                ((TextField) login.lookup("#username")).setText("Jack");
                ((PasswordField) login.lookup("#password")).setText("preview");
                ((Button) login.lookup("#loginButton")).fire();
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
            Parent login = new FXMLLoader(Main.class.getResource("login.fxml")).load();
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
}
