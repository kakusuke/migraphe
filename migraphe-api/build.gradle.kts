plugins {
    `java-library`
}

dependencies {
    // 外部依存なし - 純粋なJava API

    // テスト依存のみ
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}
