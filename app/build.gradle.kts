import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val urlVenusIaPadrao = "http://44.222.132.139/ai/"
val propriedadesLocais = Properties().apply {
    val arquivo = rootProject.file("local.properties")
    if (arquivo.exists()) {
        arquivo.inputStream().use { load(it) }
    }
}
val urlVenusIa: String = (findProperty("VENUS_IA_URL") as String?)
    ?: propriedadesLocais.getProperty("VENUS_IA_URL")
    ?: urlVenusIaPadrao

val urlVenusClassificacaoPadrao = "https://34-192-194-32.sslip.io/classificacao/"
val urlVenusClassificacao: String = (findProperty("VENUS_CLASSIFICACAO_URL") as String?)
    ?: propriedadesLocais.getProperty("VENUS_CLASSIFICACAO_URL")
    ?: urlVenusClassificacaoPadrao

android {
    namespace = "com.venussystem.venusmobile"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.venussystem.venusmobile"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "VENUS_IA_URL", "\"$urlVenusIa\"")
        buildConfigField("String", "VENUS_CLASSIFICACAO_URL", "\"$urlVenusClassificacao\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.fragment)
    implementation(libs.recyclerview)
    implementation(libs.coil)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.play.services.location)
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.work.testing)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}