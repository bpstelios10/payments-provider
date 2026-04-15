FROM gcr.io/distroless/java25-debian13

WORKDIR /app
COPY build/libs/service.jar service.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/service.jar"]
