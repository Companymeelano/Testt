package ir.meelano.manager;

import android.app.Application;

import ir.meelano.manager.core.CrashReporter;

/** App-wide hooks for the standalone Manager app. */
public final class ManagerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}
