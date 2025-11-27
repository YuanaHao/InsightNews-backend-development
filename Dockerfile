# 直接使用运行环境，不需要 Maven 镜像
FROM eclipse-temurin:17-jdk
WORKDIR /app
# 直接复制本地已经打好的包
COPY target/InsightNews-0.0.1-SNAPSHOT.jar app.jar
COPY myDenyWords.txt /app/myDenyWords.txt
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]