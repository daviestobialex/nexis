# To change this license header, choose License Headers in Project Properties.
# To change this template file, choose Tools | Templates
# and open the template in the editor.
FROM azul/zulu-openjdk:22

EXPOSE 9004

ADD target/instance-core-0.0.1.jar service.jar
ADD src/main/nexus/manifest.json manifest.json

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xss512k", "-XX:MaxRAM=100m", "-cp", "service.jar", "org.nexis.example.NexusStartInstanceExample"]
