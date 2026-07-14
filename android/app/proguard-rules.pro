-keep class pro.onephone.callfilter.** { *; }
-keep class com.android.billingclient.** { *; }
-keep class android.telephony.TelephonyManager { *; }
-keep class com.android.internal.telephony.ITelephony { *; }
-dontwarn org.json.**
-keep class org.json.** { *; }
-keep public class * extends android.app.Activity
-keep public class * extends android.telecom.CallScreeningService
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# ---------------------------------------------------------------
# Razorpay Checkout SDK
# The SDK invokes the Activity's onPaymentSuccess/onPaymentError via
# reflection, so those methods (and the SDK classes) must be kept or
# release builds will fail silently after payment.
# ---------------------------------------------------------------
-keepclassmembers class * {
    public void onPayment*(...);
}
-keep class com.razorpay.** { *; }
-keep interface com.razorpay.** { *; }
-dontwarn com.razorpay.**
-optimizations !method/inlining/*

# Keep our billing classes (referenced by the Factory / reflection)
-keep class pro.onephone.callfilter.RazorpayBillingManager { *; }
-keep class pro.onephone.callfilter.PaywallActivity { *; }
