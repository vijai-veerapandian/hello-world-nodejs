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
    // Define your Docker image name. Replace with your Docker Hub username or registry path.
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
    
    stage('Analysis & Testing') {
        parallel {
            stage('Dependency Scanning (OWASP)') {
                steps {
                    echo 'Running OWASP Dependency Check...'
                    // Use withCredentials to securely inject the NVD API key
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
                        // Archive test results if you generate them
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
            // The withSonarQubeEnv wrapper injects the server URL and authentication token.
            // Since the scanner is not being found in the PATH, we will get its location
            // from the tool configuration and call it with an absolute path. This is the most reliable method.
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

    stage('Test Built Image') {
        steps {
            script {
                echo "Starting container ${env.CONTAINER_NAME} from image ${env.DOCKER_IMAGE_NAME}:${env.IMAGE_TAG} for testing..."
                // Run the container in detached mode and give it a name for easy cleanup
                sh "docker run -d --name ${env.CONTAINER_NAME} -p 3000:3000 ${env.DOCKER_IMAGE_NAME}:${env.IMAGE_TAG}"
                
                // Wait for the container to become healthy by polling its health check status.
                // This is more robust than a fixed sleep timer.
                echo "Waiting for container to become healthy..."
                timeout(time: 2, unit: 'MINUTES') {
                    while (true) {
                        // Inspect the container's health status
                        def healthStatus = sh(script: "docker inspect --format '{{.State.Health.Status}}' ${env.CONTAINER_NAME}", returnStdout: true).trim()
                        if (healthStatus == 'healthy') {
                            echo "Container is healthy!"
                            break
                        }
                        if (healthStatus == 'unhealthy') {
                            error("Container failed its health check.")
                        }
                        echo "Container status is '${healthStatus}'. Waiting..."
                        sleep 5 // Wait 5 seconds before polling again
                    }
                }
                
                echo "Testing container endpoints..."
                sh "curl -f --retry 3 --retry-delay 5 http://localhost:3000/ || exit 1"
                sh "curl -f --retry 3 --retry-delay 5 http://localhost:3000/api/health || exit 1"
                
                echo 'Container tests passed!'
            }
        }
        post {
            always {
                script {
                    echo "Stopping and removing container ${env.CONTAINER_NAME}..."
                    // Stop and remove the test container, ignoring errors if it doesn't exist
                    sh "docker stop ${env.CONTAINER_NAME} || true"
                    sh "docker rm ${env.CONTAINER_NAME} || true"
                }
            }
        }
    }

    stage('Scan Docker Image with Trivy') {
        steps {
            echo "Scanning Docker image ${env.DOCKER_IMAGE_NAME}:${env.IMAGE_TAG} with Trivy..."
            // This step requires Trivy to be installed on the agent 'vm01'.
            // It will fail the build if Trivy finds any HIGH or CRITICAL vulnerabilities.
            // You can adjust the --severity flag as needed (e.g., UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL).
            // We scan for vulnerabilities and secrets, outputting the raw data to a JSON file.
            sh '''
               trivy image --exit-code 1 \\
                    --severity HIGH,CRITICAL,LOW,MEDIUM \\
                    --scanners vuln,secret \\
                    --format json -o trivy-image-results.json \\
                    ${env.DOCKER_IMAGE_NAME}:${env.IMAGE_TAG}
            '''

            echo "Converting Trivy JSON report to HTML..."
            // Use Trivy's built-in HTML template to convert the JSON results to a readable HTML report.
            sh '''
                trivy convert --format template --template "@contrib/html.tpl" -o trivy-report.html trivy-image-results.json
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
        // This stage is disabled by default. 
        // Enable it by changing the expression or tying it to a specific branch, e.g., when { branch 'main' }
        // It requires Docker Hub credentials configured in Jenkins with the ID 'dockerhub-credentials'.
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
