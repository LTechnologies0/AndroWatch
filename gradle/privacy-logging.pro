# Privacy-safe release logging — strip debug-only profiling.
# Release builds keep only PrivacyLog boolean flags (OpPrivacy tag).

-assumenosideeffects class ltechnologies.onionphone.androwatch.collector.DiagnosticsKt {
    public static void awLogW(...);
}

-keep class ltechnologies.onionphone.**.PrivacyLog { *; }
