package com.clinic.client;

/**
 * Entry point for the packaged fat jar.
 * <p>
 * When a JavaFX app is launched from a plain classpath jar (no module path), the JVM
 * refuses to start an {@link javafx.application.Application} subclass directly with the
 * error "JavaFX runtime components are missing". Routing {@code main} through this
 * non-Application class sidesteps that check, then hands off to {@link MainApp}.
 * <p>
 * Dev runs still use {@code mvn javafx:run} (which targets {@code MainApp} directly);
 * this launcher is only the Main-Class of the shaded {@code desktop-app.jar}.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
