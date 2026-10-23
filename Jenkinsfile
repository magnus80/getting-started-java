def compileOutcome = 'skipped'
def testsOutcome = 'skipped'

pipeline {
    agent { label 'java-jdk21' }

    parameters {
        string(name: 'BROWSER_SIZE', defaultValue: '1920x1080', description: 'Browser window size, WIDTHxHEIGHT')
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
    }

    environment {
        CHROME_VERSION = '153.0.8010.52'
        ALLURE_VERSION = '2.46.1'
        ALLURE_SHA256 = 'd25c519bbde940dc953cff8901130ce18b73013dc19a8f8b90b8b4b1c5eb252f'
        WINDOW_SIZE = "${params.BROWSER_SIZE ?: '1920x1080'}"
    }

    stages {
        stage('Set up Chrome and Allure CLI') {
            steps {
                sh '''
                    set -eu
                    tools="$WORKSPACE/.ci-tools"
                    rm -rf "$tools" && mkdir -p "$tools"
                    base="https://storage.googleapis.com/chrome-for-testing-public/$CHROME_VERSION/linux64"
                    curl -fsSL -o "$tools/chrome.zip" "$base/chrome-linux64.zip"
                    curl -fsSL -o "$tools/chromedriver.zip" "$base/chromedriver-linux64.zip"
                    curl -fsSL -o "$tools/allure.zip" \
                      "https://repo.maven.apache.org/maven2/io/qameta/allure/allure-commandline/$ALLURE_VERSION/allure-commandline-$ALLURE_VERSION.zip"
                    echo "$ALLURE_SHA256  $tools/allure.zip" | sha256sum --check --strict
                    for archive in chrome chromedriver allure; do unzip -q "$tools/$archive.zip" -d "$tools"; done
                    "$tools/chrome-linux64/chrome" --version | grep -F "$CHROME_VERSION"
                    "$tools/chromedriver-linux64/chromedriver" --version | grep -F "$CHROME_VERSION"
                '''
            }
        }

        stage('Compile tests') {
            steps {
                script {
                    compileOutcome = 'failure'
                    sh './gradlew testClasses --console=plain'
                    compileOutcome = 'success'
                }
            }
        }

        stage('Run UI tests') {
            steps {
                script {
                    def exitCode = sh(returnStatus: true, script: '''
                        tools="$WORKSPACE/.ci-tools"
                        ./gradlew test --console=plain \
                          -Dheadless=true \
                          "-DbrowserSize=$WINDOW_SIZE" \
                          "-Dselenide.browserBinary=$tools/chrome-linux64/chrome" \
                          "-Dwebdriver.chrome.driver=$tools/chromedriver-linux64/chromedriver" \
                          "-Dchromeoptions.args=--no-sandbox"
                    ''')
                    testsOutcome = exitCode == 0 ? 'success' : 'failure'
                    junit testResults: 'build/test-results/test/*.xml'
                    if (exitCode != 0 && currentBuild.currentResult != 'UNSTABLE') {
                        error("Gradle test task failed with exit code ${exitCode} although no JUnit test failed")
                    }
                }
            }
        }

        stage('Generate Allure report') {
            steps {
                sh '''
                    set -eu
                    ls build/allure-results/*-result.json >/dev/null 2>&1 || { echo "No Allure test results"; exit 1; }
                    "$WORKSPACE/.ci-tools/allure-$ALLURE_VERSION/bin/allure" generate build/allure-results -c -o build/allure-report
                '''
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'build/allure-results/**, build/allure-report/**, build/test-results/test/**, build/reports/tests/test/**',
                    allowEmptyArchive: true
            script {
                def jobStatus = currentBuild.currentResult == 'ABORTED' ? 'cancelled' :
                        (currentBuild.currentResult == 'SUCCESS' ? 'success' : 'failure')
                def notifyCommand = "./gradlew notifyTelegram --console=plain -PjobStatus=${jobStatus} " +
                        "-PcompileOutcome=${compileOutcome} -PtestsOutcome=${testsOutcome} " +
                        '"-PciProject=$JOB_NAME" -PciName=Jenkins "-Pbranch=${GIT_BRANCH:-unknown}" ' +
                        '"-Pcommit=${GIT_COMMIT:-unknown}" "-PrunUrl=$BUILD_URL"'
                try {
                    withCredentials([
                            string(credentialsId: 'TELEGRAM_BOT_TOKEN', variable: 'TELEGRAM_BOT_TOKEN'),
                            string(credentialsId: 'TELEGRAM_CHAT_ID', variable: 'TELEGRAM_CHAT_ID'),
                    ]) {
                        sh notifyCommand
                    }
                } catch (Exception e) {
                    if (!(e.message ?: '').contains('Could not find credentials entry')) {
                        throw e
                    }
                    echo "WARNING: Telegram credentials are not configured: ${e.message}"
                    sh notifyCommand
                }
            }
        }
    }
}
