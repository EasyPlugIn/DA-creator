echo "compile start"
echo "setup env vars"
export JAVA_HOME=/usr/local/openjdk7
export ANDROID_HOME=/root/easyconnect/android-sdk
echo "Session: $1"
# cd workspace/$1/CICBrick2OpenMTC
cd workspace/$1/Bulb
printf "pwd: "
pwd
./gradlew build
