-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class dagger.hilt.** { *; }
-keep class androidx.room.RoomDatabase_Impl
-dontwarn javax.annotation.**
