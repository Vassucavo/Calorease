plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 签名密钥来自 GitHub Secret。有密钥就出正式签名包,
// 没有就退回调试签名(能装,但每次密钥都不同,无法覆盖更新)。
val ksPath: String? = System.getenv("KEYSTORE_PATH")
val hasKs = !ksPath.isNullOrEmpty() && file(ksPath).exists()

// versionCode 偏移量。
//
// 这个仓库的 release tag 从 v21 起(旧仓库 Vassucavo/Ledger 停在 v13,
// 新仓库的 github.run_number 从 1 重开,加 20 保证数字只增不减)。
// 原生版换了包名 app.calorease,理论上不再受旧安装的 versionCode 约束,
// 但保持同一套偏移,tag、versionName、versionCode 三个数字才对得上。
// 这个数只能往上加,永远不要调小。
val codeOffset = 20
val runNo = (System.getenv("RUN_NUMBER") ?: "0").toInt()
val appVersionCode = runNo + codeOffset

android {
    // 包名。网页壳版本用的是 app.ledger,原生重写时趁着还没有用户数据
    // 一起改成了 app.calorease —— 和仓库名、应用名一致。
    // 从现在起这个值不能再动:改包名等于发布另一个应用,装上去和旧版并存,
    // 数据一条都带不过来。
    namespace = "app.calorease"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.calorease"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        // 网页壳版本走到 v8,原生版从 v9 这一代起算。
        versionName = "9.0.$appVersionCode"
    }

    signingConfigs {
        if (hasKs) {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // 调试版单独算一个应用,和手机上装着的正式版并存。
            //
            // 不加这个后缀的话,从电脑上按 Run 装不进去:包名一样但签名不一样
            // (正式版用的是仓库密钥,调试版用的是本机自动生成的调试密钥),
            // 系统会以 INSTALL_FAILED_UPDATE_INCOMPATIBLE 拒绝安装,
            // 除非先把正式版连数据一起卸掉。
            //
            // 分开还有个好处:调试时乱填的数据不会弄脏你真正在记的那一份。
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            if (hasKs) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time 在 minSdk 24 上要靠脱糖
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // 食物库放在仓库根目录的 foods.json,不塞进 Kotlin 代码 ——
    // 这样加一条食物、改一个数值,在 GitHub 网页上编辑那个文件就行。
    sourceSets.getByName("main") {
        assets.srcDir(layout.buildDirectory.dir("generated/foodAssets"))
    }
}

val copyFoods by tasks.registering(Copy::class) {
    from(rootProject.file("foods.json"))
    into(layout.buildDirectory.dir("generated/foodAssets"))
}

tasks.named("preBuild") { dependsOn(copyFoods) }
// 单元测试直接读根目录那份,保证测的和打进包里的是同一个文件
tasks.withType<Test>().configureEach {
    systemProperty("foods.json", rootProject.file("foods.json").absolutePath)
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
