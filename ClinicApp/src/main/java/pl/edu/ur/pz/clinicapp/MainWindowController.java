package pl.edu.ur.pz.clinicapp;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import pl.edu.ur.pz.clinicapp.models.User;
import pl.edu.ur.pz.clinicapp.utils.ChildController;
import pl.edu.ur.pz.clinicapp.utils.HistoryTracker;
import pl.edu.ur.pz.clinicapp.views.ReferralDetailsView;

import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class MainWindowController implements Initializable {
    public enum Views {
        WELCOME,
        NOTIFICATIONS,
        ACCOUNTS,
        ACCOUNT_DETAILS,
        SCHEDULE,
        VISITS,
        VISIT_DETAILS,
        PATIENTS,
        PATIENT_DETAILS,
        REFERRALS,
        REFERRAL_DETAILS,
        PRESCRIPTIONS,
        PRESCRIPTION_DETAILS,
        REPORTS,
        TIMETABLE,
    }

    private static URL getViewResource(String resourcePath) {
        return ClinicApplication.class.getResource(resourcePath);
    }

    private static final EnumMap<Views, URL> viewToResource = new EnumMap<>(Map.of(
        Views.NOTIFICATIONS,        getViewResource("views/NotificationsView.fxml"),
        Views.ACCOUNTS,             getViewResource("views/AccountsView.fxml"),
        Views.ACCOUNT_DETAILS,      getViewResource("views/AccountDetailsView.fxml"),
        Views.VISITS,               getViewResource("views/VisitsView.fxml"),
        Views.PATIENTS,             getViewResource("views/PatientsView.fxml"),
        Views.REFERRALS,            getViewResource("views/ReferralsView.fxml"),
        Views.REFERRAL_DETAILS,     getViewResource("views/ReferralDetailsView.fxml"),
        Views.PRESCRIPTIONS,        getViewResource("views/PrescriptionsView.fxml"),
        Views.TIMETABLE,            getViewResource("views/TimetableView.fxml"),
        Views.PRESCRIPTION_DETAILS, getViewResource("views/PrescriptionDetailsView.fxml")
    ));

    static class ViewDefinition {
        public Node node;
        public ChildController<MainWindowController> controller;

        ViewDefinition(Node node, ChildController<MainWindowController> controller) {
            this.node = node;
            this.controller = controller;
        }
    }

    @FXML private BorderPane contentPane;

    @FXML private VBox navigationButtons;

    @FXML private Text loggedAsText;

    @FXML private Text roleText;

    public Window getWindow() {
        return this.contentPane.getScene().getWindow();
    }
    public Stage getStage() {
        return (Stage) getWindow();
    }

    private EnumMap<Views, ViewDefinition> views;
    private HistoryTracker<Views> historyTracker;

    public List<HistoryTracker.HistoryPoint<Views>> getHistory() {
        return historyTracker.getHistory();
    }

    private ViewDefinition getView(Views which) {
        final var cached = views.get(which);
        if (cached == null) {
            try {
                Logger.getGlobal().finest("Loading view: " + which);
                final var loader = new FXMLLoader(viewToResource.get(which));
                final Node node = loader.load();
                final ChildController<MainWindowController> controller = loader.getController();
                controller.setParentController(this);
                final var def = new ViewDefinition(node, controller);
                views.put(which, def);
                return def;
            }
            catch (IOException e) {
                throw new RuntimeException("Error while loading view: " + which.name(), e);
            }
        }
        else {
            return cached;
        }
    }

    private ViewDefinition getCurrentView() {
        return getView(historyTracker.getCurrent().which);
    }

    private ViewDefinition getPreviousView() {
        final var point = historyTracker.getPrevious();
        if (point == null) {
            return null;
        }
        return getView(point.which);
    }

    private Button buttonForNavigationMenu(String text, EventHandler<ActionEvent> action) {
        final var button = new Button(text);
        button.getStyleClass().add("navigation-menu-button");
        button.setOnAction(action);
        return button;
    }

    private Button buttonForLogout() {
        final var button = new Button("Wyloguj się");
        button.getStyleClass().addAll("navigation-menu-button", "log-out");
        button.setOnAction((e) -> {
            // Log-out is necessary here, even tho there already is `setOnCloseRequest` for the stage,
            // as it just catches window event.
            if(ReferralDetailsView.getEditState() && !ReferralDetailsView.exitConfirm()) return;
            ClinicApplication.logOut();
            getStage().close();
        });
        return button;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialize views cache map and history tracker
        views = new EnumMap<>(Views.class);
        historyTracker = new HistoryTracker<>();

        // Update displayed names if any
        final var displayName = ClinicApplication.getUser().getDisplayName();
        final var role = ClinicApplication.getUser().getRole();
        if (displayName != null) {
            loggedAsText.setText("Zalogowany jako " + displayName);
        }
        roleText.setText(ClinicApplication.getUser().getRole().toString());

        // Populate navigation menu
        {
            final var c = navigationButtons.getChildren();

            // TODO: notifications button should include red dot when there are any unread
            c.add(buttonForNavigationMenu("Powiadomienia", (e) -> goToView(Views.NOTIFICATIONS)));
            if (!role.isGroupUser()) {
                c.add(buttonForNavigationMenu("Moje dane", (e) -> goToView(Views.ACCOUNT_DETAILS, ClinicApplication.getUser())));
            }

            if (role == User.Role.DOCTOR) {
//                c.add(buttonForNavigationMenu("Terminarz", (e) -> goToView(Views.SCHEDULE)));
                // TODO: instead having direct button to timetable, there should be button in schedule,
                //      as timetable isn't that interesting to be checked every time and the info will be
                //      most likely already included on the schedule too.
                c.add(buttonForNavigationMenu("Harmonogram", (e) -> goToView(Views.TIMETABLE)));
            }

            if (role == User.Role.PATIENT) {
                c.add(buttonForNavigationMenu("Wizyty", (e) -> goToView(Views.VISITS, ClinicApplication.getUser())));
                c.add(buttonForNavigationMenu("Recepty", (e) -> goToView(Views.PRESCRIPTIONS)));
                c.add(buttonForNavigationMenu("Skierowania", (e) -> goToView(Views.REFERRALS)));
            }
            else {
                c.add(buttonForNavigationMenu("Wizyty", (e) -> goToView(Views.VISITS)));
                c.add(buttonForNavigationMenu("Recepty", (e) -> goToView(Views.PRESCRIPTIONS)));
                c.add(buttonForNavigationMenu("Skierowania", (e) -> goToView(Views.REFERRALS)));
                c.add(buttonForNavigationMenu("Pacjenci", (e) -> goToView(Views.PATIENTS)));
            }

            if (role == User.Role.ADMIN) {
                c.add(buttonForNavigationMenu("Zarządzanie kontami", (e) -> goToView(Views.ACCOUNTS)));
                c.add(buttonForNavigationMenu("Raporty", (e) -> goToView(Views.REPORTS)));
            }

            c.add(buttonForLogout());
        }

        // Choose initial view
        // TODO: happy welcome/dashboard screen? even if user would only see it once
        // TODO: if pending notifications, start with notification view
        switch (role) {
            case PATIENT -> goToView(Views.VISITS);
            case DOCTOR -> goToView(Views.PATIENTS);
            default -> goToView(Views.NOTIFICATIONS);
        }
    }

    private void updateNavigationMenuButtons() {
        // TODO: update 'active' class (CSS)
    }

    /**
     * Navigates to view without pushing history stack.
     * @param which Which view to navigate to.
     * @param context Additional context parameter(s).
     */
    public void goToViewRaw(Views which, Object... context) {
        final var newView = getView(which);
        final var oldView = getPreviousView();

        // TODO: might be good idea to make it more generic behaviour of view, maybe providing new interface
        //      and checking if (oldView instanceof NavigationAware np) { if (np.onNavigation(new)) {...} }
        if (ReferralDetailsView.getEditState() && !ReferralDetailsView.exitConfirm()) return;
        ReferralDetailsView.setEditState(false);

        if (oldView != null && oldView.controller != null) {
            oldView.controller.dispose();
        }
        contentPane.setCenter(newView.node);
        this.updateNavigationMenuButtons();
        if (newView.controller != null) {
            newView.controller.populate(context);
        }
    }

    /**
     * Navigates to view.
     * @param which Which view to navigate to.
     * @param context Additional context parameter(s).
     */
    public void goToView(Views which, Object... context) {
        historyTracker.go(which, context);
        goToViewRaw(which, context);
    }

    public void goToHistoryPoint(HistoryTracker.HistoryPoint<Views> point) {
        goToViewRaw(point.which, point.context);
    }

    public void goBack() {
        goToHistoryPoint(historyTracker.back());
    }

    public void refreshCurrentView() {
        final var view = getCurrentView();
        if (view.controller != null) {
            view.controller.refresh();
        }
    }


}
