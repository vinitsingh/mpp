package dev.mpp.protocol;

public final class MppHeaders {
    private MppHeaders() {}

    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String AUTHORIZATION = "Authorization";
    public static final String PAYMENT_RECEIPT = "Payment-Receipt";

    /** Authorization: Payment <base64url-credential-json> */
    public static final String PAYMENT_SCHEME = "Payment ";

    public static final String PROBLEM_BASE = "https://paymentauth.org/problems/";
}
