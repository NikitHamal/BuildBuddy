package javax.lang.model;

/**
 * Stub class for Android on-device ECJ compilation.
 * Provides the minimal SourceVersion enum needed by ECJ batch compiler.
 */
public enum SourceVersion {
    RELEASE_0,
    RELEASE_1,
    RELEASE_2,
    RELEASE_3,
    RELEASE_4,
    RELEASE_5,
    RELEASE_6,
    RELEASE_7,
    RELEASE_8,
    RELEASE_9,
    RELEASE_10,
    RELEASE_11,
    RELEASE_12,
    RELEASE_13,
    RELEASE_14,
    RELEASE_15,
    RELEASE_16,
    RELEASE_17;

    public static SourceVersion latest() {
        return RELEASE_17;
    }

    public static SourceVersion latestSupported() {
        return RELEASE_17;
    }

    public static boolean isName(CharSequence name) {
        if (name == null || name.length() == 0) return false;
        if (!Character.isJavaIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
        }
        return true;
    }

    public static boolean isIdentifier(CharSequence name, SourceVersion version) {
        return isName(name);
    }

    public static String toBinaryName(String name) {
        if (name == null) return null;
        return name.replace('/', '.');
    }
}
