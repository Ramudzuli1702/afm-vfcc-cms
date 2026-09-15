package com.afmvfcc.utils;

import java.util.function.Consumer;

/**
 * Lets any page navigate the sidebar without holding a reference to
 * MainLayoutController — MainLayoutController registers itself once via
 * {@link #attach}, the same way ToastManager works for toasts. Used by the
 * Dashboard's stat cards to jump straight to the related module.
 */
public class NavigationBus {

    private static Consumer<String> navigator;

    public static void attach(Consumer<String> navigateByButtonId) {
        navigator = navigateByButtonId;
    }

    /** @param btnId one of the sidebar button fx:ids, e.g. "btnMembers" */
    public static void goTo(String btnId) {
        if (navigator != null) navigator.accept(btnId);
    }
}
