# Preserve metadata Gson relies on when rebuilding generic collections or honouring @SerializedName
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# Ensure Gson's reflective TypeToken helpers stay intact for release builds.
-keep class com.google.gson.reflect.TypeToken { *; }

-keep class com.marfeel.** { *; }
