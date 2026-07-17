# Firestore maps documents to data classes by reflection over field names.
# Keep the model package so those names survive any future minification.
-keep class com.chefshare.app.data.model.** { *; }

# Glide recommended keep rules.
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { **[] $VALUES; public *; }
