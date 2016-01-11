echo "building morsensor"
echo "setup env vars"
export JAVA_HOME=/usr/local/openjdk7
export ANDROID_HOME=/root/easyconnect/android-sdk
# cd workspace/$1/CICBrick2OpenMTC
cd morsensor
printf "pwd: "
pwd
./gradlew build
