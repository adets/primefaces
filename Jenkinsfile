pipeline {
    agent any
    
    tools {
        jdk 'Java 11'
    }
	
    parameters {
        booleanParam(name: 'releaseBuild', defaultValue: false, description: 'Is this a release build?')
        string(name: 'releaseVersion', defaultValue: '', description: 'Artifact version for the release build')
        string(name: 'developmentVersion', defaultValue: '', description: 'Artifact version for further development after release build')
    }
    
    stages {
        stage('Set Version') {
            when {
                expression {
                	return params.releaseBuild
                }
            }
            steps {
                sh "mvn -B versions:set -DnewVersion=${params.releaseVersion}"
            }
        }
        
        stage('Build') {
            steps {
                sh 'mvn -B clean package -DskipTests'
            }
        }
        
        stage('Test') { 
            steps {
                sh 'mvn -B test' 
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true 
                }
            }
        }
        
        stage('Deploy') {
            steps {
                sh 'mvn -B clean deploy -DskipTests'
             }
        }
        
        stage('Release') {
            when {
                expression {
                	return params.releaseBuild
                }
            }
            steps {
                sh "mvn -B scm:checkin -Dmessage='Increment project version to ${params.releaseVersion}'"
                sh 'mvn -B scm:tag'
                sh "mvn -B versions:set -DnewVersion=${params.developmentVersion}"
                sh "mvn -B scm:checkin -Dmessage='Increment project version to ${params.developmentVersion}'"
            }
        }
    }
}
