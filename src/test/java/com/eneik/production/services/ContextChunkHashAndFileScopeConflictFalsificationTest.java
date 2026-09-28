package com.eneik.production.services;

import com.eneik.production.models.persistence.ContextChunkEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.ContextChunkRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Фальсифицирующий замер Ступени 2 для:
 * 1. V62: Floridi Level of Abstraction Lock (LUCHANO_FLORIDI_04_LEVEL_OF_ABSTRACTION_LOCK / D010)
 *    - Защита от холостого и искажающего реэмбеддинга контекста через content_hash.
 *    - Проверка размерности векторов (storedAtCurrentDimension) при смене модели эмбеддингов.
 *    - Очистка при пустом содержимом.
 * 2. V21: Varzi Mereological Boundary Invariant (AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP / D004)
 *    - Мерологическое разделение областей файлов (file_scope) и пресечение параллельных коллизий.
 *    - Корректность пересечения путей (идентичность, иерархия директорий, wildcard ..., нормализация слешей).
 *    - Предотвращение самоблокировки задачи (рефлексивная индифферентность к собственному ID).
 */
class ContextChunkHashAndFileScopeConflictFalsificationTest {

    @Nested
    @DisplayName("V62 / Floridi LOA Lock: Защита от холостого реэмбеддинга по content_hash")
    class FloridiContentHashLockTests {

        @Test
        @DisplayName("FLORIDI_04: Неизмененный документ пропускает реэмбеддинг и не вызывает удалений/ML-вызовов")
        void unchangedDocumentSkipsReEmbeddingAndDeletion() {
            ContextChunkRepository repository = mock(ContextChunkRepository.class);
            MLPredictionServiceClient mlClient = mock(MLPredictionServiceClient.class);
            SystemSettingsService settingsService = mock(SystemSettingsService.class);

            GeminiContextService service = new GeminiContextService(repository, mlClient, settingsService, "/tmp");

            String sourceRef = "BARCAN-TAG-01.md";
            String sourceType = "charter";
            String content = "Role charter standing knowledge content.\n\nSecond paragraph.";

            // SHA-256 хэш контента совпадает в БД
            when(repository.existsBySourceRefAndContentHash(eq(sourceRef), anyString())).thenReturn(true);

            // Модель эмбеддингов выдает размерность 3
            when(mlClient.embed("dimension probe")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

            // В БД уже лежит чанк с той же размерностью (3 float)
            ContextChunkEntity existingChunk = new ContextChunkEntity();
            existingChunk.setSourceRef(sourceRef);
            existingChunk.setEmbedding("0.1,0.2,0.3");
            existingChunk.setEmbeddingDims(3);
            when(repository.findBySourceRef(sourceRef)).thenReturn(List.of(existingChunk));

            // Вызов индексации
            service.indexDocument(sourceType, sourceRef, content);

            // Инвариант: никаких удалений в БД, никаких вызовов эмбеддинга для чанков, никаких saveAll
            verify(repository, never()).deleteBySourceRef(anyString());
            verify(mlClient, never()).embed(startsWith("Role charter"));
            verify(repository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("FLORIDI_04: Изменение контента (хэш не совпадает) форсирует очистку и полный реэмбеддинг")
        void contentChangeTriggersDeletionAndFullReEmbedding() {
            ContextChunkRepository repository = mock(ContextChunkRepository.class);
            MLPredictionServiceClient mlClient = mock(MLPredictionServiceClient.class);
            SystemSettingsService settingsService = mock(SystemSettingsService.class);

            GeminiContextService service = new GeminiContextService(repository, mlClient, settingsService, "/tmp");

            String sourceRef = "docs/philosopher-patterns/D010.md";
            String sourceType = "philosopher_pattern";
            String newContent = "Updated epistemological pattern for Floridi Level of Abstraction.";

            // Хэш не найден в БД
            when(repository.existsBySourceRefAndContentHash(eq(sourceRef), anyString())).thenReturn(false);
            when(mlClient.embed(anyString())).thenReturn(new float[]{0.42f, 0.99f});

            service.indexDocument(sourceType, sourceRef, newContent);

            // Удаление старых чанков обязательно
            verify(repository).deleteBySourceRef(sourceRef);
            // Эмбеддинг нового контента
            verify(mlClient, atLeastOnce()).embed(anyString());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ContextChunkEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(repository).saveAll(captor.capture());

            List<ContextChunkEntity> saved = captor.getValue();
            assertFalse(saved.isEmpty(), "Должен быть сохранен минимум 1 чанк");
            for (ContextChunkEntity chunk : saved) {
                assertEquals(sourceRef, chunk.getSourceRef());
                assertEquals(sourceType, chunk.getSourceType());
                assertNotNull(chunk.getContentHash(), "content_hash должен быть материализован");
                assertEquals(64, chunk.getContentHash().length(), "SHA-256 hex должен иметь длину 64 символа");
                assertEquals(2, chunk.getEmbeddingDims());
            }
        }

        @Test
        @DisplayName("FLORIDI_04: Смена размерности эмбеддингов инвалидирует кэш даже при совпадении хэша контента")
        void embeddingDimensionDriftForcesReEmbedding() {
            ContextChunkRepository repository = mock(ContextChunkRepository.class);
            MLPredictionServiceClient mlClient = mock(MLPredictionServiceClient.class);
            SystemSettingsService settingsService = mock(SystemSettingsService.class);

            GeminiContextService service = new GeminiContextService(repository, mlClient, settingsService, "/tmp");

            String sourceRef = "CHARTER.md";
            String content = "Constant content";

            // Хэш совпадает
            when(repository.existsBySourceRefAndContentHash(eq(sourceRef), anyString())).thenReturn(true);

            // Текущий зонд возвращает вектор размерности 4 (новая модель эмбеддингов)
            when(mlClient.embed("dimension probe")).thenReturn(new float[]{0.1f, 0.2f, 0.3f, 0.4f});

            // Старый сохраненный чанк имеет только 3 измерения
            ContextChunkEntity staleChunk = new ContextChunkEntity();
            staleChunk.setSourceRef(sourceRef);
            staleChunk.setEmbedding("0.1,0.2,0.3");
            staleChunk.setEmbeddingDims(3);
            when(repository.findBySourceRef(sourceRef)).thenReturn(List.of(staleChunk));

            // Новый вызов эмбеддинга вернет размерность 4
            when(mlClient.embed(eq(content))).thenReturn(new float[]{0.1f, 0.2f, 0.3f, 0.4f});

            service.indexDocument("charter", sourceRef, content);

            // Так как размерность разошлась, реэмбеддинг обязан произойти!
            verify(repository).deleteBySourceRef(sourceRef);
            verify(repository).saveAll(anyList());
        }

        @Test
        @DisplayName("FLORIDI_04: Пустой или null контент удаляет старые чанки без вызова ML-сервиса")
        void emptyOrNullContentDeletesOldChunksWithoutMlCalls() {
            ContextChunkRepository repository = mock(ContextChunkRepository.class);
            MLPredictionServiceClient mlClient = mock(MLPredictionServiceClient.class);
            SystemSettingsService settingsService = mock(SystemSettingsService.class);

            GeminiContextService service = new GeminiContextService(repository, mlClient, settingsService, "/tmp");

            service.indexDocument("charter", "empty.md", "");
            verify(repository).deleteBySourceRef("empty.md");

            service.indexDocument("charter", "null.md", null);
            verify(repository).deleteBySourceRef("null.md");

            service.indexDocument("charter", "blank.md", "   \n\t  ");
            verify(repository).deleteBySourceRef("blank.md");

            verifyNoInteractions(mlClient);
        }
    }

    @Nested
    @DisplayName("V21 / Varzi Mereological Boundary Invariant: Разграничение file_scope и обнаружение конфликтов")
    class VarziMereologicalScopeTests {

        private final TaskRepository taskRepository = mock(TaskRepository.class, CALLS_REAL_METHODS);

        @Test
        @DisplayName("VARZI_02: Идентичные пути пересекаются, разные файлы одного пакета не пересекаются")
        void pathsIntersectExactMatches() {
            assertTrue(taskRepository.pathsIntersect("src/main/A.java", "src/main/A.java"),
                    "Идентичные пути должны пересекаться");
            assertFalse(taskRepository.pathsIntersect("src/main/A.java", "src/main/B.java"),
                    "Разные файлы одного каталога не должны пересекаться");
        }

        @Test
        @DisplayName("VARZI_02: Иерархическое включение директорий (Part-Whole) определяет конфликт")
        void pathsIntersectDirectoryHierarchy() {
            // Директория со слешем покрывает вложенные файлы
            assertTrue(taskRepository.pathsIntersect("src/main/services/", "src/main/services/OrderService.java"));
            assertTrue(taskRepository.pathsIntersect("src/main/services/OrderService.java", "src/main/services/"));

            // Директория без конечного слеша также покрывает вложенные файлы
            assertTrue(taskRepository.pathsIntersect("src/main/services", "src/main/services/OrderService.java"));
            assertTrue(taskRepository.pathsIntersect("src/main/services/OrderService.java", "src/main/services"));

            // Родственные директории не пересекаются
            assertFalse(taskRepository.pathsIntersect("src/main/services", "src/main/repositories"));

            // Ложные префиксы имен файлов не пересекаются (защита от коллизии имен без границы каталога)
            assertFalse(taskRepository.pathsIntersect("src/main/service", "src/main/services/OrderService.java"),
                    "'service' не должен покрывать 'services/'");
        }

        @Test
        @DisplayName("VARZI_02: Паттерны с многоточием (...) корректно сопоставляются как wildcard")
        void pathsIntersectWildcardEllipsisPattern() {
            assertTrue(taskRepository.pathsIntersect("src/.../OrderService.java", "src/main/services/OrderService.java"));
            assertTrue(taskRepository.pathsIntersect("src/main/services/OrderService.java", "src/.../OrderService.java"));

            assertTrue(taskRepository.pathsIntersect("docs/reports/...", "docs/reports/MANAGER_STATE.md"));
            assertFalse(taskRepository.pathsIntersect("docs/reports/...", "src/main/OrderService.java"));

            assertFalse(taskRepository.pathsIntersect("src/.../Foo.java", "src/main/Bar.java"));
        }

        @Test
        @DisplayName("VARZI_02: Windows обратные слеши нормализуются к POSIX стандарту")
        void pathsIntersectBackslashNormalization() {
            assertTrue(taskRepository.pathsIntersect("src\\main\\services\\A.java", "src/main/services/A.java"));
            assertTrue(taskRepository.pathsIntersect("src\\main\\services\\", "src/main/services/A.java"));
        }

        @Test
        @DisplayName("VARZI_02: fileScopesIntersect корректно обрабатывает JSON списки путей")
        void fileScopesIntersectJsonArrays() {
            String scope1 = "[\"src/main/ServiceA.java\", \"src/main/ServiceB.java\"]";
            String scope2 = "[\"src/main/ServiceB.java\", \"src/main/ServiceC.java\"]";
            String scope3 = "[\"src/main/ServiceD.java\"]";

            assertTrue(taskRepository.fileScopesIntersect(scope1, scope2),
                    "Пересекающиеся множества файлов должны давать конфликт");
            assertFalse(taskRepository.fileScopesIntersect(scope1, scope3),
                    "Непересекающиеся множества файлов не должны давать конфликт");

            // Граничные и невалидные JSON
            assertFalse(taskRepository.fileScopesIntersect(null, scope1));
            assertFalse(taskRepository.fileScopesIntersect(scope1, null));
            assertFalse(taskRepository.fileScopesIntersect("", scope1));
            assertFalse(taskRepository.fileScopesIntersect("[]", scope1));
            assertFalse(taskRepository.fileScopesIntersect("{invalid_json}", scope1));
        }

        @Test
        @DisplayName("VARZI_02: hasFileScopeConflict защищает от коллизий в рамках одного проекта и исключает сам себя")
        void hasFileScopeConflictBehavior() {
            UUID projectId = UUID.randomUUID();
            ProjectEntity project = new ProjectEntity();
            project.setId(projectId);

            UUID candId = UUID.randomUUID();
            TaskEntity candidate = new TaskEntity();
            candidate.setId(candId);
            candidate.setProject(project);
            candidate.setFileScope("[\"src/main/A.java\"]");

            // Случай 1: нет проекта -> false
            TaskEntity noProj = new TaskEntity();
            noProj.setFileScope("[\"src/main/A.java\"]");
            assertFalse(taskRepository.hasFileScopeConflict(noProj));

            // Случай 2: пустой скоуп кандидата -> false
            candidate.setFileScope("[]");
            assertFalse(taskRepository.hasFileScopeConflict(candidate));
            candidate.setFileScope(null);
            assertFalse(taskRepository.hasFileScopeConflict(candidate));

            // Восстанавливаем скоуп
            candidate.setFileScope("[\"src/main/A.java\"]");

            // Случай 3: активная задача в том же проекте с конфликтующим скоупом -> true
            TaskEntity activeConflict = new TaskEntity();
            activeConflict.setId(UUID.randomUUID());
            activeConflict.setProject(project);
            activeConflict.setFileScope("[\"src/main/A.java\", \"src/main/B.java\"]");

            when(taskRepository.findActiveTasksByProject(projectId)).thenReturn(List.of(activeConflict));
            assertTrue(taskRepository.hasFileScopeConflict(candidate),
                    "При пересечении file_scope с активной задачей должен фиксироваться конфликт");

            // Случай 4: активная задача не пересекается -> false
            TaskEntity activeDisjoint = new TaskEntity();
            activeDisjoint.setId(UUID.randomUUID());
            activeDisjoint.setProject(project);
            activeDisjoint.setFileScope("[\"src/main/C.java\"]");

            when(taskRepository.findActiveTasksByProject(projectId)).thenReturn(List.of(activeDisjoint));
            assertFalse(taskRepository.hasFileScopeConflict(candidate),
                    "При непересекающихся file_scope конфликта быть не должно");

            // Случай 5: активная задача - это сам кандидат (тот же ID) -> не конфликтует сам с собой
            TaskEntity activeSelf = new TaskEntity();
            activeSelf.setId(candId);
            activeSelf.setProject(project);
            activeSelf.setFileScope("[\"src/main/A.java\"]");

            when(taskRepository.findActiveTasksByProject(projectId)).thenReturn(List.of(activeSelf));
            assertFalse(taskRepository.hasFileScopeConflict(candidate),
                    "Задача не должна конфликтовать сама с собой");
        }
    }
}
