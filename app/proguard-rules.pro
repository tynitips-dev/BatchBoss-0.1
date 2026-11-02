# Firestore decodes this model through reflection. Preserve its wire property names
# and default constructor while allowing optimization everywhere else.
-keep,allowoptimization class com.example.data.model.Recipe {
    public <init>();
    <fields>;
    public *** get*();
}

# Keep useful source locations in the mapping/retraced crash reports.
-keepattributes SourceFile,LineNumberTable
