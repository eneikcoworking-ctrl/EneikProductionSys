# Архитектурный каркас проекта (Project Template)

Этот документ описывает стандартную архитектуру монорепозитория, стек технологий и сквозные примеры кода для создания новых проектов.

## 1. Стек технологий
- **Backend:** Java 21, Spring Boot 3.x (Spring Web, Spring Data JPA, Spring Security).
- **Frontend:** SvelteKit (с TypeScript, TailwindCSS, адаптером под Node или static).
- **Database:** PostgreSQL + Flyway/Liquibase.
- **Storage:** S3 (MinIO локально) — загрузка файлов через Presigned URL.
- **DevOps:** Docker, Docker Compose.

---

## 2. Структура проекта (Monorepo)

```
.
├── docker-compose.yml       # Инфраструктура (PostgreSQL, MinIO)
├── pom.xml                  # Зависимости Java Backend (Maven)
├── src/
│   └── main/
│       └── java/
│           └── com/example/app/
│               ├── config/      # Конфигурации (Security, S3 Client, CORS)
│               ├── controller/  # REST API endpoints (только прием/отдача DTO)
│               ├── dto/         # Объекты передачи данных (отделены от Entity)
│               ├── model/       # JPA Entities (структура БД)
│               ├── repository/  # Spring Data JPA интерфейсы
│               └── service/     # Бизнес-логика
└── frontend/
    ├── package.json
    ├── svelte.config.js
    ├── vite.config.ts
    └── src/
        ├── lib/
        │   ├── api/         # API клиенты (axios или fetch обертки, например, media.ts)
        │   └── components/  # Переиспользуемые Svelte-компоненты (например, PostForm.svelte)
        └── routes/          # Страницы SvelteKit
            ├── +layout.svelte
            └── +page.svelte # Главная страница с формой
```

### Архитектурные пояснения к структуре:
- **Разделение DTO и Entity:** Защищает от циклических зависимостей, предотвращает утечку внутренней структуры БД в API (N+1 проблемы, ленивая загрузка) и позволяет менять формат ответа без изменения схемы таблиц.
- **Тонкие контроллеры:** Контроллеры только валидируют входные DTO, вызывают нужный сервис и мапят ответ сервиса обратно в DTO. Вся бизнес-логика живет в `service/`.

---

## 3. Конфигурация инфраструктуры (`docker-compose.yml`)

Этот файл поднимает базу данных и MinIO. Специальный `entrypoint` в MinIO используется для автоматического создания бакета, чтобы S3 сразу был готов к приему файлов.

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: app_db
      POSTGRES_USER: db_user
      POSTGRES_PASSWORD: db_password
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000" # S3 API
      - "9001:9001" # Консоль управления
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - minio_data:/data
    command: server /data --console-address ":9001"

  minio-init:
    image: minio/mc
    depends_on:
      - minio
    entrypoint: >
      /bin/sh -c "
      until (/usr/bin/mc config host add myminio http://minio:9000 minioadmin minioadmin) do echo '...waiting...' && sleep 1; done;
      /usr/bin/mc mb myminio/media-bucket || true;
      /usr/bin/mc anonymous set public myminio/media-bucket;
      exit 0;
      "

volumes:
  pgdata:
  minio_data:
```

---

## 4. Сквозной пример: Создание поста с медиафайлом

**Архитектурное решение:** Файлы не хранятся в БД (BLOB) и не передаются через Backend для экономии RAM сервера.
Backend выдает подписанный URL (Presigned URL), а Svelte напрямую отправляет файл в S3. Затем Svelte отправляет метаданные на Backend.

### 4.1. Бэкенд (Java Spring Boot)

**Entity (Модель БД)**
```java
// src/main/java/com/example/app/model/MediaFile.java
@Entity
@Table(name = "media_files")
@Getter @Setter
public class MediaFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String objectKey; // Путь в S3
    private String url;
    private String mimeType;
    private Long size;
}

// src/main/java/com/example/app/model/Post.java
@Entity
@Table(name = "posts")
@Getter @Setter
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String content;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "media_file_id", referencedColumnName = "id")
    private MediaFile media; // Связь One-to-One / Many-to-One
}
```

**DTO**
```java
// src/main/java/com/example/app/dto/PostCreateDto.java
public record PostCreateDto(
    String title,
    String content,
    MediaFileDto media
) {}

// src/main/java/com/example/app/dto/MediaFileDto.java
public record MediaFileDto(
    String objectKey,
    String url,
    String mimeType,
    Long size
) {}
```

**Service (Логика Presigned URL)**
```java
// src/main/java/com/example/app/service/MediaService.java
@Service
@RequiredArgsConstructor
public class MediaService {
    // Представьте, что здесь инжектится AmazonS3 / S3Presigner
    private final S3Client s3Client;

    public String generatePresignedUrl(String objectKey, String contentType) {
        // Логика генерации URL на 15 минут для PUT запроса
        // Настраиваем CORS в MinIO, чтобы фронт мог делать PUT.
        return "http://localhost:9000/media-bucket/" + objectKey + "?signature=...";
    }
}
```

**Controller**
```java
// src/main/java/com/example/app/controller/PostController.java
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;
    private final MediaService mediaService;

    // 1. Фронт сначала запрашивает URL
    @GetMapping("/presigned-url")
    public ResponseEntity<Map<String, String>> getPresignedUrl(@RequestParam String filename, @RequestParam String contentType) {
        String objectKey = UUID.randomUUID().toString() + "-" + filename;
        String uploadUrl = mediaService.generatePresignedUrl(objectKey, contentType);
        return ResponseEntity.ok(Map.of("uploadUrl", uploadUrl, "objectKey", objectKey));
    }

    // 2. Фронт присылает метаданные после загрузки
    @PostMapping
    public ResponseEntity<PostDto> createPost(@RequestBody PostCreateDto dto) {
        Post post = postService.createPost(dto); // Сохраняем пост и метаданные медиа
        return ResponseEntity.status(HttpStatus.CREATED).body(PostMapper.toDto(post));
    }
}
```

### 4.2. Фронтенд (SvelteKit)

**API Client (`frontend/src/lib/api/media.ts`)**
```typescript
export interface PresignedUrlResponse {
    uploadUrl: string;
    objectKey: string;
}

export async function getPresignedUrl(filename: string, contentType: string): Promise<PresignedUrlResponse> {
    const res = await fetch(`/api/posts/presigned-url?filename=${filename}&contentType=${contentType}`);
    if (!res.ok) throw new Error("Failed to get presigned URL");
    return res.json();
}

export async function uploadToS3(url: string, file: File) {
    const res = await fetch(url, {
        method: 'PUT',
        body: file,
        headers: { 'Content-Type': file.type }
    });
    if (!res.ok) throw new Error("Upload to S3 failed");
}

export async function createPost(data: any) {
    const res = await fetch('/api/posts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    });
    return res.json();
}
```

**Svelte Component (`frontend/src/lib/components/PostForm.svelte`)**
```svelte
<script lang="ts">
    import { getPresignedUrl, uploadToS3, createPost } from '$lib/api/media';

    let title = '';
    let content = '';
    let fileInput: HTMLInputElement;
    let loading = false;

    async function handleSubmit() {
        loading = true;
        try {
            let mediaData = null;

            if (fileInput.files && fileInput.files.length > 0) {
                const file = fileInput.files[0];

                // 1. Получаем URL от бэкенда
                const { uploadUrl, objectKey } = await getPresignedUrl(file.name, file.type);

                // 2. Грузим напрямую в S3 (MinIO)
                await uploadToS3(uploadUrl, file);

                // Формируем метаданные для бэкенда
                mediaData = {
                    objectKey,
                    url: `http://localhost:9000/media-bucket/${objectKey}`,
                    mimeType: file.type,
                    size: file.size
                };
            }

            // 3. Сохраняем пост с привязанным файлом
            await createPost({ title, content, media: mediaData });
            alert('Post created successfully!');

        } catch (error) {
            console.error(error);
            alert('Error creating post');
        } finally {
            loading = false;
        }
    }
</script>

<form on:submit|preventDefault={handleSubmit} class="space-y-4 p-4 border rounded">
    <div>
        <label class="block font-bold mb-1" for="title">Title</label>
        <input id="title" bind:value={title} class="border p-2 w-full" required />
    </div>

    <div>
        <label class="block font-bold mb-1" for="content">Content</label>
        <textarea id="content" bind:value={content} class="border p-2 w-full" required></textarea>
    </div>

    <div>
        <label class="block font-bold mb-1" for="file">Media</label>
        <input id="file" type="file" bind:this={fileInput} class="border p-2 w-full" />
    </div>

    <button type="submit" disabled={loading} class="bg-blue-500 text-white px-4 py-2 rounded">
        {loading ? 'Uploading...' : 'Submit'}
    </button>
</form>
```
