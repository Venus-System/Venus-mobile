import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

// Endereco da API de IA (Venus-AI-api). Fica fora do codigo porque muda entre
// quem testa: a API rodando no PC (http://localhost:8080/ com adb reverse) ou
// o servidor publicado. Vem de -PVENUS_IA_URL=... ou do local.properties;
// vazio deixa o chat avisando que ainda nao esta conectado, em vez de quebrar.
val propriedadesLocais = Properties().apply {
    val arquivo = rootProject.file("local.properties")
    if (arquivo.exists()) {
        arquivo.inputStream().use { load(it) }
    }
}
val urlVenusIa: String = (findProperty("VENUS_IA_URL") as String?)
    ?: propriedadesLocais.getProperty("VENUS_IA_URL")
    ?: ""

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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Provisorio: ainda nao existe a chave de release (o keystore vai
            // nos secrets do GitHub). Sem assinatura o APK nem instala, entao
            // por enquanto o release usa a chave de debug. O que protege a
            // pessoa e o release nao ser depuravel: num APK de debug, o
            // "adb run-as" le o token do Firebase e as respostas de saude.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Libera o BuildConfig: o DEBUG so liga o log de rede em debug, e o
    // VENUS_IA_URL diz onde esta a API de IA.
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
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.text.recognition)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}