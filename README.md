<p align='center'>
    <img src="https://capsule-render.vercel.app/api?type=soft&color=ff4500&height=200&section=header&text=Welcome%20to%20LOGEAT%20👋&fontSize=50&animation=fadeIn&fontColor=ffffff"/>
</p>

<p align='center'>
  <a>
    <img src="https://img.shields.io/badge/GitHub-100000?style=for-the-badge&logo=github&logoColor=white"/>
  </a>
  <a>
    <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=for-the-badge&logo=github-actions&logoColor=white"/>
  </a>
    <a>
        <img src="https://img.shields.io/badge/Postman-FF6C37?style=for-the-badge&logo=postman&logoColor=white"/>
    </a>
   
  <a>
    <img src="https://img.shields.io/badge/Slack-4A154B?style=for-the-badge&logo=slack&logoColor=white"/>
  </a>

<br>
    
  <a>
    <img src="https://img.shields.io/badge/Docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white"/>
  </a>
  <a>
    <img src="https://img.shields.io/badge/Redis-%23DD0031.svg?&style=for-the-badge&logo=redis&logoColor=white"/>
  </a>
  <a>
    <img src="https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white"/>
  </a>
  <a>
    <img src="https://img.shields.io/badge/Amazon_AWS-232F3E?style=for-the-badge&logo=amazon-aws&logoColor=white"/>
  </a>
  <a>
    <img src="https://img.shields.io/badge/MariaDB-003545?style=for-the-badge&logo=mariadb&logoColor=white"/>
  </a>

<br>
	
    
</p>



<p align='center'>
  <b>Team: LOGEAT<b>
  <br>
    👨‍💻 김영광, 김종원, 장은지, 박성준, 이종표 
</p>

## 목차
- [아키텍처 설계서](#설계서)
- [아키텍처 상세 문서](#상세문서)
- [구성스크립트](#구성스크립트)
- [테스트 결과](#테스트-결과)

---

## 설계서

![아키텍처](https://github.com/beyond-sw-camp/be03-4th-2team-logeat-backend/assets/97268373/137d4c71-386d-4d60-bfb8-5c3bf37c79a8)


## 상세문서
 - 사용자가 웹 브라우저를 통해 www.logeat.shop 주소로 접속합니다.
    
  - 사용자의 요청은 DNS 서버를 통해 www.logeat.shop 의 IP 주소로 해석됩니다.
    
  - 해석된 IP 주소는 Amazon CloudFront로 연결됩니다. CloudFront는 사용자에게 더 빠른 콘텐츠 전송을 위해 캐싱된 데이터를 제공합니다.
    
  - CloudFront는 요청을 Amazon S3 버킷으로 전달합니다. S3는 정적 웹 콘텐츠 호스팅에 사용됩니다.
    
  - 브라우저에서의 API요청 이후 AWS의 로드 밸런서를 거쳐 Nginx로 이동합니다.
    
  - Nginx는 로드 밸런싱 기능을 제공합니다. 여기에서 Nginx는 블루-그린 배포 방식을 이용하여, 요청을 현재 활성화된(EC2 인스턴스 내에 있는) Docker 컨테이너 그룹(블루 또는 그린)으로 안내합니다.
    
  - 해당 Docker 컨테이너들은 Spring Boot 애플리케이션을 실행하고 있으며, 사용자의 요청을 처리합니다.
    
  - Spring Boot 애플리케이션은 API 요청 처리를 위해 Amazon RDS와 Redis와 같은 데이터 스토리지 및 캐싱 서비스와 통신합니다.
    
  - API 처리가 완료된 후, 응답은 사용자에게 전달됩니다.
    
- 블루-그린 배포
  
  - 블루/그린 배포는 소프트웨어 개발에서 사용되는 무중단 배포 방식 중 하나로, 새로운 버전의 애플리케이션을 기존 버전과 완전히 분리된 환경(그린)에 배포한 다음, 모든 것이 정상적으로 작동하는 것을 확인한 후에 트래픽을 새로운 환경으로 전환함으로써 사용자에게 서비스 중단 없이 업데이트를 제공하는 방식입니다.

## 구성스크립트

<details>
<summary><b>logeat.yml</b></summary>
<div markdown="1">
	
```yaml
name: Deploy to Ec2 With Docker Blue/Green
on:
  push:
    branches:
      - master
jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2

      - name: Set YML
        run: |
          mkdir -p src/main/resources
          echo "${{ secrets.APPLICATION_YML }}" | base64 --decode > src/main/resources/application.yml
          find src
          echo "${{ secrets.JWT_YML }}" | base64 --decode > src/main/resources/jwt.yml
          find src
      
      - name: Build with Gradle  
        working-directory: ./  
        run: |  
          chmod +x ./gradlew  
          ./gradlew bootJar 

      - name: Build Docker image
        working-directory: ./
        run: |
          docker build -t ticketpaper2/logeat-backend:blue -f Dockerfile .
          docker build -t ticketpaper2/logeat-backend:green -f Dockerfile .
      
      - name: DockerHub Login
        uses: docker/login-action@v1
        with:
          username: ${{ secrets.DOCKER_NAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}

      - name: Push Docker Images to DockerHub
        run: |
          docker push ticketpaper2/logeat-backend:blue
          docker push ticketpaper2/logeat-backend:green
      - name: EC2 SSH Login and Docker run with Blue/Green Deployment
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USERNAME }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            ./deploy.sh
            
            echo "Deployed $NEW_VERSION version"
            # 이전 버전의 이미지 삭제 또는 보관 로직 추가 (선택적)
```
 </div>

</details>
<details>
<summary><b>Dockerfile</b></summary>
<div markdown="1">
	
```Dockerfile
FROM openjdk:11 as stage1
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
RUN chmod +x ./gradlew
RUN ./gradlew bootJar
FROM openjdk:11
WORKDIR /app
COPY --from=stage1 /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
```
 </div>

</details>


## 테스트 결과
<details> <summary><b>결과</b></summary>   
  <div markdown="1"> 
  </div>
</details>



