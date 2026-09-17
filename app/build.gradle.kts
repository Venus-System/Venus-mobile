plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Libera o BuildConfig.DEBUG, usado para so ligar o log de rede em debug.
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

    // A BoM define as versoes de todas as libs do Firebase: nao coloque
    // versao nas linhas de firebase-* abaixo, ela vem daqui.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Login com Google. O GoogleSignInClient antigo esta descontinuado;
    // o caminho atual e o Credential Manager.
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.fragment)
    implementation(libs.recyclerview)
    // Carrega imagem de URL: o ImageView sozinho nao busca da rede.
    implementation(libs.coil)

    // Retrofit fala com a API do Venus-CRUD; o converter-gson transforma
    // o JSON nas classes de repository/api/dto.
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}