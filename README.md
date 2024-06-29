# Model Converter Server
This is a very simple TCP server that allows clients to send a minecraft Java edition resourcepack, 
converts it to a minecraft Bedrock edition resourcepack using https://github.com/Kas-tle/java2bedrock.sh, 
and sends the result back to the client.

## Purpose
I am using this for my minecraft Custom Items plug-in, which needs to convert Java edition item models
to Bedrock edition item models. The java2bedrock.sh script can do this, but it requires several installation
steps, and does **not** work on Windows. This is problematic since my Custom Items editor runs on the
computers of end users, which usually run Windows. To work around this problem, I installed the
java2bedrock.sh script on my (Linux) VPS, and run this model converter server on the VPS. When my
Custom Items editor needs to convert a model, it will create a Java edition pack that has the model,
send it to my VPS, and extract the Bedrock model from the resulting Bedrock pack.

## Usage
If you want to run this project on your own computer or server for some reason, you should:
- install java2bedrock.sh (and all its dependencies)
- install Maven
- install JDK 8 or later
- clone/download this project
- run `mvn package`
- run `java -jar target/model-converter-server-1.0.jar path/to/java2bedrock.sh` (the path should point to the .sh file, not to the directory containing it)
