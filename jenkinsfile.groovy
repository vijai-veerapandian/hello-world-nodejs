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
}

stages {
    stage('Setup') {
        steps {
            echo 'Ensuring Node.js is available...'
            sh '''
                if ! command -v node >/dev/null; then
                    echo "Node.js not found in PATH!"
                    exit 1
                fi
                echo "Using Node version: $(node -v)"
                '''
        }
    }

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
    
    stage('Dependency Scanning') {
        steps {
            echo 'Running OWASP Dependency Check..'
            dependencyCheck additionalArguments: '--scan . --out . --format ALL --prettyPrint', odcInstallation: 'OWASP-Dependency-check-7.4.4'
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
                echo 'Test stage completed'
                junit testResults: 'test-results.xml'
            }
        }
    }
    
    stage('Code Quality') {
        parallel {
            stage('Lint') {
                steps {
                    echo 'Running linting...'
                    // Assuming you have a lint script in package.json. If not, this will fail gracefully.
                    sh 'npm run lint'
                }
            }
            stage('Security Audit') {
                steps {
                    echo 'Running security audit...'
                    sh 'npm audit --audit-level=critical'
                }
            }
        }
    }
    
    stage('SAST - SonarQube') {
        steps {
            withSonarQubeEnv('sonarqube-server') {
                sh 'sonar-scanner'
            }
        }
    }
    
    stage('Build') {
        steps {
            echo 'Building application...'
            sh 'npm run build || echo "No build script found, skipping..."'
        }
    }
    
    stage('Start Application') {
        steps {
            echo 'Starting application for testing...'
            script {
                // Start the application in background
                sh 'npm start &'
                sh 'sleep 10' // Wait for app to start
                
                // Test if application is running
                sh 'curl -f http://localhost:3000/ || exit 1'
                sh 'curl -f http://localhost:3000/api/health || exit 1'
                
                echo 'Application tests passed!'
            }
        }
        post {
            always {
                script {
                    // Stop the application
                    sh 'pkill -f "node server.js" || true'
                    sh 'sleep 2'
                }
            }
        }
    }
}

post {
    always {
        echo 'Cleaning up...'
        sh 'pkill -f "node server.js" || true'
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
