pipeline {
    agent any

    triggers {
        pollSCM('*/5 * * * *')
    }

    environment {
        // Testcontainers on Linux: use the Docker socket mounted into the Jenkins container.
        DOCKER_HOST                          = 'unix:///var/run/docker.sock'
        TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE = '/var/run/docker.sock'
        // Disable Ryuk if it cannot bind back to the Jenkins container network.
        TESTCONTAINERS_RYUK_DISABLED         = 'true'
    }

    parameters {
        booleanParam(name: 'FORCE_BACKEND', defaultValue: false, description: 'Force backend build regardless of Git changes')
        booleanParam(name: 'FORCE_KIDS_APP', defaultValue: false, description: 'Force kids-app build regardless of Git changes')
    }

    stages {
        stage('Clone sources') {
            steps {
                git credentialsId: 'github-ssh',
                    url: 'git@github.com:v3rtumnus/kidstune.git',
                    branch: 'main'
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    env.BACKEND_CHANGED = sh(
                        script: "git diff --name-only HEAD~1 | grep '^backend/' || true",
                        returnStdout: true).trim()
                    env.KIDS_CHANGED = sh(
                        script: "git diff --name-only HEAD~1 | grep '^kids-app/\\|^shared/' || true",
                        returnStdout: true).trim()
                }
            }
        }

        stage('Backend') {
            when { expression { env.BACKEND_CHANGED || params.FORCE_BACKEND } }
            steps {
                dir('backend') {
                    sh 'chmod +x gradlew'
                    sh './gradlew clean test bootJar'
                    sh 'docker compose -f /var/kidstune-data/docker-compose.yml -p kidstune down'
                    sh 'cp build/libs/kidstune.jar /var/kidstune-data/docker'
                    sh 'docker compose -f /var/kidstune-data/docker-compose.yml -p kidstune build'
                    sh 'docker compose -f /var/kidstune-data/docker-compose.yml -p kidstune up -d'
                }
            }
            post {
                always {
                    junit 'backend/build/test-results/test/*.xml'
                }
            }
        }
        stage('Playwright E2E') {
            when { expression { env.BACKEND_CHANGED || params.FORCE_BACKEND } }
            steps {
                dir('backend') {
                    sh './gradlew playwrightInstall'
                    sh './gradlew e2eTest'
                }
            }
            post {
                always {
                    junit 'backend/build/test-results/e2eTest/*.xml'
                }
            }
        }
        stage('Kids App') {
            when { expression { env.KIDS_CHANGED || params.FORCE_KIDS_APP } }
            steps {
                dir('kids-app') {
                    sh 'chmod +x gradlew'
                    sh './gradlew test'
                    sh './gradlew assembleRelease'
                }
            }
            post {
                always {
                    junit 'kids-app/build/test-results/**/*.xml'
                }
                success {
                    archiveArtifacts artifacts: 'kids-app/build/outputs/apk/release/*.apk',
                                     fingerprint: true
                }
            }
        }
    }
}