# App-specific R8 rules; the defaults (proguard-android-optimize.txt) cover everything so far

# The tools' pages in their Play modules, loaded by name (FeatureDelivery.ui)
-keep class * implements org.lukemech.lmtoolbox.helpers.FeatureUi { public static ** INSTANCE; }
