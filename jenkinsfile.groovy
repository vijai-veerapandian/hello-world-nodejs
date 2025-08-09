pipeline {
    agent {
        label 'vm01'
    }
    
    tools {
        nodejs 'NodeJS-18'
    }

    environment {
        NODE_ENV = 'test'
        PORT = 3000
        DOCKER_IMAGE_NAME = 'vijaiv/hello-world-nodejs'
        IMAGE_TAG         = "v1.0.${env.BUILD_NUMBER}"
        CONTAINER_NAME    = "hello-world-test-${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out source code...'
                checkout scm
            }
        }
        
        stage('Install Dependencies') {
            steps {
                echo 'Installing Node.js dependencies...'
                sh 'rm -rf node_modules package-lock.json'
                sh 'npm install'
            }
        }
        
        stage('Generate Source SBOM') {
            steps {
                echo 'Generating SBOM from source dependencies...'
                sh 'npx @cyclonedx/cyclonedx-npm --output source-sbom.xml --output-format xml'
            }
            post {
                always {
                    archiveArtifacts artifacts: 'source-sbom.xml', allowEmptyArchive: true
                }
            }
        }
        
        stage('OWASP Analysis scanning & Unit Testing') {
            parallel {
                stage('Dependency Scanning (OWASP)') {
                    steps {
                        echo 'Running OWASP Dependency Check...'
                        withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_TOKEN')]) {
                            dependencyCheck(
                                additionalArguments: """
                                    --scan .
                                    --out .
                                    --format ALL
                                    --nvdApiKey ${NVD_API_TOKEN}
                                    --noupdate
                                    --suppression suppressions.xml
                                """,
                                odcInstallation: 'OWASP-dependency-check-10'
                            )
                        }
                    }
                    post {
                        always {
                            echo "Archiving OWASP Dependency-Check report..."
                            archiveArtifacts artifacts: 'dependency-check-report.html', allowEmptyArchive: true
                        }
                    }
                }
                    
                stage('Unit Testing') {
                    options { retry(2) }
                    steps {
                        echo 'Running tests...'
                        sh 'npm test'
                    }
                    post {
                        always {
                            junit testResults: 'test-results.xml'
                            echo 'Unit testing completed.'
                        }
                    }
                }

                stage('Security Audit (NPM)') {
                    steps {
                        echo 'Running NPM security audit...'
                        sh 'npm audit fix'
                        sh 'npm audit --audit-level=moderate'
                    }
                }
            }
        }

        stage('SAST (SonarQube)') {
            steps {
                echo "Running SonarQube analysis..."
                withSonarQubeEnv('sonarqube-server') {
                    script {
                        def scannerHome = tool 'sonarqube-scanner-7'
                        sh "'${scannerHome}/bin/sonar-scanner'"
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "Building Docker image with tag: ${env.IMAGE_TAG}"
                sh "docker build -t ${env.DOCKER_IMAGE_NAME}:${env.IMAGE_TAG} ."
                sh "docker tag ${env.DOCKER_IMAGE_NAME}:${env.IMAGE_TAG} ${env.DOCKER_IMAGE_NAME}:latest"
            }
        }

        stage('Trivy Vulnerability Scanning') {
            steps {
                echo "Scanning Docker image ${env.DOCKER_IMAGE_NAME}:${env.IMAGE_TAG} with Trivy..."
                sh '''
                   trivy image --exit-code 1 \
                        --severity CRITICAL \
                        --quiet \
                        --format json -o trivy-image-CRITICAL-results.json \
                        ${DOCKER_IMAGE_NAME}:${IMAGE_TAG}
                    
                    trivy image --exit-code 0 \
                        --severity LOW,MEDIUM,HIGH \
                        --quiet \
                        --format json -o image-trivy-MEDIUM-results.json \
                        ${DOCKER_IMAGE_NAME}:${IMAGE_TAG}

                    trivy image --scanners vuln --format cyclonedx --output image-sbom.xml \
                        --quiet \
                        ${DOCKER_IMAGE_NAME}:${IMAGE_TAG}   

                    trivy image --scanners vuln --format cyclonedx --output image-sbom.json \
                        --quiet \
                        ${DOCKER_IMAGE_NAME}:${IMAGE_TAG}
                    
                    '''
            }
            post {
                always {
                    echo "Archiving Trivy scan reports (JSON and HTML)..."
                    sh '''
                    trivy convert \
                        --format template --template "@/usr/local/share/trivy/templates/html.tpl" \
                        --output image-trivy-MEDIUM-results.html image-trivy-MEDIUM-results.json
                    trivy convert \
                        --format template --template "@/usr/local/share/trivy/templates/html.tpl" \
                        --output image-trivy-CRITICAL-results.html trivy-image-CRITICAL-results.json

                    trivy convert \
                        --format template --template "@/usr/local/share/trivy/templates/junit.tpl" \
                        --output image-trivy-MEDIUM-results.xml image-trivy-MEDIUM-results.json
                    trivy convert \
                        --format template --template "@/usr/local/share/trivy/templates/junit.tpl" \
                        --output image-trivy-CRITICAL-results.xml trivy-image-CRITICAL-results.json
                    '''
                    archiveArtifacts artifacts: 'image-sbom.*', fingerprint: true
                }
            }
        }

    stage('Push Docker Image') {
        steps {
            withCredentials([usernamePassword(
                credentialsId: 'dockerhub-credentials', 
                usernameVariable: 'DOCKER_USERNAME', 
                passwordVariable: 'DOCKER_PASSWORD'
            )]) {
            sh 'echo ${DOCKER_PASSWORD} | docker login -u ${DOCKER_USERNAME} --password-stdin'
            sh "docker push ${env.DOCKER_IMAGE_NAME}:${env.IMAGE_TAG}"
            sh "docker push ${env.DOCKER_IMAGE_NAME}:latest"
            }
        }
        post {
            always {
                sh 'docker logout'
            }
            }
        }
    }

    post {
        always {
            echo 'Cleaning up...'
            junit allowEmptyResults: true, stdioRetention: '', testResults: 'test-results.xml'
            junit allowEmptyResults: true, stdioRetention: '', testResults: 'dependency-check-junit.xml'
            
            script {
                def reports = [
                    [file: 'image-trivy-CRITICAL-results.html', name: 'Trivy Critical Vul Report'],
                    [file: 'image-trivy-MEDIUM-results.html',   name: 'Trivy Medium Vul Report'],
                    [file: 'dependency-check-report.html',    name: 'OWASP Dependency Check Report'],
                ]

                reports.each { report ->
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: './',
                        reportFiles: report.file,
                        reportName: report.name,
                        reportTitles: '',
                        useWrapperFileDirectly: true
                    ])
                }
            }
        }
        
        success {
            echo 'Pipeline completed successfully! ✅'
        }
        
        failure {
            echo 'Pipeline failed! ❌'
        }
        
        cleanup {
            // Clean workspace
            cleanWs()
        }
    }
}