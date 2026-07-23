pipeline {
    agent any

    options {
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    tools {
        jdk 'jdk-21'
    }

    environment {
        MAVEN_OPTS                      = '-Xmx512m -XX:+UseContainerSupport'
        DOCKER_IMAGE_NAME               = 'inventory-api'
        DOCKER_IMAGE_TAG                = "${env.BUILD_NUMBER}"
        TESTCONTAINERS_RYUK_DISABLED    = 'true'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                echo "Branch: ${env.BRANCH_NAME} | Build: ${env.BUILD_NUMBER}"
            }
        }

        stage('Build') {
            steps {
                dir('backend') {
                    sh './mvnw clean compile -B'
                }
            }
        }

        stage('Unit Tests') {
            steps {
                dir('backend') {
                    // Sin -Dtest=. El POM ya limita surefire a **/*Test.java y excluye
                    // **/*IT.java, y `-Dtest=` tiene prioridad sobre esos <excludes>: el
                    // filtro `!StockServiceConcurrencyIT` arrastraba a esta etapa TODOS
                    // los tests de integracion, incluido LiveDatabaseIT, que necesita una
                    // base desplegada. Por eso el build #1 fallo aqui.
                    sh './mvnw test -B'
                }
            }
            post {
                always {
                    junit 'backend/target/surefire-reports/**/*.xml'
                }
            }
        }

        stage('Integration Tests') {
            steps {
                dir('backend') {
                    sh './mvnw verify -B'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'backend/target/surefire-reports/**/*.xml,backend/target/failsafe-reports/**/*.xml'
                }
            }
        }

        // Va despues de Integration Tests a proposito: `verify` es quien genera
        // target/site/jacoco/jacoco.xml, y sin ese informe SonarCloud reportaria 0 %
        // de cobertura sobre un proyecto que esta por encima del 90 %.
        stage('Quality Gate — SonarCloud') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    dir('backend') {
                        sh '''
                            ./mvnw -B \
                              org.sonarsource.scanner.maven:sonar-maven-plugin:5.0.0.4389:sonar \
                              -Dsonar.token=$SONAR_TOKEN
                        '''
                    }
                }
            }
        }

        stage('Package') {
            steps {
                dir('backend') {
                    sh './mvnw package -B -DskipTests'
                }
                archiveArtifacts artifacts: 'backend/target/*.jar', fingerprint: true
            }
        }

        stage('Docker Build') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                sh "docker build -t ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG} ./backend"
                sh "docker tag ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG} ${DOCKER_IMAGE_NAME}:latest"
            }
        }

        stage('Deploy — Stack') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                withCredentials([file(credentialsId: 'inventory-env-file', variable: 'ENV_FILE')]) {
                    sh 'cp $ENV_FILE .env'
                }
                sh 'docker compose up -d --build'
                sh '''
                    echo "Waiting for backend..."
                    for i in $(seq 1 18); do
                        if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
                            echo "Backend ready after $((i * 10))s"
                            break
                        fi
                        [ "$i" = "18" ] && echo "ERROR: Backend timeout" && exit 1
                        echo "  attempt $i/18, retrying in 10s..."
                        sleep 10
                    done
                '''
            }
        }

        stage('API Smoke Tests') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                withCredentials([
                    string(credentialsId: 'kc-admin-password',  variable: 'ADMIN_PASS'),
                    string(credentialsId: 'kc-viewer-password', variable: 'VIEWER_PASS')
                ]) {
                    sh '''
                        set -e
                        BASE=http://localhost:8080
                        KC=http://localhost:8180

                        TOKEN=$(curl -sf \
                            "$KC/realms/inventory/protocol/openid-connect/token" \
                            -d "client_id=inventory-frontend" \
                            -d "username=inv_admin" \
                            -d "password=$ADMIN_PASS" \
                            -d "grant_type=password" \
                            -d "scope=openid product:view product:manage stock:view stock:manage report:view audit:view user:manage" \
                            | jq -r '.access_token')

                        [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ] && echo "ERROR: JWT not obtained" && exit 1
                        echo "✓ JWT obtained"

                        check() {
                            local label="$1" expected="$2" url="$3"
                            local status
                            status=$(curl -s -o /dev/null -w "%{http_code}" \
                                -H "Authorization: Bearer $TOKEN" "$url")
                            if [ "$status" = "$expected" ]; then
                                echo "✓ $label → $status"
                            else
                                echo "✗ $label expected $expected, got $status"
                                exit 1
                            fi
                        }

                        curl -sf "$BASE/actuator/health" | jq .
                        check "GET /categories"                     200 "$BASE/categories"
                        check "GET /products"                       200 "$BASE/products"
                        check "GET /api/stock/movements"            200 "$BASE/api/stock/movements"
                        check "GET /api/reports/dashboard-metrics"  200 "$BASE/api/reports/dashboard-metrics"
                        check "GET /api/audit/stock-movements"      200 "$BASE/api/audit/stock-movements"

                        UNAUTH=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/products")
                        [ "$UNAUTH" = "401" ] && echo "✓ Unauthenticated rejected (401)" || (echo "✗ Expected 401, got $UNAUTH" && exit 1)

                        VIEWER_TOKEN=$(curl -s \
                            "$KC/realms/inventory/protocol/openid-connect/token" \
                            -d "client_id=inventory-frontend" \
                            -d "username=inv_viewer" \
                            -d "password=$VIEWER_PASS" \
                            -d "grant_type=password" \
                            -d "scope=openid product:view stock:view" \
                            | jq -r '.access_token // empty')

                        VIEWER_CREATE=$(curl -s -o /dev/null -w "%{http_code}" \
                            -X POST -H "Authorization: Bearer $VIEWER_TOKEN" \
                            -H "Content-Type: application/json" \
                            -d "{\"name\":\"test\",\"sku\":\"SKU-JENKINS-TEST\",\"price\":1.00,\"stock\":1,\"minimumStock\":0,\"categoryId\":1}" \
                            "$BASE/products")
                        [ "$VIEWER_CREATE" != "201" ] && [ "$VIEWER_CREATE" != "200" ] \
                            && echo "✓ Viewer blocked from POST /products ($VIEWER_CREATE)" \
                            || (echo "✗ Viewer should not create products, got $VIEWER_CREATE" && exit 1)

                        echo "✅ All API smoke tests passed"
                    '''
                }
            }
        }

        // E2E y Security Scan van despues del despliegue porque necesitan el sistema
        // en marcha, y comparten su misma condicion `when`: sin stack levantado no
        // tienen nada contra lo que correr.
        stage('E2E Tests — Playwright') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                // Se usa la imagen oficial de Playwright en lugar de instalar
                // navegadores en el agente: trae Chromium, Firefox y WebKit con sus
                // dependencias de sistema ya resueltas.
                sh '''
                    docker run --rm --network host \
                      -v "$WORKSPACE:/work" -w /work/e2e \
                      -e PLAYWRIGHT_JUNIT_OUTPUT_NAME=results.xml \
                      mcr.microsoft.com/playwright:v1.49.0-jammy \
                      sh -c "npm ci && npx playwright test --reporter=line,junit"
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'e2e/results.xml'
                    archiveArtifacts artifacts: 'e2e/playwright-report/**', allowEmptyArchive: true
                }
            }
        }

        stage('Security Scan — ZAP') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                withCredentials([string(credentialsId: 'kc-admin-password', variable: 'ADMIN_PASS')]) {
                    sh '''
                        set -e
                        # Escaneo autenticado y sembrado con el OpenAPI, igual que en
                        # staging.yml: sin token ZAP solo recibiria 401 y no encontraria
                        # nada, que es como pasar sin haber probado.
                        TOKEN=$(curl -sf \
                            "http://localhost:8180/realms/inventory/protocol/openid-connect/token" \
                            -d "client_id=inventory-frontend" \
                            -d "username=inv_admin" \
                            -d "password=$ADMIN_PASS" \
                            -d "grant_type=password" \
                            -d "scope=openid product:view product:manage stock:view stock:manage report:view audit:view user:manage" \
                            | jq -r '.access_token')

                        [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ] && echo "ERROR: sin JWT no hay escaneo autenticado" && exit 1

                        mkdir -p "$WORKSPACE/zap-out" && chmod 777 "$WORKSPACE/zap-out"

                        REPLACER="replacer.full_list(0)"
                        ZAP_OPTS="-config $REPLACER.description=auth"
                        ZAP_OPTS="$ZAP_OPTS -config $REPLACER.enabled=true"
                        ZAP_OPTS="$ZAP_OPTS -config $REPLACER.matchtype=REQ_HEADER"
                        ZAP_OPTS="$ZAP_OPTS -config $REPLACER.matchstr=Authorization"
                        ZAP_OPTS="$ZAP_OPTS -config $REPLACER.regex=false"
                        ZAP_OPTS="$ZAP_OPTS -config $REPLACER.replacement=Bearer $TOKEN"

                        docker run --rm --network host \
                          -v "$WORKSPACE/zap-out:/zap/wrk/:rw" \
                          zaproxy/zap-stable zap-api-scan.py \
                            -t http://localhost:8080/v3/api-docs \
                            -f openapi \
                            -r zap-report.html \
                            -J zap-report.json \
                            -z "$ZAP_OPTS"
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'zap-out/**', allowEmptyArchive: true
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo "✅ Pipeline completed successfully — Build #${env.BUILD_NUMBER}"
        }
        failure {
            echo "❌ Pipeline failed — Build #${env.BUILD_NUMBER}"
        }
        unstable {
            echo "⚠️  Pipeline unstable (coverage or test threshold) — Build #${env.BUILD_NUMBER}"
        }
    }
}
