pipeline {
    agent any

    triggers {
        pollSCM('*/5 * * * *')
    }

    parameters {
        booleanParam(name: 'FORCE_BACKEND', defaultValue: false, description: 'Force backend build regardless of Git changes')
        booleanParam(name: 'FORCE_KIDS_APP', defaultValue: false, description: 'Force kids-app build regardless of Git changes')
        booleanParam(name: 'FORCE_PARENT_APP', defaultValue: false, description: 'Force parent-app build regardless of Git changes')
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
                    env.PARENT_CHANGED = sh(
                        script: "git diff --name-only HEAD~1 | grep '^parent-app/\\|^shared/' || true",
                        returnStdout: true).trim()
                }
            }
        }

        stage('Backend') {
            when { expression { env.BACKEND_CHANGED || params.FORCE_BACKEND } }
            steps {
                dir('backend') {
                    sh './gradlew clean bootJar'
                    sh 'docker compose -f /var/kidstune-data/docker-compose.yml -p kidstune down'
                    sh 'cp build/libs/kidstune.jar /var/kidstune-data/docker'
                    sh 'docker compose -f /var/kidstune-data/docker-compose.yml -p kidstune build'
                    sh 'docker compose -f /var/kidstune-data/docker-compose.yml -p kidstune up -d'
                }
            }
        }
        stage('Kids App') {
            when { expression { env.KIDS_CHANGED || params.FORCE_KIDS_APP } }
            steps {
                dir('kids-app') {
                    sh './gradlew test'
                    sh './gradlew assembleRelease'
                    // Store APK as artifact for sideloading
                }
            }
        }
        stage('Parent App') {
            when { expression { env.PARENT_CHANGED || params.FORCE_PARENT_APP } }
            steps {
                dir('parent-app') {
                    sh './gradlew test'
                    sh './gradlew assembleRelease'
                }
            }
        }
    }
}