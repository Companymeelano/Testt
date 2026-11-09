package ir.meelano.windowsinvoice.data;

import ir.meelano.windowsinvoice.AtiranGateway;
import ir.meelano.windowsinvoice.DemoAtiranGateway;

import java.util.List;
import java.util.Optional;

/**
 * Gateway selection order: the SQL profile saved on this Windows account together with the
 * signed-in Atiran session, then the local environment configuration, then demo mode.
 * Partial live environment settings never silently fall back to demo.
 */
public final class GatewayFactory {
    private GatewayFactory() { }

    /** Environment/demo gateway, used before an Atiran login exists (first run, setup). */
    public static AtiranGateway create() {
        return create(null);
    }

    /** Live gateway from the saved endpoint profile; demo while no Atiran session is signed in. */
    public static AtiranGateway create(AtiranLogin.Session session) {
        return create(WindowsSqlSettings.candidates(), session);
    }

    private static AtiranGateway create(List<WindowsSqlSettings.Endpoint> endpoints,
                                        AtiranLogin.Session session) {
        if (session != null && endpoints != null) {
            boolean writes = writesEnabled();
            for (WindowsSqlSettings.Endpoint endpoint : endpoints) {
                try {
                    return AtiranSqlGateway.fromProfile(endpoint, session, writes);
                } catch (RuntimeException ignored) {
                    // Try the other saved route (LAN ↔ WAN) before falling back.
                }
            }
        }
        Optional<AtiranSqlGateway> configured = AtiranSqlGateway.fromEnvironment();
        return configured.<AtiranGateway>map(g -> g).orElseGet(DemoAtiranGateway::new);
    }

    /** Real invoice writes stay behind the explicit local switch. */
    public static boolean writesEnabled() {
        String value = System.getenv("ATIRAN_ALLOW_INVOICE_WRITES");
        return value != null && "true".equalsIgnoreCase(value.trim());
    }
}
