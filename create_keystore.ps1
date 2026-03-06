$dname = "CN=shizuku, OU=deskpet, O=shizuku, L=Unknown, ST=Unknown, C=CN"
keytool -genkey -v -keystore "e:\Android\deskpet\shizuku-release.jks" -keyalg RSA -keysize 2048 -validity 10000 -alias shizuku -storepass shizuku123 -keypass shizuku123 -dname $dname
