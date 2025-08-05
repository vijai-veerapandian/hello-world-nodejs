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
            sh 'npm ci'
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
                                --nvdApiKey '${NVD_API_TOKEN}'
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
                    sh 'npm audit --audit-level=critical'
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


    stage('Trivy scan Docker Image') {
        steps {
            echo "Scanning Docker image ${env.DOCKER_IMAGE_NAME}:${env.IMAGE_TAG} with Trivy..."
            sh """
               trivy image --exit-code 1 \\
                    --severity CRITICAL \\
                    --quiet \\
                    --format json -o trivy-image-results.json \\
                    ${env.DOCKER_IMAGE_NAME}:${env.IMAGE_TAG}
            """

            echo "Converting Trivy JSON report to HTML..."
            sh '''
                trivy convert --format template --template @contrib/html.tpl -o trivy-report.html trivy-image-results.json
            '''
        }
        post {
            always {
                echo "Archiving Trivy scan reports (JSON and HTML)..."
                archiveArtifacts artifacts: 'trivy-image-results.json, trivy-report.html', allowEmptyArchive: true
            }
        }
    }

    stage('Push Docker Image') {
        when { expression { false } }
        steps {
            withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                sh "echo ${DOCKER_PASSWORD} | docker login -u ${DOCKER_USERNAME} --password-stdin"
                sh "docker push ${env.DOCKER_IMAGE_NAME} --all-tags"
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
