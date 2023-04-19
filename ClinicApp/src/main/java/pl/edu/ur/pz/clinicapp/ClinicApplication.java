package pl.edu.ur.pz.clinicapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.hibernate.service.spi.ServiceException;
import pl.edu.ur.pz.clinicapp.dialogs.LoginDialog;
import pl.edu.ur.pz.clinicapp.models.User;
import pl.edu.ur.pz.clinicapp.utils.CustomImportSqlCommandExtractor;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static pl.edu.ur.pz.clinicapp.utils.OtherUtils.isStringNullOrEmpty;

public class ClinicApplication extends Application {
    private static final Logger logger = Logger.getLogger(CustomImportSqlCommandExtractor.class.getName());

    private Properties properties;
    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;
    private User user;
    private MainWindowController mainWindowController;

    static private ClinicApplication instance;

    public static String getProperty(String key) {
        return instance.properties.getProperty(key);
    }

    public static EntityManager getEntityManager() {
        return instance.entityManager;
    }

    public static User getUser() {
        return instance.user;
    }

    @Override
    public void start(Stage stage) throws Exception {
        instance = this;

        // Load logging custom properties if any
        try (FileInputStream loggingPropertiesFile = new FileInputStream("logging.properties")) {
            LogManager.getLogManager().readConfiguration(loggingPropertiesFile);
        }
        catch (FileNotFoundException e) {
            // Ignore, system defaults will be used, most likely INFO level only to console.
            LogManager.getLogManager().readConfiguration();
        }
        logger.finest("Hello!");

        // Load app properties
        try (InputStream inputStream = ClassLoader.getSystemResourceAsStream("app.default.properties")) {
            final var defaults = new Properties() {{
                load(inputStream);
            }};
            properties = new Properties(defaults);
        }
        try (FileInputStream appPropertiesFile = new FileInputStream("app.properties")) {
            properties.load(appPropertiesFile);
        }
        catch (FileNotFoundException e) {
            // Ignore, defaults will be used.
        }

        if (isSeedingAvailable()) {
            if (showConfirmSeedingDialog()) {
                seedDatabase();
            }
        }

        connectToDatabaseAnonymously();
        while (waitForLogin()) {
            spawnMainWindow();
        }
    }

    private boolean waitForLogin() {
        final var dialog = new LoginDialog("dblaszczyk@gmail.com", "administrator");
//        final var dialog = new LoginDialog();
        dialog.showAndWait();
        if (user == null) {
            Platform.exit();
            return false;
        }
        return true;
    }

    private void handleDatabaseConnectionError(ServiceException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText("Wystąpił błąd w czasie łączenia do bazy danych.\n\nSprawdź, czy dla zadanych ustawień aplikacji baza danych jest poprawnie skonfigurowana i uruchomiona.\n\nSzczegóły:\n" + e.getLocalizedMessage());
        alert.setTitle("Błąd w czasie łączenia do bazy danych");
        alert.setHeaderText(null);
        alert.showAndWait();
        System.exit(1);
    }

    private void disconnectFromDatabase() {
        // TODO: gracefully disconnect from the database (if connected)
        if (entityManager != null) {
            entityManager.close();
        }
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    private boolean isSeedingAvailable() {
        return !isStringNullOrEmpty(properties.getProperty("seeding.username"));
    }

    private boolean showConfirmSeedingDialog() {
        final var dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Reinitializacja bazy danych");
        dialog.setHeaderText(null);
        dialog.setContentText("Zmienna środowiskowa `SEED_USERNAME` istnieje. " +
                "Czy chcesz reinitializować bazę danych? Wszystkie dane zostaną utracone, " +
                "schemat zostanie utworzony na nowo i wypełniony przykładowymi danymi.");
        dialog.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        return dialog.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void seedDatabase() {
        disconnectFromDatabase();
        logger.fine("----------------------------------------------------------------");
        logger.info("Seeding database...");
        try {
            // For seeding we need superuser and special setting, so we shadow default settings from `persistence.xml`.
            entityManagerFactory = Persistence.createEntityManagerFactory("default", Map.ofEntries(
                    Map.entry("hibernate.connection.username", properties.getProperty("seeding.username")),
                    Map.entry("hibernate.connection.password", properties.getProperty("seeding.password")),
                    Map.entry("hibernate.hbm2ddl.auto", "create")
            ));
            entityManager = entityManagerFactory.createEntityManager();
            logger.info("Finished seeding!");
        }
        catch (ServiceException e) {
            handleDatabaseConnectionError(e);
        }
        finally {
            logger.fine("----------------------------------------------------------------");
        }
    }

    /**
     * Connects to database anonymously, in order to fetch user details while logging in or view public data as guest.
     */
    private void connectToDatabaseAnonymously() {
        disconnectFromDatabase();
        try {
            // For anonymous connect, the login details are used from  `persistence.xml` along other settings.
            entityManagerFactory = Persistence.createEntityManagerFactory("default");
            entityManager = entityManagerFactory.createEntityManager();
        }
        catch (ServiceException e) {
            handleDatabaseConnectionError(e);
        }
    }

    private void connectToDatabaseAsUser(String emailOrPESEL, String password) {
        try {
            // We shadow default (anonymous) login details from `persistence.xml` with user specific ones.
            final var emf = Persistence.createEntityManagerFactory("default", Map.ofEntries(
                    Map.entry("hibernate.connection.username", User.getDatabaseUsernameForInput(emailOrPESEL)),
                    Map.entry("hibernate.connection.password", password)
            ));
            final var em = entityManagerFactory.createEntityManager();

            // Late replace to prevent reconnecting as anonymous on login failures and allow database username fetching
            disconnectFromDatabase();
            entityManagerFactory = emf;
            entityManager = em;
        }
        // TODO: catch security specific exceptions to show "bad password" message.
        catch (ServiceException e) {
            handleDatabaseConnectionError(e);
        }
    }

    private void spawnMainWindow() throws IOException {
        final var stage = new Stage();
        final var loader = new FXMLLoader(ClinicApplication.class.getResource("MainWindow.fxml"));
        final BorderPane pane = loader.load();
        final var scene = new Scene(pane);
        stage.setTitle("ClinicApp");
        stage.setScene(scene);
        stage.minWidthProperty().bind(pane.minWidthProperty());
        stage.maxWidthProperty().bind(pane.maxWidthProperty());
        stage.minHeightProperty().bind(pane.minHeightProperty());
        stage.maxHeightProperty().bind(pane.maxHeightProperty());
        mainWindowController = loader.getController();
//        stage.setWidth(pane.getMinWidth());
//        stage.setHeight(pane.getMinHeight());
        stage.setOnCloseRequest(we -> logOut());
        stage.showAndWait();
    }

    static public void logOut() {
        instance.user = null;
        instance.connectToDatabaseAnonymously();
        logger.info("Logged out");
    }

    static public void logIn(String emailOrPESEL, String password) {
        logger.info("Logging in as `%s`".formatted(emailOrPESEL));
        instance.connectToDatabaseAsUser(emailOrPESEL, password);
        instance.user = User.getCurrent();
        logger.info("Logged in");
    }

    public static void main(String[] args) {
        launch();
    }
}