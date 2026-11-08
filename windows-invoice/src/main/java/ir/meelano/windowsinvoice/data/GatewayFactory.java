package ir.meelano.windowsinvoice.data;

import ir.meelano.windowsinvoice.AtiranGateway;
import ir.meelano.windowsinvoice.DemoAtiranGateway;

import java.util.Optional;

/** Only demo mode is used when no SQL URL is configured. Partial live settings never silently fall back. */
public final class GatewayFactory {
    private GatewayFactory() { }

    public static AtiranGateway create() {
        Optional<AtiranSqlGateway> configured = AtiranSqlGateway.fromEnvironment();
        return configured.<AtiranGateway>map(g -> g).orElseGet(DemoAtiranGateway::new);
    }
}
