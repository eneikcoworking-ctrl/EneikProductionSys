# Диагностика и план интеграции LLM (Antigravity & Gemini)

**Дата:** 11 июля 2026 г.

Этот документ является диагностическим отчетом о том, как реализован механизм "Antigravity" в текущей архитектуре проекта, и концептуальным планом по внедрению реального LLM-движка (Google Gemini) по запросу пользователя. **Никаких изменений в код на данном этапе не вносилось.**

---

## 1. Диагностика текущей реализации "Antigravity"

В техническом задании "Antigravity" заявлен как интеллектуальный AI-агент, проводящий ревью кода (Code Review) и оценивающий код на соответствие критериям отказа (Refusal Criteria). Однако детальный аудит `MLPredictionServiceClient.java` (на стороне Spring Boot) и `PredictionService.py` (на стороне FastAPI) выявил следующее:

### А. Заглушка в Python (Имитация AI)
В файле `PredictionService.py` эндпоинт `/api/v1/review/pr` **не использует ни одну нейросеть (LLM)**.
Вместо анализа реального diff-кода, скрипт содержит хардкод:
```python
approved = True
remarks = f"CORE ARCHITECTURE VERIFIED. APPROVED. Antigravity local agent review passed for role {role_tag}."
```
Затем он возвращает список "Kano Refactoring" задач на основе примитивного поиска слов (если в описании задачи есть слово `chess`, предлагается рефакторинг шахматного движка; если нет — кэширование Redis или CSS-загрузчики).

### Б. Уязвимость "Fail-Open" в Java-бэкенде
Еще более критичной является логика резервного поведения в Java. Если FastAPI-сервис недоступен или падает, `MLPredictionServiceClient` подавляет исключение в блоке `catch` и принудительно возвращает успешный ответ:
```java
return Map.of(
    "approved", true,
    "remarks", "CORE ARCHITECTURE VERIFIED. APPROVED. Antigravity fallback review pass."
);
```
Это означает, что код проходит в ветку `main` без какого-либо ревью, создавая критическую брешь (Fail-Open паттерн). Аналогичная имитация применяется и для эндпоинта проверки критериев отказа (`checkRefusalCriteria`).

---

## 2. Концептуальный план интеграции Google Gemini (Без внешних зависимостей)

Пользователь выразил желание оставаться в инфраструктуре Google и использовать **Gemini** в качестве реального ядра "Antigravity". Архитектура проекта позволяет внедрить это чисто и элегантно.

### Требования к инфраструктуре:
- **Никаких тяжелых библиотек:** Согласно AGENTS.md, Python-скрипты должны предпочитать стандартные библиотеки (например, `urllib.request`), если пакеты вроде `requests` не предустановлены.
- **Environment Variable:** Интеграция будет опираться на переменную `GEMINI_API_KEY`, прокинутую в Docker-контейнер ML-сервиса.

### Как будет выглядеть интеграция в коде (Концепт):

#### 1. Вызов Gemini API через `urllib`
В `PredictionService.py` создается утилитарная функция:

```python
import os
import json
import urllib.request

def ask_gemini(prompt: str, system_instruction: str) -> str:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY is not set.")

    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key={api_key}"

    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "systemInstruction": {"parts": [{"text": system_instruction}]}
    }

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})

    with urllib.request.urlopen(req) as response:
        result = json.loads(response.read().decode("utf-8"))
        # Парсим ответ Gemini (из result['candidates'][0]['content']['parts'][0]['text'])
        # и возвращаем строковый JSON, который затем конвертируется в dict.
        return extract_text_from_gemini(result)
```

#### 2. Загрузка реального кода (PR Diff)
В эндпоинте `/api/v1/review/pr` сервис должен получить реальный код:
```python
# Добавляем .diff к публичному URL Pull Request'а на GitHub
diff_url = request.prUrl + ".diff"
diff_req = urllib.request.Request(diff_url)
with urllib.request.urlopen(diff_req) as response:
    pr_diff = response.read().decode("utf-8")
```

#### 3. Формирование промпта и возврат результата
Сформированный Diff отправляется в Gemini:
```python
system_instruction = "Ты строгий Code Guardian. Верни ТОЛЬКО JSON: {\"approved\": bool, \"remarks\": \"строка\"}"
gemini_response_json = ask_gemini(pr_diff, system_instruction)
return ReviewResponse(**json.loads(gemini_response_json))
```

### 3. Исправление уязвимости в Java (Fail-Safe)
Интеграция будет неполной без исправления бэкенда. При внедрении Gemini, блок `catch` в `MLPredictionServiceClient.java` должен быть изменен:
```java
} catch (Exception e) {
    LOGGER.severe("Gemini ML service failed: " + e.getMessage());
    return Map.of(
        "approved", false,
        "remarks", "VERIFICATION_SERVICE_UNAVAILABLE"
    );
}
```
А метод `dispatch` должен переводить задачу в статус `needs_human_review` при `approved = false`.

**Заключение:**
Предложенная архитектура позволяет полностью отказаться от "декоративной" заглушки Antigravity и интегрировать реальную нейросеть (Gemini 1.5), используя только встроенные модули Python и закрывая архитектурную дыру авто-мержа.

## 4. Ответы на вопросы о рабочем процессе с Gemini

Вы спросили:
> *«То есть если у джулсов будут появляться какие то промежуточные вопросы - джемини на все ответит. Если все ок - смержит в мейн. Раз в сутки применит на себя все роли и будет критиковать? правильно?»*

Ответ состоит из трех частей, потому что текущая архитектура позволяет реализовать не всё из этого "из коробки":

### 1. "Раз в сутки применит на себя все роли и будет критиковать" — ДА, именно так
Это механизм **Falsification Cycle**. Раз в сутки (в 2:00 ночи) Java-бэкенд собирает свежий код (diff) и отправляет его в эндпоинт `/api/v1/review/refusal-criteria`. С внедрением Gemini:
- Нейросеть "надевает шляпу" каждой роли (например, становится AppSec, потом Frontend Engineer) и читает их `REFUSAL CRITERIA` (критерии отказа).
- Gemini критикует кодовую базу от лица этой роли. Если она находит нарушение (например, хардкод паролей для AppSec), она отвечает `compliant = false`.
- Java-бэкенд тут же автоматически создает новую задачу в очередь (self-falsification task), чтобы агенты-исполнители исправили этот недочет на следующий день.

### 2. "Если все ок - смержит в мейн" — ДА, это Code Review
Когда агент-исполнитель (Jules) заканчивает писать код и открывает Pull Request, задача переходит в стадию проверки (`reviewPr`). С внедрением Gemini:
- Gemini скачивает код этого Pull Request'а и выступает в роли финального Code Guardian (`BARCAN-TAG-00`).
- Она проверяет логику, стиль и архитектуру.
- **Если всё ок:** Gemini возвращает `approved = true`. Бэкенд передает сигнал в `AutoMergeService`, и код сливается в `main`.
- **Если есть ошибка:** Gemini возвращает `approved = false` с детальным комментарием. Как обсуждалось в предыдущем аудите (Приоритет №1), задача должна блокироваться (переходить в статус `needs_human_review` или возвращаться на доработку). Авто-мерж блокируется.

### 3. "Если у джулсов будут появляться какие-то промежуточные вопросы - джемини на все ответит" — НЕТ (пока не поддерживается архитектурой)
Текущая оркестрация задач в EneikProductionSys (через таблицы `tasks`, `jules_sessions` и `ClaimService`) — это система **одноразовых (single-shot)** запусков.
- Оркестратор берет задачу, формирует один огромный промпт (с правилами роли и контекстом), отправляет его агенту Jules и ждет, пока тот не завершит работу (создаст PR) или не упадет с ошибкой.
- **В базе данных нет таблиц для сохранения "переписки" или "тредов" (chat threads).** Агенты не могут "поставить задачу на паузу", задать промежуточный вопрос в бэкенд, получить ответ от Gemini и продолжить писать код в той же сессии.
- **Как это можно решить в будущем:** Потребуется внедрить концепцию двустороннего чата (например, через комментарии в Linear Issue или GitHub PR, которые бэкенд будет транслировать в Gemini и возвращать ответы агенту). Пока что агенты работают в строгом режиме "получил приказ -> написал код -> отдал на проверку Gemini".
