package ir.meelano.android;

/** Decides when a previously entered delivery address needs an explicit customer-change review. */
final class VisitorAddressReview {
    private VisitorAddressReview() { }

    static boolean required(String previousCode, String nextCode, String existingAddress) {
        if (existingAddress == null || existingAddress.trim().isEmpty()) return false;
        String oldCode = previousCode == null ? "" : previousCode.trim();
        String newCode = nextCode == null ? "" : nextCode.trim();
        // Unknown identifiers must not be treated as proof that the customer stayed the same.
        return oldCode.isEmpty() || newCode.isEmpty() || !oldCode.equals(newCode);
    }
}
