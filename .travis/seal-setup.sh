# Sealed build environment setup, sourced by .travis.yml (before_install) and by
# .github/workflows/seal-build.yml. Do not use `set -e`: this file is sourced.
#
# 1. Zulu OpenJDK 7 ($SEAL_JDK) - Netty 3.7 targets Java 5 and its enforcer rule
#    requires JDK >= 1.7; the published jar was built with JDK 1.7.0_25. The
#    current JAVA_HOME (a JDK 8) donates its cacerts so JDK 7 can reach Maven
#    Central over TLS 1.2.
# 2. Maven $SEAL_MAVEN.
# 3. MAVEN_OPTS: TLSv1.2 for JDK 7, plus .travis/seal-java.security, which
#    re-allows the 512-bit RSA certificate of the securechat example keystore
#    used by the *SocketSslEchoTest tests (surefire runs with forkMode=never, so
#    the property reaches the tests).
SEAL_JDK=${SEAL_JDK:-zulu7.56.0.11-ca-jdk7.0.352-linux_x64}
SEAL_MAVEN=${SEAL_MAVEN:-3.8.8}
SEAL_DIR=${SEAL_DIR:-$PWD}
SEAL_DONOR_CACERTS="$JAVA_HOME/jre/lib/security/cacerts"
[ -f "$SEAL_DONOR_CACERTS" ] || SEAL_DONOR_CACERTS="$JAVA_HOME/lib/security/cacerts"

mkdir -p /tmp/seal-jdk || return 1
curl -fsSL --retry 5 --retry-delay 10 -o /tmp/seal-jdk.tar.gz "https://cdn.azul.com/zulu/bin/$SEAL_JDK.tar.gz" || return 1
tar -xzf /tmp/seal-jdk.tar.gz -C /tmp/seal-jdk --strip-components=1 || return 1
if [ -f "$SEAL_DONOR_CACERTS" ]; then
  cp -f "$SEAL_DONOR_CACERTS" /tmp/seal-jdk/jre/lib/security/cacerts || return 1
fi
export JAVA_HOME=/tmp/seal-jdk
export PATH="$JAVA_HOME/bin:$PATH"

curl -fsSL --retry 5 --retry-delay 10 -o /tmp/seal-maven.tar.gz \
  "https://archive.apache.org/dist/maven/maven-3/$SEAL_MAVEN/binaries/apache-maven-$SEAL_MAVEN-bin.tar.gz" || return 1
tar -xzf /tmp/seal-maven.tar.gz -C /tmp || return 1
export M2_HOME="/tmp/apache-maven-$SEAL_MAVEN"
export PATH="$M2_HOME/bin:$PATH"
export MAVEN_OPTS="-Xmx1g -Dhttps.protocols=TLSv1.2 -Djava.security.properties=$SEAL_DIR/.travis/seal-java.security"

java -version
mvn -version
