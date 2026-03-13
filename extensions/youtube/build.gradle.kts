dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:youtube:stub"))
    compileOnly(libs.annotation)
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.25.1")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("com.squareup.okio:okio:3.7.0")
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
}

repositories {
    mavenCentral()
    google()
    maven {
        url = uri("https://jitpack.io")
    }
}

android {
    defaultConfig {
        minSdk = 26
    }
}
