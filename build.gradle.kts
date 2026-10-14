plugins {
    java
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "Knowra-api"


val querydslVersion = "5.0.0"
// Boot BOM 은 querydsl 을 5.1.0 으로 관리한다 — 그대로 두면 querydsl-jpa 5.0.0 옆에 core 5.1.0 이 섞인다.
// Maven 시절 런타임과 같게 5.0.0 으로 맞춘다.
extra["querydsl.version"] = querydslVersion
val jjwtVersion = "0.12.6"

dependencies {
    // 파일 저장(prod) — S3. 로컬·개발은 디스크(StorageService 프로필 분기).
    // synub 생태계와 같은 버전(전역 규칙: AWS SDK 는 BOM 으로 관리, 레퍼런스 synub-office)
    implementation(platform("software.amazon.awssdk:bom:2.28.29"))
    implementation("software.amazon.awssdk:s3")

    // HTML 정리(XSS) — 글 본문·위키는 HTML 이라 허용 목록 밖은 저장 전에 걷어낸다
    implementation("org.jsoup:jsoup:1.18.3")

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-tomcat")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // 메일 — 가입 인증 코드 · 비밀번호 재설정 링크 (MailService)
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // JWT (0.12.x: API 변경됨 - Jwts.parserBuilder() → Jwts.parser())
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.querydsl:querydsl-jpa:$querydslVersion:jakarta")
    annotationProcessor("com.querydsl:querydsl-apt:$querydslVersion:jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // DB. Spring Boot 4 는 자동설정이 기술별 모듈로 쪼개져 있다. flyway-core 만 넣으면
    // FlywayAutoConfiguration 이 없어 마이그레이션이 조용히 안 돈다.
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // QR Code
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.google.zxing:javase:3.5.2")
    // Gson (CVE-2022-25647: 2.8.9+에서 수정)
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.modelmapper:modelmapper:3.2.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Maven 시절(spring-boot-starter-parent)과 같게 — 21 바이트코드, UTF-8 소스.
// 툴체인 대신 release 를 쓴다: CI 는 JDK 23 이고 로컬도 21 이상이면 아무 JDK 로 빌드된다.
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 배포(deploy-api.yml)가 build/libs/app.jar 하나만 올린다 — plain jar 는 만들지 않는다
tasks.bootJar {
    archiveFileName = "app.jar"
}
tasks.jar {
    enabled = false
}
