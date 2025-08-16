# Stage 1: Clone the repo
FROM gradle:jdk21-corretto AS clone
ARG GITHUB_REPO_URL=https://github.com/ryeash/full-steam
ARG BRANCH=master
RUN git clone --depth 1 --branch "${BRANCH}" --single-branch ${GITHUB_REPO_URL} /app

# Stage 2: Build the graalvm image
FROM ghcr.io/graalvm/native-image-community:21 AS build
COPY --from=clone /app /app
WORKDIR /app
RUN ./gradlew clean nativeCompile --no-daemon --no-build-cache


# Stage 3: run the image
FROM ghcr.io/graalvm/jdk-community:21 AS run
EXPOSE 8080

COPY --from=build /app/build/native/nativeCompile/full-steam /app/full-steam

ENTRYPOINT ["/app/full-steam"]