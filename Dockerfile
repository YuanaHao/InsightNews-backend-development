FROM eclipse-temurin:17-jdk
WORKDIR /app
VOLUME /tmp
COPY InsightNews-0.0.1-SNAPSHOT.jar .
COPY myDenyWords.txt /app/myDenyWords.txt
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "InsightNews-0.0.1-SNAPSHOT.jar"]