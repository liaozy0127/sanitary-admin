# 后端多阶段构建
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
# 先复制 pom.xml 利用缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B -q
# 再复制源码构建
COPY src ./src
RUN mvn package -DskipTests -B -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
