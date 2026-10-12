plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.retrostation.engine.libretro"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DANDROID_PLATFORM=android-26"
                )
                cppFlags(
                    "-std=c++20",
                    "-O3",
                    "-fvisibility=hidden",
                    "-fvisibility-inlines-hidden",
                    "-fexceptions",
                    "-frtti"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    packaging {
        jniLibs {
            pickFirsts.addAll(listOf("**/libc++_shared.so"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core-canonical"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
}
