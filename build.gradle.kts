buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:4.2.2")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()

        // TODO: Needed for Xposed API libs
        jcenter()
    }
}
