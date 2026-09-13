# Eword 混淆规则

# Gson 反序列化依赖字段名与泛型签名，需保留数据模型
-keep class com.eword.app.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
