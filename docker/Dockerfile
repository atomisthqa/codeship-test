FROM sforzando-docker-dockerv2-local.artifactoryonline.com/newrelic:0.5.0

MAINTAINER Jim Clark <jim@atomist.com>

RUN mkdir -p /opt/rugarchives/lib

WORKDIR /opt/rugarchives

COPY target/lib /opt/rugarchives/lib

COPY target/rugarchives-service.jar /opt/rugarchives

CMD ["-Xmx512m", "-cp", "/opt/rugarchives/rugarchives-service.jar", "clojure.main", "-m", "atomist.rugs.main"]

EXPOSE 8080
