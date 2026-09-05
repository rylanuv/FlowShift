# Project-specific R8 rules.
# Keep empty unless a library reports missing keep rules.

# Google Play Billing Library
-keep class com.android.billingclient.api.** { *; }
-keep class com.google.android.gms.internal.play_billing.** { *; }
