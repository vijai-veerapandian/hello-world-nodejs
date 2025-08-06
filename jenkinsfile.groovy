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
        DOCKER_REGISTRY_USER = 'vijaiv'
        DOCKER_IMAGE_REPO    = 'hello-world-nodejs'
        DOCKER_IMAGE_NAME    = "${DOCKER_REGISTRY_USER}/${DOCKER_IMAGE_REPO}"
        // Dynamic tag: 'v1.0.x' for main branch, 'pr-branch-name-x' for others
        IMAGE_TAG            = (env.BRANCH_NAME == 'main') ? "v1.0.${env.BUILD_NUMBER}" : "pr-${env.BRANCH_NAME.replaceAll('/', '-')}-${env.BUILD_NUMBER}"
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
                sh 'npm ci' // Use 'ci' for reproducible builds
            }
        }
        
        stage('Code Quality & Security Analysis') {
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
                                    --noupdate
                                """,
                                odcInstallation: 'OWASP-dependency-check-10'
                            )
                        }
                    }
                    post {
                        always {
                            echo "Archiving OWASP Dependency-Check report..."
                            archiveArtifacts artifacts: 'dependency-check-report.html', allowEmptyArchive: true
                            archiveArtifacts artifacts: 'dependency-check-junit.xml', allowEmptyArchive: true
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
                            echo 'Unit testing completed.'
                            archiveArtifacts artifacts: 'test-results.xml', allowEmptyArchive: true
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

        stage('SAST (SonarQube) & Quality Gate') {
            steps {
                echo "Running SonarQube analysis..."
                withSonarQubeEnv('sonarqube-server') {
                    script {
                        def scannerHome = tool 'sonarqube-scanner-7'
                        sh "'${scannerHome}/bin/sonar-scanner'"
                    }
                }
            }
            post {
                success {
                    // Wait for SonarQube analysis to complete and check the Quality Gate status
                    timeout(time: 1, unit: 'HOURS') {
                        waitForQualityGate abortPipeline: true
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "Building Docker image: ${DOCKER_IMAGE_NAME}:${IMAGE_TAG}"
                sh "docker build -t ${DOCKER_IMAGE_NAME}:${IMAGE_TAG} ."
            }
        }

        stage('Scan Docker Image (Trivy)') {
            steps {
                echo "Scanning Docker image ${DOCKER_IMAGE_NAME}:${IMAGE_TAG} with Trivy..."
                // Fail build on CRITICAL vulnerabilities
                sh "trivy image --exit-code 1 --severity CRITICAL --quiet --format json -o trivy-critical.json ${DOCKER_IMAGE_NAME}:${IMAGE_TAG}"
                // Generate a report for other severities without failing the build
                sh "trivy image --exit-code 0 --severity UNKNOWN,LOW,MEDIUM,HIGH --quiet --format json -o trivy-report.json ${DOCKER_IMAGE_NAME}:${IMAGE_TAG}"
            }
            post {
                always {
                    echo "Generating and archiving Trivy scan reports..."
                    sh '''
                        trivy convert --format template --template "@/usr/local/share/trivy/templates/html.tpl" --output trivy-critical-report.html trivy-critical.json
                        trivy convert --format template --template "@/usr/local/share/trivy/templates/html.tpl" --output trivy-full-report.html trivy-report.json
                    '''
                    archiveArtifacts artifacts: 'trivy-critical-report.html, trivy-full-report.html', allowEmptyArchive: true
                }
            }
        }

        // =================================================================
        // Phase 2: CD Pipeline (Runs ONLY for the 'main' branch)
        // =================================================================

        stage('Push Docker Image') {
            when {
                branch 'main'
            }
            steps {
                echo "Pushing Docker image to registry..."
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials', 
                    usernameVariable: 'DOCKER_USERNAME', 
                    passwordVariable: 'DOCKER_PASSWORD'
                )]) {
                    sh 'echo "${DOCKER_PASSWORD}" | docker login -u "${DOCKER_USERNAME}" --password-stdin'
                    // Push the version-specific tag
                    sh "docker push ${DOCKER_IMAGE_NAME}:${IMAGE_TAG}"
                    // Also tag and push as 'latest'
                    sh "docker tag ${DOCKER_IMAGE_NAME}:${IMAGE_TAG} ${DOCKER_IMAGE_NAME}:latest"
                    sh "docker push ${DOCKER_IMAGE_NAME}:latest"
                }
            }
            post {
                always {
                    // Always log out from the registry
                    sh 'docker logout'
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when {
                branch 'main'
            }
            steps {
                echo "Deploy to Kubernetes is the next step! This is a placeholder."
                // Example deployment script:
                // withCredentials([kubeconfigContent(credentialsId: 'your-kubeconfig-id', variable: 'KUBECONFIG_CONTENT')]) {
                //     sh '''
                //         export KUBECONFIG=./kubeconfig
                //         echo "$KUBECONFIG_CONTENT" > $KUBECONFIG
                //         # Update the image in the deployment manifest using the new tag
                //         sed -i "s|image: .*|image: ${DOCKER_IMAGE_NAME}:${IMAGE_TAG}|g" k8s-deployment.yaml
                //         # Apply the updated manifest to the cluster
                //         kubectl apply -f k8s-deployment.yaml
                //     '''
                // }
            }
        }
    }

    post {
        always {
            echo 'Pipeline finished. Publishing reports and cleaning up...'
            
            // Consolidate all JUnit reports for a single test result view
            junit allowEmptyResults: true, testResults: 'test-results.xml, dependency-check-junit.xml'

            publishHTML([
                allowMissing: true, 
                alwaysLinkToLastBuild: true, 
                keepAll: true, 
                reportDir: './',
                reportFiles: 'trivy-image-CRITICAL-results.html', 
                reportName: 'Trivy Critical Report'
            ])

            publishHTML([
                allowMissing: true, 
                alwaysLinkToLastBuild: true, 
                keepAll: true, 
            ])

            publishHTML([
                allowMissing: true, 
                alwaysLinkToLastBuild: true, 
                keepAll: true, 
                reportDir: './',
                reportFiles: 'dependency-check-report.html', 
                reportName: 'OWASP Dependency Check Report',
                reportTitles: '', 
                useWrapperFileDirectly: true
            ])

            publishHTML([
                allowMissing: true, 
                alwaysLinkToLastBuild: true, 
                keepAll: true, 
                reportDir: './',
                reportFiles: 'sonarqube-report.html', 
                reportName: 'SonarQube Analysis Report',
                reportTitles: '', 
                useWrapperFileDirectly: true
            ])
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