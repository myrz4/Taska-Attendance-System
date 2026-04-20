package nfc;

import java.util.prefs.Preferences;

final class SidebarStateManager {
    private static final Preferences PREFS = Preferences.userNodeForPackage(AdminDashboard.class);
    private static final String KEY_COLLAPSED = "sidebar.collapsed";

    private SidebarStateManager() {
    }

    static boolean isCollapsed() {
        return PREFS.getBoolean(KEY_COLLAPSED, false);
    }

    static void setCollapsed(boolean collapsed) {
        PREFS.putBoolean(KEY_COLLAPSED, collapsed);
    }
}