# kotlinx.serialization: mantém serializers gerados (DTOs, modelos persistidos e rotas de navegação).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.routeprice.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.routeprice.**$$serializer { *; }

# Retrofit usa reflexão nas interfaces de API.
-keep,allowobfuscation,allowshrinking interface com.routeprice.core.data.remote.RoutesApi
-keep,allowobfuscation,allowshrinking interface com.routeprice.core.data.remote.PlacesApi
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
