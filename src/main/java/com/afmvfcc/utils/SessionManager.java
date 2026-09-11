package com.afmvfcc.utils;

import com.afmvfcc.models.User;
import javafx.application.Platform;

import java.util.Timer;
import java.util.TimerTask;

public class SessionManager {

    private static SessionManager instance;

    private User currentUser;
    private Timer timeoutTimer;
    private Runnable onSessionExpired;

    private boolean suspended = false;

    // 15 minutes in milliseconds
    private static final long TIMEOUT_MS = 15 * 60 * 1000;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /**
     * Call this when a user successfully logs in.
     */
    public void login(User user, Runnable onSessionExpired) {
        this.currentUser      = user;
        this.onSessionExpired = onSessionExpired;
        this.suspended        = false;
        startTimer();
        AuditLogger.log(user.getId(), "Logged in.");
    }

    /**
     * Call this whenever the user interacts with the app to reset the timeout.
     * Has no effect while a broadcast task is running.
     */
    public void resetTimer() {
        if (currentUser != null && !suspended) {
            stopTimer();
            startTimer();
        }
    }

    /**
     * Suspend the inactivity timer while a long-running task (download / upload)
     * is in progress. Safe to call multiple times — only the first call acts.
     */
    public void suspendTimeout() {
        if (!suspended) {
            suspended = true;
            stopTimer();
        }
    }

    /**
     * Resume the inactivity timer after a long-running task finishes (success or
     * failure). The user gets a fresh full 15-minute window — they were clearly
     * present if they just completed an upload or download.
     * Safe to call multiple times — only the first call after a suspend acts.
     */
    public void resumeTimeout() {
        if (suspended) {
            suspended = false;
            if (currentUser != null) {
                startTimer();
            }
        }
    }

    /**
     * Call this when the user manually logs out.
     */
    public void logout() {
        if (currentUser != null) {
            AuditLogger.log(currentUser.getId(), "Logged out.");
        }
        suspended = false;
        stopTimer();
        currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isSuperAdmin() {
        return currentUser != null && currentUser.isSuperAdmin();
    }

    private void startTimer() {
        timeoutTimer = new Timer(true);
        timeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    if (onSessionExpired != null) {
                        AuditLogger.log(
                            currentUser != null ? currentUser.getId() : -1,
                            "Session timed out after 15 minutes of inactivity."
                        );
                        currentUser = null;
                        suspended   = false;
                        onSessionExpired.run();
                    }
                });
            }
        }, TIMEOUT_MS);
    }

    private void stopTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }
}
