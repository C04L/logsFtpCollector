# Log Collector

App Java thu thập log files từ FTP/SFTP server, lưu trữ vào Cloudflare R2 (S3-compatible storage), và theo dõi thông tin file đã download qua SQLite database.

## Tính năng

- **Thu thập log tự động**: Quét và download file `.log` từ FTP/SFTP server theo chu kỳ 5 phút
- **Quét đệ quy**: Tự động quét tất cả thư mục con trong remote directory
- **Lưu trữ S3**: Upload file log lên Cloudflare R2 hoặc bất kỳ S3-compatible storage nào
- **Tracking database**: SQLite database theo dõi file đã download để tránh duplicate
- **Elasticsearch integration**: Cấu hình pipeline để parse log với Grok pattern
- **Docker support**: Containerized application sẵn sàng deploy

## Yêu cầu hệ thống

- Java 23 (GraalVM JDK Community) (nếu chạy local)
- Gradle 8.14+ (nếu chạy local)
- Docker & Docker Compose (nếu chạy bằng container)
- SFTP server với quyền truy cập
- Cloudflare R2 hoặc S3-compatible storage
- Elasticsearch (optional, cho log parsing)

## Cấu trúc

```
LogCollector/
├── src/main/java/org/coal/
│   ├── Main.java                 # Entry point
│   ├── Configuration.java        # Load config từ .env
│   ├── DatabaseConnection.java   # SQLite connection
│   ├── FTPConnection.java        # SFTP logic
│   ├── S3Connection.java         # S3 client setup
│   └── ElasticSearch.java        # Elasticsearch pipeline
├── build.gradle                  # Dependencies
├── Dockerfile                    # Container build
├── .env.example                  # Template cấu hình
└── README.md
```

## Cài đặt và cấu hình

Có thể chạy trực tiếp trên local hoặc sử dụng docker



### Clone repository

```bash
git clone <repository-url>
cd LogCollector
```

### Tạo file .env (Có thể không tạo nếu truyền trực tiếp môi trường vào docker)

Copy file `.env.example` thành `.env` và điền thông tin:

Chỉnh sửa file `.env`:

```properties
# FTP/SFTP Configuration
FTP_HOST=Địa-chỉ-server-sftp
FTP_PORT=22
FTP_USERNAME=username-đăng-nhập-vào-sftp
FTP_PASSWORD=mật-khẩu
# Đường dẫn đến thư mục lưu logs (sẽ tự quét qua các thư mục con để tìm file logs)
FTP_REMOTE_DIR=/home/ftpuser/logs

# Thông tin server S3 dùng để lưu trữ file log
# Có thể dùng Cloudflare R2 (10Gb miễn phí)
S3_ENDPOINT=https://your-account-id.r2.cloudflarestorage.com
S3_ACCESS_KEY=your-access-key-id
S3_SECRET_KEY=your-secret-access-key
S3_BUCKET=your-bucket-name
S3_REGION=auto

# Elasticsearch (optional)
ELASTICSEARCH_HOST=http://elasticsearch:9200

# Địa chỉ lưu file logs về máy
# Dùng filebeat track theo folder này để tự động upload lên elastic
# Nếu truyền thẳng file env vào docker thì không nên sửa dòng này
LOCAL_LOG_DIR=./logs
```
### CHẠY VỚI DOCKER (RECOMMEND)

 - **_**Yêu cầu Docker**_**

```bash
#Build docker image
docker build -t log-collector . --no-cache
```
#### Chạy bằng docker run
```bash
docker run -d \
  --name log-collector \
  --env-file .env \
  -v $(pwd)/data:/app/data \
  -v $(pwd)/logs:/app/logs \
  log-collector
```

#### Chạy bằng docker-compose
Với file .env để cùng thư mục với file docker-compose.yml
```yaml
services:
  #...Các services khác
  log-collector:
    image: log-collector
    container_name: log-collector
#    thay đổi ./logs (vế trái) thành thư mục bạn muốn dùng để lưu logs
    volumes:
      - ./logs:/app/logs
      - ./data:/app/data
    env_file:
      - .env
    restart: unless-stopped

```

Hoặc

```yaml
services:
  log-collector:
    image: log-collector
    container_name: log-collector
    volumes:
      - ./logs:/app/logs
      - ./data:/app/data
    environment:
      - FTP_HOST=${FTP_HOST}
      - FTP_PORT=${FTP_PORT:-22}
      - FTP_USERNAME=${FTP_USERNAME}
      - FTP_PASSWORD=${FTP_PASSWORD}
      - FTP_REMOTE_DIR=${FTP_REMOTE_DIR:-/}
      - S3_ENDPOINT=${S3_ENDPOINT}
      - S3_ACCESS_KEY=${S3_ACCESS_KEY}
      - S3_SECRET_KEY=${S3_SECRET_KEY}
      - S3_BUCKET=${S3_BUCKET}
      - S3_REGION=${S3_REGION:-auto}
      - S3_PREFIX=${S3_PREFIX:-logs/}
      - ELASTICSEARCH_HOST=${ELASTICSEARCH_HOST:-http://elasticsearch:9200}
    restart: unless-stopped
```

Hoặc truyền trực tiếp môi trường thay vì dùng env file
```yaml
services:
  log-collector:
    image: log-collector
    container_name: log-collector
#    thay đổi ./logs (vế trái) thành thư mục bạn muốn dùng để lưu logs

    volumes:
      - ./logs:/app/logs
      - ./data:/app/data
    environment:
        - FTP_HOST=your-ftp-server.com
        - FTP_PORT=22
        - FTP_USERNAME=your-ftp-username
        - FTP_PASSWORD=your-ftp-password
        - FTP_REMOTE_DIR=/logs
        - S3_ENDPOINT=https://your-account-id.r2.cloudflarestorage.com
        - S3_ACCESS_KEY=your-r2-access-key-id
        - S3_SECRET_KEY=your-r2-secret-access-key
        - S3_BUCKET=your-bucket-name
        - S3_REGION=auto
        - S3_PREFIX=logs/
        - ELASTICSEARCH_HOST=http://elasticsearch:9200
    depends_on:
      - elasticsearch
    networks:
      - logs-network
    restart: unless-stopped

```
### Ví dụ:

```
dir/
├── .env
└── docker-compose.yaml
```

```properties
#.env
FTP_HOST=192.168.1.1
FTP_PORT=22
FTP_USERNAME=sftpu
FTP_PASSWORD=123456
FTP_REMOTE_DIR=/home/ftpuser/logs/9c65f96bdeee/
# Cloudflare R2 (S3-compatible) Configuration
S3_ENDPOINT=https://0bd70c0.r2.cloudflarestorage.com
S3_ACCESS_KEY=0xxxx000000x0x000000000x0x0c7sad98as7d
S3_SECRET_KEY=0xxxx000000x0x000000000x0x0cxx98xxx098
S3_BUCKET=logs
S3_REGION=auto
S3_PREFIX=logs/
LOCAL_LOG_DIR=/home/user/logs/
# Elasticsearch Configuration
ELASTICSEARCH_HOST=http://localhost:9200
```

```yaml
# docker-compose.yaml
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
      - cluster.routing.allocation.disk.threshold_enabled=false
      - action.destructive_requires_name=false
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    networks:
      - logs-network

  kibana:
    image: docker.elastic.co/kibana/kibana:8.11.0
    container_name: kibana
    ports:
      - "5601:5601"
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    depends_on:
      - elasticsearch
    networks:
      - logs-network

  filebeat:
    image: docker.elastic.co/beats/filebeat:8.11.0
    container_name: filebeat
    user: root
    volumes:
      - ./filebeat.yml:/usr/share/filebeat/filebeat.yml:ro
      - ./logs:/logs:ro
      - filebeat_data:/usr/share/filebeat/data
    command: filebeat -e -strict.perms=false
    depends_on:
      - elasticsearch
    networks:
      - logs-network

  log-collector:
    image: log-collector
    container_name: log-collector
    volumes:
      - ./logs:/app/logs
      - ./data:/app/data
    environment:
      # FTP Configuration
      - FTP_HOST=${FTP_HOST}
      - FTP_PORT=${FTP_PORT:-21}
      - FTP_USERNAME=${FTP_USERNAME}
      - FTP_PASSWORD=${FTP_PASSWORD}
      - FTP_REMOTE_DIR=${FTP_REMOTE_DIR:-/}
      - FTP_DELETE_AFTER_DOWNLOAD=${FTP_DELETE_AFTER_DOWNLOAD:-false}
      # S3/R2 Configuration
      - S3_ENDPOINT=${S3_ENDPOINT}
      - S3_ACCESS_KEY=${S3_ACCESS_KEY}
      - S3_SECRET_KEY=${S3_SECRET_KEY}
      - S3_BUCKET=${S3_BUCKET}
      - S3_REGION=${S3_REGION:-auto}
      - S3_PREFIX=${S3_PREFIX:-logs/}
      # Elasticsearch Configuration
      - ELASTICSEARCH_HOST=${ELASTICSEARCH_HOST:-http://elasticsearch:9200}
      # Polling Configuration
      - POLLING_INTERVAL_SECONDS=${POLLING_INTERVAL_SECONDS:-300}
    depends_on:
      - elasticsearch
    networks:
      - logs-network
    restart: unless-stopped

volumes:
  es_data:
  filebeat_data:

networks:
  logs-network:
    driver: bridge

```

### CHẠY LOCAL

- _Yêu cầu JDK 23, Java 23_

#### Build với Gradle

```bash
./gradlew build
```


#### Chạy trực tiếp với Java

```bash
java -jar build/libs/LogCollector-1.0-SNAPSHOT.jar
```

## Dependencies chính

- **JSch 2.27.3**: SFTP client
- **AWS SDK S3 2.30.6**: S3-compatible storage
- **SQLite JDBC 3.44.1**: Database driver
- **Dotenv Java 3.2.0**: Environment configuration
- **Jackson 2.16.0**: JSON processing
- **Lombok 9.0.0**: Reduce boilerplate code

## License


**Lưu ý**: Ứng dụng này chạy liên tục trong background. Để dừng:

```bash
# Docker
docker stop log-collector

# Docker Compose
docker-compose down

# Java process
# Ctrl+C hoặc kill process ID
```