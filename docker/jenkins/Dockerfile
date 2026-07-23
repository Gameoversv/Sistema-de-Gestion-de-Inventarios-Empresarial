# Jenkins con los plugins ya instalados y el asistente desactivado.
#
# Por que una imagen propia y no la oficial a secas: la configuracion hecha por el
# asistente vive solo dentro del volumen jenkins_home. Se pierde al recrear el
# contenedor, no esta en el repositorio y nadie mas puede reproducirla. Este
# proyecto ya lo sufrio: el Jenkinsfile referencia credenciales y un tool jdk-21
# que no existian en la instalacion, asi que el pipeline nunca llego a ejecutarse.
FROM jenkins/jenkins:lts-jdk21

USER root

# El Jenkinsfile hace `docker build` y `docker compose up` desde las etapas, asi que
# el contenedor necesita el cliente de Docker. El socket del host se monta en el
# compose; aqui solo va el cliente, no el demonio.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg jq \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg \
       | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
    && chmod a+r /etc/apt/keyrings/docker.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
       https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
       > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin \
    && rm -rf /var/lib/apt/lists/*

USER jenkins

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt --latest true

# runSetupWizard=false evita la pantalla de desbloqueo: la configuracion entra por
# JCasC, no a mano.
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
ENV CASC_JENKINS_CONFIG=/var/jenkins_conf/casc.yaml
